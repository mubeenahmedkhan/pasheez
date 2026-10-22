package com.pasheez.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

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
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbol=BTCUSDT"))
                .andRespond(withSuccess("""
                        {"symbol":"BTCUSDT","lastPrice":"82252.00000000","priceChangePercent":"-3.455",
                         "bidPrice":"82250.00000000","askPrice":"82254.00000000"}
                        """, MediaType.APPLICATION_JSON));

        PriceTick tick = priceSource.latestPrice(new Symbol("btcusdt"));

        assertEquals("BTCUSDT", tick.symbol().value());
        // Mid of bid 82250 and ask 82254 — not the last trade.
        assertEquals(0, new BigDecimal("82252").compareTo(tick.price()));
        assertEquals(new BigDecimal("-3.455"), tick.changePercent());
        assertNotNull(tick.timestamp());
        server.verify();
    }

    @Test
    void translatesA400IntoUnknownSymbol() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbol=NOPE"))
                .andRespond(withBadRequest().body("""
                        {"code":-1121,"msg":"Invalid symbol."}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThrows(UnknownSymbolException.class, () -> priceSource.latestPrice(new Symbol("nope")));
    }

    @Test
    void translatesAnUpstreamFailureIntoPriceUnavailable() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbol=BTCUSDT"))
                .andRespond(withServerError());

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrice(new Symbol("BTCUSDT")));
    }

    @Test
    void mapsABatchResponseAndTrustsTheSymbolInEachElementNotItsPosition() {
        // Deliberately NOT the order we asked for — Binance does not echo request order.
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%2C%22ETHUSDT%22%5D"))
                .andRespond(withSuccess("""
                        [{"symbol":"ETHUSDT","lastPrice":"2386.81","priceChangePercent":"1.2",
                          "bidPrice":"2386.80","askPrice":"2386.82"},
                         {"symbol":"BTCUSDT","lastPrice":"81555.99","priceChangePercent":"-0.5",
                          "bidPrice":"81555.98","askPrice":"81556.00"}]
                        """, MediaType.APPLICATION_JSON));

        List<PriceTick> ticks = priceSource.latestPrices(
                List.of(new Symbol("BTCUSDT"), new Symbol("ETHUSDT")));

        assertEquals(2, ticks.size());
        PriceTick eth = ticks.stream().filter(t -> t.symbol().value().equals("ETHUSDT")).findFirst().orElseThrow();
        PriceTick btc = ticks.stream().filter(t -> t.symbol().value().equals("BTCUSDT")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2386.81").compareTo(eth.price()));
        assertEquals(0, new BigDecimal("81555.99").compareTo(btc.price()));
        assertEquals(new BigDecimal("1.2"), eth.changePercent());
        server.verify();
    }

    @Test
    void batchTranslatesA400IntoUnknownSymbol() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%2C%22NOPE%22%5D"))
                .andRespond(withBadRequest().body("""
                        {"code":-1121,"msg":"Invalid symbol."}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThrows(UnknownSymbolException.class, () -> priceSource.latestPrices(
                List.of(new Symbol("BTCUSDT"), new Symbol("NOPE"))));
    }

    @Test
    void askingForNoSymbolsMakesNoUpstreamCall() {
        assertEquals(List.of(), priceSource.latestPrices(List.of()));
        server.verify();   // no expectations were set, so any call would have failed
    }

    @Test
    void treatsAnEmptyBodyAsUnavailableRatherThanNullPointering() {
        server.expect(requestTo("https://binance.test/api/v3/ticker/24hr?symbol=BTCUSDT"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrice(new Symbol("BTCUSDT")));
    }
}
