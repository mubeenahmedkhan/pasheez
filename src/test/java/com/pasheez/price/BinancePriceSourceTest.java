package com.pasheez.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * No network and no Spring context: MockRestServiceServer swaps the RestClient's request
 * factory for a stub, so these tests assert our mapping and error translation, not Binance.
 */
class BinancePriceSourceTest {

    private MockRestServiceServer server;
    private BinancePriceSource priceSource;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://binance.test");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.priceSource = new BinancePriceSource(builder.build());
    }

    @Test
    void mapsBinanceResponseOntoAPriceTick() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/price?symbol=BTCUSDT"))
                .andRespond(withSuccess("""
                        {"symbol":"BTCUSDT","price":"82252.00000000"}
                        """, MediaType.APPLICATION_JSON));

        PriceTick tick = priceSource.latestPrice(new Symbol("btcusdt"));

        assertEquals("BTCUSDT", tick.symbol().value());
        assertEquals(new BigDecimal("82252.00000000"), tick.price());
        assertNotNull(tick.timestamp());
        server.verify();
    }

    @Test
    void translatesA400IntoUnknownSymbol() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/price?symbol=NOPE"))
                .andRespond(withBadRequest().body("""
                        {"code":-1121,"msg":"Invalid symbol."}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThrows(UnknownSymbolException.class, () -> priceSource.latestPrice(new Symbol("nope")));
    }

    @Test
    void translatesAnUpstreamFailureIntoPriceUnavailable() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/price?symbol=BTCUSDT"))
                .andRespond(withServerError());

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrice(new Symbol("BTCUSDT")));
    }

    @Test
    void treatsAnEmptyBodyAsUnavailableRatherThanNullPointering() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/price?symbol=BTCUSDT"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrice(new Symbol("BTCUSDT")));
    }
}
