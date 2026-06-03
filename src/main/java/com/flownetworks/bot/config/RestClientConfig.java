package com.flownetworks.bot.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * RestTemplate dùng chung cho các external API (Coda, Jira) với timeout
     * để tránh request treo vô hạn khi service bên ngoài không phản hồi.
     */
    @Bean
    public RestTemplate externalApiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }
}
