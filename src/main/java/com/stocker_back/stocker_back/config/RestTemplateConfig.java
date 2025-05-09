package com.stocker_back.stocker_back.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean(name = "customRestTemplate")
    @Primary
    public RestTemplate customRestTemplate(RestTemplateBuilder builder) {
        // 커스텀 ObjectMapper 생성
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        // RestTemplate에 커스텀 ObjectMapper 설정
        return builder
            .messageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }
} 