package com.pasheez.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Pulls fresh prices for every symbol someone is listening to and hands them to the stream
 * service. Binance has no push API here, so "live" is polling plus fan-out.
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
        Set<Symbol> symbols = streamService.subscribedSymbols();
        if (symbols.isEmpty()) {
            return;
        }

        try {
            // One upstream request per tick, whatever the symbol count. Previously this was
            // a sequential call per symbol on the single scheduler thread, which would
            // overrun the poll interval as the watchlist grew.
            priceSource.latestPrices(symbols).forEach(streamService::broadcast);
        } catch (RuntimeException ex) {
            // Must not escape: an exception thrown out of a @Scheduled method is only logged,
            // and the next tick should still run.
            log.warn("Poll failed for {}: {}",
                    symbols.stream().map(Symbol::value).toList(), ex.getMessage());
        }
    }
}
