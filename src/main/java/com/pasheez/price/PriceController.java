package com.pasheez.price;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @GetMapping(value = "/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrice(@PathVariable String symbol) {
        return priceStreamService.subscribe(new Symbol(symbol));
    }
}
