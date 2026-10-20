package com.pasheez.price;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/prices")
public class PriceController {

    private final PriceSource priceSource;
    private final PriceStreamService priceStreamService;

    public PriceController(PriceSource priceSource, PriceStreamService priceStreamService) {
        this.priceSource = priceSource;
        this.priceStreamService = priceStreamService;
    }

    @GetMapping("/{symbol}")
    public PriceTick getPrice(@PathVariable String symbol) {
        return priceSource.latestPrice(new Symbol(symbol));
    }

    /**
     * One stream for a whole watchlist: GET /prices/stream?symbols=BTCUSDT,ETHUSDT
     * <p>
     * A literal path segment, so Spring matches it ahead of the /{symbol} pattern.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices(@RequestParam List<String> symbols) {
        List<Symbol> requested = symbols.stream()
                .map(Symbol::new)
                .distinct()
                .toList();
        return priceStreamService.subscribe(requested);
    }

    @GetMapping(value = "/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrice(@PathVariable String symbol) {
        return priceStreamService.subscribe(new Symbol(symbol));
    }
}
