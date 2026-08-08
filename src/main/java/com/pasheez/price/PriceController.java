package com.pasheez.price;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/prices")
public class PriceController {

    private final PriceSource priceSource;

    public PriceController(PriceSource priceSource) {

        this.priceSource = priceSource;
    }

    @GetMapping("/{symbol}")
    public PriceTick getPrice(@PathVariable String symbol) {
        return priceSource.latestPrice(new Symbol(symbol));
    }


    @PostMapping("/{symbol}")
    public PriceTick setPrice(@PathVariable String symbol) {
        return priceSource.latestPrice(new Symbol(symbol));
    }

}
