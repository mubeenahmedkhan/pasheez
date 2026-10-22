package com.pasheez.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The React dev server runs on a different port than this API, which makes every call from
 * the browser a cross-origin request. Without an explicit opt-in here the browser discards
 * responses it has already received — the server looks healthy while the UI reports failure.
 * <p>
 * Origins come from configuration because they differ per environment: localhost in dev, the
 * deployed frontend's domains in production. They are <em>patterns</em> ({@code allowedOriginPatterns})
 * rather than exact origins, because Vercel gives every pull request a fresh preview hostname
 * that a fixed list cannot anticipate, e.g. {@code https://pasheez-*-yourteam.vercel.app}.
 * Never a bare {@code *}: that would let any site on the internet read this API through a
 * visitor's browser.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    CorsConfig(@Value("${frontend.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/prices/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET");
    }
}
