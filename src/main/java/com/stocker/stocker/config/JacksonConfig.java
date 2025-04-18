package com.stocker.stocker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 알 수 없는 프로퍼티가 있어도 디시리얼라이즈 실패하지 않도록 설정
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Java 8 날짜/시간 타입 지원을 위한 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
} 