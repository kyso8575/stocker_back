package com.stocker_back.stocker_back.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FinnhubApiConfig {

    @Value("${finnhub.api.key}")
    private String apiKey;
    
    @Value("${finnhub.api.connect-timeout:5000}")
    private int connectTimeout; // 기본값 5초
    
    @Value("${finnhub.api.read-timeout:10000}")
    private int readTimeout; // 기본값 10초
    
    /**
     * 통합 RestTemplate Bean - 타임아웃 및 JSON 처리 설정 포함
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        // 커스텀 ObjectMapper 생성
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // HTTP 클라이언트 팩토리 설정 (타임아웃)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        // RestTemplate 생성 및 설정
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(objectMapper));
        
        return restTemplate;
    }
    
    /**
     * Finnhub API 키 반환
     */
    public String getApiKey() {
        return apiKey;
    }
} 