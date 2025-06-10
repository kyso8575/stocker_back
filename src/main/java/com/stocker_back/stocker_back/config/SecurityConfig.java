package com.stocker_back.stocker_back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for development
            .formLogin(form -> form.disable()) // Disable default form login
            .httpBasic(basic -> basic.disable()) // Disable HTTP Basic auth
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/check-username", "/api/auth/check-email").permitAll()
                .requestMatchers("/api/auth/me").permitAll() // Allow current user info access
                .requestMatchers("/api/auth/logout").permitAll() // Allow logout for now
                .requestMatchers("/api/auth/session-info").permitAll() // Allow session info access
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1) // 최대 1개 세션만 허용
                .maxSessionsPreventsLogin(false) // 새로운 로그인이 기존 세션을 만료시킴
                .and()
                .sessionFixation().changeSessionId() // 세션 고정 공격 방지
                .invalidSessionUrl("/api/auth/login") // 무효한 세션 접근 시 리다이렉트
            )
            .headers(headers -> headers
                .frameOptions().deny() // 클릭재킹 방지
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                    .preload(true)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            );
        
        return http.build();
    }
} 