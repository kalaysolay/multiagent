package com.example.workflow.confluence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Регистрирует ConfluenceApiClient только при заданном app.confluence.base-url.
 */
@Configuration
@ConditionalOnProperty(name = "app.confluence.base-url")
public class ConfluenceApiConfig {

    @Bean
    public ConfluenceApiClient confluenceApiClient(
            @Value("${app.confluence.base-url}") String baseUrl,
            @Value("${app.confluence.login:}") String login,
            @Value("${app.confluence.password:}") String password,
            WebClient.Builder webClientBuilder) {
        return new ConfluenceApiClient(baseUrl, login, password, webClientBuilder);
    }
}
