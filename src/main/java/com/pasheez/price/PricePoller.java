package com.pasheez.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls a fresh price for every symbol someone is listening to and hands it to the
 * stream service. Binance has no push API here, so "live" is polling plus fan-out.
 */
@Component
public class PricePoller {

    private static final Logger log = LoggerFactory.getLogger(PricePoller.class);

    private final PriceSource priceSource;
    private final PriceStreamService streamService;

    PricePoller(PriceSource priceSource, PriceStreamService streamService) {
        this.priceSource = priceSource;
        this.streamService = streamService;
    }

    @Scheduled(fixedRateString = "${price-stream.poll-interval}")
    void pollSubscribedSymbols() {
        for (Symbol symbol : streamService.subscribedSymbols()) {
            try {
                streamService.broadcast(priceSource.latestPrice(symbol));
            } catch (RuntimeException ex) {
                // One failing symbol must not abort the tick for the others, and must not
                // escape: an exception thrown out of a @Scheduled method is only logged.
                log.warn("Poll failed for {}: {}", symbol.value(), ex.getMessage());
            }
        }
    }
}
