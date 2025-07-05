package com.stocker_back.stocker_back.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    @Qualifier("customRestTemplate")
    public RestTemplate customRestTemplate() {
        return new RestTemplate();
    }
} 