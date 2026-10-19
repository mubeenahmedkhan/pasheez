package com.pasheez.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of who is currently listening to which symbol, and fans price ticks out
 * to them. Every method here can be called from two different threads at once — the
 * Tomcat request thread (subscribe) and the scheduler thread (broadcast) — so all state
 * is held in concurrent collections.
 */
@Service
public class PriceStreamService {

    private static final Logger log = LoggerFactory.getLogger(PriceStreamService.class);

    private final Map<Symbol, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final PriceSource priceSource;
    private final Duration streamTimeout;

    PriceStreamService(PriceSource priceSource,
                       @Value("${price-stream.timeout}") Duration streamTimeout) {
        this.priceSource = priceSource;
        this.streamTimeout = streamTimeout;
    }

    /**
     * Open a stream for one symbol.
     * <p>
     * The first price is fetched <em>before</em> the emitter is created, on purpose. Once
     * an SseEmitter is returned the response is committed as 200 text/event-stream and the
     * status can never be changed — so an unknown symbol has to fail here, while a 404 is
     * still possible. It also means the client sees data immediately instead of waiting
     * for the next poll tick.
     */
    public SseEmitter subscribe(Symbol symbol) {
        PriceTick first = priceSource.latestPrice(symbol);

        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        subscribers.computeIfAbsent(symbol, s -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> unsubscribe(symbol, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            unsubscribe(symbol, emitter);
        });
        emitter.onError(e -> unsubscribe(symbol, emitter));

        send(symbol, emitter, first);
        log.debug("Subscribed to {} ({} listener(s))", symbol.value(), countFor(symbol));
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
            unsubscribe(symbol, emitter);
            emitter.completeWithError(ex);
        }
    }

    /**
     * Remove one listener, and drop the symbol entirely once nobody is left — otherwise
     * the poller would keep calling Binance for a symbol with no audience. computeIfPresent
     * makes the "remove, then maybe remove the key" pair atomic for that key.
     */
    private void unsubscribe(Symbol symbol, SseEmitter emitter) {
        subscribers.computeIfPresent(symbol, (s, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private int countFor(Symbol symbol) {
        Set<SseEmitter> emitters = subscribers.get(symbol);
        return emitters == null ? 0 : emitters.size();
    }
}
