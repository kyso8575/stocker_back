package com.stocker_back.stocker_back.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final AuthenticationInterceptor authenticationInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/**") // 모든 API 경로에 적용
                .excludePathPatterns(
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/check-username",
                    "/api/auth/check-email",
                    "/api/dev/**" // 개발용 엔드포인트 제외
                ); // 공개 API는 제외
    }
} 