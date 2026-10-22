package com.pasheez.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KrakenPriceSourceTest {

    private static final String BTC_AND_DOGE =
            "https://kraken.test/0/public/Ticker?pair=BTCUSDT%2CDOGEUSDT";

    private MockRestServiceServer server;
    private KrakenPriceSource priceSource;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kraken.test");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.priceSource = new KrakenPriceSource(builder.build());
    }

    private List<Symbol> btcAndDoge() {
        return List.of(new Symbol("BTCUSDT"), new Symbol("DOGEUSDT"));
    }

    @Test
    void translatesKrakensLegacyPairNamesBackToCommonSymbols() {
        // Asked for BTCUSDT and DOGEUSDT; Kraken answers as XBTUSDT and XDGUSDT.
        server.expect(requestTo(BTC_AND_DOGE))
                .andRespond(withSuccess("""
                        {"error":[],"result":{
                          "XBTUSDT":{"c":["81292.10000","0.01"],"v":["1","2"]},
                          "XDGUSDT":{"c":["0.078530000","100"]}}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, BigDecimal> prices = priceSource.latestPrices(btcAndDoge()).stream()
                .collect(Collectors.toMap(t -> t.symbol().value(), PriceTick::price));

        assertEquals(Map.of(
                "BTCUSDT", new BigDecimal("81292.10000"),
                "DOGEUSDT", new BigDecimal("0.078530000")), prices);
        server.verify();
    }

    @Test
    void anUnknownPairIsAnHttp200WithAnErrorBody() {
        // Kraken does not use status codes for this: the body is the only signal.
        server.expect(requestTo(BTC_AND_DOGE))
                .andRespond(withSuccess("""
                        {"error":["EQuery:Unknown asset pair"]}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(UnknownSymbolException.class, () -> priceSource.latestPrices(btcAndDoge()));
    }

    @Test
    void anyOtherErrorBodyIsTreatedAsUnavailable() {
        server.expect(requestTo(BTC_AND_DOGE))
                .andRespond(withSuccess("""
                        {"error":["EAPI:Rate limit exceeded"]}
                        """, MediaType.APPLICATION_JSON));

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrices(btcAndDoge()));
    }

    @Test
    void anHttpErrorIsTreatedAsUnavailable() {
        server.expect(requestTo(BTC_AND_DOGE)).andRespond(withServerError());

        assertThrows(PriceUnavailableException.class, () -> priceSource.latestPrices(btcAndDoge()));
    }

    @Test
    void symbolTranslationLeavesOrdinaryPairsAlone() {
        assertEquals("BTCUSDT", KrakenPriceSource.toCommonSymbol("XBTUSDT"));
        assertEquals("DOGEUSDT", KrakenPriceSource.toCommonSymbol("XDGUSDT"));
        // XRP starts with X but is not a legacy alias — must not be mangled.
        assertEquals("XRPUSDT", KrakenPriceSource.toCommonSymbol("XRPUSDT"));
        assertEquals("ETHUSDT", KrakenPriceSource.toCommonSymbol("ETHUSDT"));
    }
}
