package com.pasheez.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of who is listening to which symbols, and fans price ticks out to them.
 * Every method here can be called from two thread types at once — Tomcat request threads
 * (subscribe) and the scheduler thread (broadcast) — so all state is concurrent.
 * <p>
 * One emitter may be registered under several symbols: a client opens a single connection
 * for a whole watchlist, because browsers cap concurrent HTTP/1.1 connections per origin
 * at around six and a long-lived stream would occupy one of those slots indefinitely.
 */
@Service
public class PriceStreamService {

    private static final Logger log = LoggerFactory.getLogger(PriceStreamService.class);

    /** Guard rail: one request cannot ask us to poll an unbounded number of symbols. */
    static final int MAX_SYMBOLS_PER_STREAM = 20;

    private final Map<Symbol, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final PriceSource priceSource;
    private final Duration streamTimeout;

    PriceStreamService(PriceSource priceSource,
                       @Value("${price-stream.timeout}") Duration streamTimeout) {
        this.priceSource = priceSource;
        this.streamTimeout = streamTimeout;
    }

    public SseEmitter subscribe(Symbol symbol) {
        return subscribe(List.of(symbol));
    }

    /**
     * Open one stream carrying every requested symbol.
     * <p>
     * The first prices are fetched <em>before</em> the emitter is created, on purpose. Once
     * an SseEmitter is returned the response is committed as 200 text/event-stream and the
     * status can never be changed — so an unknown symbol has to fail here, while a 404 is
     * still possible. It also means the client renders immediately instead of waiting for
     * the next poll tick.
     */
    public SseEmitter subscribe(List<Symbol> symbols) {
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required");
        }
        if (symbols.size() > MAX_SYMBOLS_PER_STREAM) {
            throw new IllegalArgumentException(
                    "At most " + MAX_SYMBOLS_PER_STREAM + " symbols per stream, got " + symbols.size());
        }

        List<PriceTick> firstTicks = priceSource.latestPrices(symbols);

        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        symbols.forEach(symbol ->
                subscribers.computeIfAbsent(symbol, s -> ConcurrentHashMap.newKeySet()).add(emitter));

        // This emitter is registered under several keys, so teardown has to unregister it
        // from all of them — not just the one that happened to fail.
        Runnable cleanUp = () -> symbols.forEach(symbol -> unsubscribe(symbol, emitter));
        emitter.onCompletion(cleanUp);
        emitter.onTimeout(() -> {
            emitter.complete();
            cleanUp.run();
        });
        emitter.onError(e -> cleanUp.run());

        firstTicks.forEach(tick -> send(tick.symbol(), emitter, tick));
        log.debug("Subscribed to {}", symbols.stream().map(Symbol::value).toList());
        return emitter;
    }

    /** Symbols with at least one listener — the poller only fetches these. */
    public Set<Symbol> subscribedSymbols() {
        return Set.copyOf(subscribers.keySet());
    }

    public void broadcast(PriceTick tick) {
        Set<SseEmitter> emitters = subscribers.get(tick.symbol());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(tick.symbol(), emitter, tick);
        }
    }

    private void send(Symbol symbol, SseEmitter emitter, PriceTick tick) {
        try {
            emitter.send(SseEmitter.event().name("price").data(tick));
        } catch (IOException | IllegalStateException ex) {
            // Normal: the browser tab closed. Not an error worth shouting about.
            log.debug("Dropping dead emitter for {}: {}", symbol.value(), ex.getMessage());
            emitter.completeWithError(ex);
        }
    }

    /**
     * Remove one listener, and drop the symbol entirely once nobody is left — otherwise the
     * poller would keep calling Binance for a symbol with no audience. computeIfPresent makes
     * the "remove, then maybe remove the key" pair atomic for that key.
     */
    private void unsubscribe(Symbol symbol, SseEmitter emitter) {
        subscribers.computeIfPresent(symbol, (s, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
