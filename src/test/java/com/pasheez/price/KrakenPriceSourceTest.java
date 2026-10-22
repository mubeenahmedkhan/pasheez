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
                          "XBTUSDT":{"a":["81300.00000","1","1"],"b":["81290.00000","1","1"],
                                     "c":["81292.10000","0.01"],"o":"80000.00000","v":["1","2"]},
                          "XDGUSDT":{"a":["0.078540000","1","1"],"b":["0.078520000","1","1"],
                                     "c":["0.078530000","100"],"o":"0.080000000"}}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, PriceTick> ticks = priceSource.latestPrices(btcAndDoge()).stream()
                .collect(Collectors.toMap(t -> t.symbol().value(), t -> t));

        assertEquals(java.util.Set.of("BTCUSDT", "DOGEUSDT"), ticks.keySet());
        // Mid of bid 81290 and ask 81300, not the last trade of 81292.10.
        assertEquals(0, new BigDecimal("81295").compareTo(ticks.get("BTCUSDT").price()));
        // (81292.10 - 80000) / 80000 = +1.62% (last trade against today's open)
        assertEquals(new BigDecimal("1.62"), ticks.get("BTCUSDT").changePercent());
        assertEquals(new BigDecimal("-1.84"), ticks.get("DOGEUSDT").changePercent());
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
    void fallsBackToTheLastTradeWhenTheBookIsMissing() {
        server.expect(requestTo(BTC_AND_DOGE))
                .andRespond(withSuccess("""
                        {"error":[],"result":{"XBTUSDT":{"c":["81292.10000","0.01"],"o":"80000.0"}}}
                        """, MediaType.APPLICATION_JSON));

        PriceTick btc = priceSource.latestPrices(btcAndDoge()).getFirst();
        assertEquals(new BigDecimal("81292.10000"), btc.price());
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
