package com.mecklon.RagPoweredCodingAssistant.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a RestClient.Builder for outbound HTTP calls (e.g. the GitHub REST
 * API). In this project the standard auto-configuration bean is not present,
 * so we expose one explicitly.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}