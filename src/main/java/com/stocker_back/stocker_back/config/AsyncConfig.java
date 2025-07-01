package com.stocker_back.stocker_back.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정 클래스
 * 
 * 이 클래스는 애플리케이션에서 비동기 작업을 처리하기 위한 스레드 풀 설정을 담당합니다.
 * 주로 다음과 같은 작업에서 사용됩니다:
 * - 주식 데이터 수집 (API 호출)
 * - 배치 처리 작업
 * - 백그라운드 데이터 업데이트
 * 
 * @EnableAsync: Spring의 비동기 처리 기능을 활성화
 * @Configuration: Spring 설정 클래스임을 명시
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 최대 스레드 수 (기본값: 4)
     * 동시에 실행할 수 있는 최대 작업 수를 제한합니다.
     * CPU 코어 수와 시스템 리소스를 고려하여 설정해야 합니다.
     */
    @Value("${app.parallel.max-threads:4}")
    private int maxThreads;

    /**
     * 작업 큐 용량 (기본값: 100)
     * 스레드 풀이 가득 찼을 때 대기할 작업의 최대 개수입니다.
     * 너무 크면 메모리 사용량이 증가하고, 너무 작으면 작업이 거부될 수 있습니다.
     */
    @Value("${app.parallel.queue-capacity:100}")
    private int queueCapacity;

    /**
     * 비동기 작업을 처리할 스레드 풀 Executor를 생성합니다.
     * 
     * 스레드 풀 설정:
     * - Core Pool Size: 항상 유지할 스레드 수 (maxThreads와 동일)
     * - Max Pool Size: 최대 스레드 수 (maxThreads와 동일)
     * - Queue Capacity: 대기 큐 용량
     * - Thread Name Prefix: 스레드 이름 접두사 (디버깅용)
     * 
     * @return 설정된 ThreadPoolTaskExecutor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 핵심 스레드 풀 크기 설정 (항상 유지할 스레드 수)
        executor.setCorePoolSize(maxThreads);
        
        // 최대 스레드 풀 크기 설정 (최대 동시 실행 가능한 스레드 수)
        executor.setMaxPoolSize(maxThreads);
        
        // 작업 대기 큐 용량 설정
        executor.setQueueCapacity(queueCapacity);
        
        // 스레드 이름 접두사 설정 (로그에서 스레드 식별용)
        executor.setThreadNamePrefix("StockSymbol-");
        
        // 스레드 풀 초기화
        executor.initialize();
        
        return executor;
    }
} 