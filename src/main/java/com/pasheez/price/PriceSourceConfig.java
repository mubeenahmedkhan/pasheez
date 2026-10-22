package com.pasheez.price;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Chooses which exchange backs the {@link PriceSource} port, by configuration:
 * {@code price-source.provider: binance | kraken}.
 * <p>
 * Exactly one of these beans exists at runtime, so everything that depends on
 * {@code PriceSource} — controller, stream service, poller — is unaware which exchange it is
 * talking to. Adding an exchange means a new adapter and one more method here; nothing else
 * changes (Open-Closed Principle).
 * <p>
 * Why a deployment needs this at all: Binance bans Render's shared outbound IPs (HTTP 418),
 * so production runs on Kraken while local development stays on Binance. See ADR 0005.
 */
@Configuration
public class PriceSourceConfig {

    private final Duration connectTimeout;
    private final Duration readTimeout;

    PriceSourceConfig(@Value("${upstream.connect-timeout}") Duration connectTimeout,
                      @Value("${upstream.read-timeout}") Duration readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Bean
    @ConditionalOnProperty(name = "price-source.provider", havingValue = "binance", matchIfMissing = true)
    PriceSource binancePriceSource(@Value("${binance.base-url}") String baseUrl) {
        return new BinancePriceSource(restClient(baseUrl));
    }

    @Bean
    @ConditionalOnProperty(name = "price-source.provider", havingValue = "kraken")
    PriceSource krakenPriceSource(@Value("${kraken.base-url}") String baseUrl) {
        return new KrakenPriceSource(restClient(baseUrl));
    }

    /**
     * Every upstream gets the same bounded client (see ADR 0002 for why this is built by hand):
     * connect timeout on the JDK HttpClient, read timeout on the Spring request factory.
     */
    private RestClient restClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
