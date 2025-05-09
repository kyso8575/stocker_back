package com.stocker_back.stocker_back.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class FinnhubApiConfig {

    @Value("${finnhub.api.key}")
    private String apiKey;
    
    @Value("${finnhub.api.connect-timeout:5000}")
    private int connectTimeout; // 기본값 5초
    
    @Value("${finnhub.api.read-timeout:10000}")
    private int readTimeout; // 기본값 10초
    
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
    
    @Bean("finnhubRestTemplate")
    public RestTemplate finnhubRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
    
    public String getApiKey() {
        return apiKey;
    }
} 