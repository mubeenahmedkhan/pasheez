package com.pasheez.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc: the controller plus its @RestControllerAdvice, with a stubbed
 * PriceSource. Proves the HTTP contract — in particular that a domain exception becomes
 * the right status code instead of a 500.
 */
class PriceControllerTest {

    private PriceSource priceSource;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.priceSource = mock(PriceSource.class);
        PriceStreamService streamService = mock(PriceStreamService.class);
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new PriceController(priceSource, streamService))
                .setControllerAdvice(new PriceExceptionHandler())
                .build();
    }

    @Test
    void returnsThePriceAsJson() throws Exception {
        when(priceSource.latestPrice(any()))
                .thenReturn(new PriceTick(new Symbol("BTCUSDT"), new BigDecimal("82252.00"), Instant.now()));

        mockMvc.perform(get("/prices/btcusdt"))
                .andExpect(status().isOk())
                // Symbol is @JsonValue-annotated, so it serializes as a bare string
                // rather than a nested {"value":"..."} object.
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.price").value(82252.00))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns404ForAnUnknownSymbol() throws Exception {
        when(priceSource.latestPrice(any())).thenThrow(new UnknownSymbolException(new Symbol("NOPE")));

        mockMvc.perform(get("/prices/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Unknown symbol"));
    }

    @Test
    void returns503WhenUpstreamIsUnavailable() throws Exception {
        when(priceSource.latestPrice(any()))
                .thenThrow(new PriceUnavailableException("Binance unreachable", null));

        mockMvc.perform(get("/prices/btcusdt"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Price temporarily unavailable"));
    }
}
