package com.stocker_back.stocker_back.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(servers());
    }

    private Info apiInfo() {
        return new Info()
            .title("Stocker Back API")
            .description("""
                주식 투자 플랫폼 백엔드 API
                
                ## 주요 기능
                - 실시간 주식 시세 조회
                - 관심 종목 관리
                - 모의 투자 계좌
                - 재무 지표 분석
                - S&P 500 데이터
                
                ## 인증
                - 세션 기반 인증 사용
                - 관리자 권한이 필요한 API는 별도 표시
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("Stocker Team")
                .email("support@stocker.com"));
    }

    private List<Server> servers() {
        return List.of(
            new Server()
                .url("http://localhost:8080")
                .description("개발 서버")
        );
    }
}
