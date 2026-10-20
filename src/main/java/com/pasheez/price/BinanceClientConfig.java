package com.pasheez.price;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BinanceClientConfig {

    @Bean
    public RestClient binanceRestClient(
            @Value("${binance.base-url}") String baseUrl,
            @Value("${binance.connect-timeout}") Duration connectTimeout,
            @Value("${binance.read-timeout}") Duration readTimeout) {

        // Connect timeout lives on the JDK HttpClient (it bounds the TCP/TLS handshake);
        // read timeout lives on the Spring factory (it bounds the wait for a response).
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
