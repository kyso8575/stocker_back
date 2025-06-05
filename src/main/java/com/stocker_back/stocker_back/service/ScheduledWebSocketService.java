package com.stocker_back.stocker_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 미국 주식 시장 시간 기반 WebSocket 연결 관리 서비스
 * 
 * 주요 기능:
 * - 매분마다 미국 시장 시간 체크 
 * - 시장 개장시 자동 WebSocket 연결
 * - 설정 가능한 간격으로 연결 상태 모니터링
 * - 시장 마감시 자동 WebSocket 해제
 * 
 * 시장 시간: 9:30 AM - 4:00 PM ET (월-금)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledWebSocketService {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    
    @Value("${finnhub.scheduled.websocket.enabled:true}")
    private boolean scheduledWebSocketEnabled;
    
    @Value("${finnhub.scheduled.websocket.monitor-interval-ms:10000}")
    private long monitorIntervalMs;
    
    private volatile boolean isMarketHours = false;
    private volatile boolean isConnected = false;
    private ScheduledExecutorService monitoringScheduler;
    
    @PostConstruct
    public void initializeMonitoring() {
        this.monitoringScheduler = Executors.newScheduledThreadPool(1);
        
        log.info("🔧 ScheduledWebSocketService initialized");
        log.info("⏰ Market hours check: every 60 seconds");
        log.info("⏰ Connection monitoring: every {} ms", monitorIntervalMs);
        
        // 설정 가능한 간격으로 모니터링 시작
        monitoringScheduler.scheduleAtFixedRate(
            this::monitorWebSocketConnection, 
            0, 
            monitorIntervalMs, 
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 미국 주식 시장 시간 확인 (매분마다 체크)
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void checkMarketHours() {
        if (!scheduledWebSocketEnabled) {
            return;
        }
        
        boolean currentMarketStatus = isUSMarketOpen();
        
        if (currentMarketStatus != isMarketHours) {
            isMarketHours = currentMarketStatus;
            
            if (isMarketHours) {
                log.info("🟢 US Market OPENED - Starting WebSocket connections");
                connectWebSocket();
            } else {
                log.info("🔴 US Market CLOSED - Stopping WebSocket connections");
                disconnectWebSocket();
            }
        }
    }
    
    /**
     * 설정 가능한 간격으로 WebSocket 연결 상태 확인 및 재연결 (시장 시간에만)
     */
    public void monitorWebSocketConnection() {
        if (!scheduledWebSocketEnabled || !isMarketHours) {
            return;
        }
        
        boolean actuallyConnected = multiKeyWebSocketService.isAnyConnected();
        
        if (!actuallyConnected && isConnected) {
            log.warn("⚠️ WebSocket connection lost during market hours - attempting reconnection");
            connectWebSocket();
        } else if (actuallyConnected && !isConnected) {
            log.info("✅ WebSocket connection restored");
            isConnected = true;
        }
        
        // 설정된 간격마다 연결 상태 로깅 (디버그용)
        if (actuallyConnected) {
            log.debug("📡 WebSocket monitoring: Connected and active (interval: {}ms, market hours: {})", 
                    monitorIntervalMs, isMarketHours);
        } else {
            log.warn("❌ WebSocket monitoring: Not connected during market hours");
        }
    }
    
    /**
     * 미국 주식 시장 개장 시간 확인
     * @return true if US market is open
     */
    private boolean isUSMarketOpen() {
        // 미국 동부 시간 (EST/EDT)
        ZonedDateTime nowET = ZonedDateTime.now(ZoneId.of("America/New_York"));
        DayOfWeek dayOfWeek = nowET.getDayOfWeek();
        LocalTime timeET = nowET.toLocalTime();
        
        // 주말 제외
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        // 정규 거래 시간: 9:30 AM - 4:00 PM ET
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);
        
        return timeET.isAfter(marketOpen) && timeET.isBefore(marketClose);
    }
    
    /**
     * WebSocket 연결 시작
     */
    private void connectWebSocket() {
        try {
            if (!multiKeyWebSocketService.isAnyConnected()) {
                log.info("🔌 Connecting to Finnhub WebSocket for market hours data collection");
                multiKeyWebSocketService.connectAll();
                isConnected = true;
                
                // 연결 확인을 위한 짧은 지연
                Thread.sleep(3000);
                
                if (multiKeyWebSocketService.isAnyConnected()) {
                    log.info("✅ WebSocket connections established successfully");
                } else {
                    log.error("❌ Failed to establish WebSocket connections");
                    isConnected = false;
                }
            } else {
                log.debug("✅ WebSocket already connected");
                isConnected = true;
            }
        } catch (Exception e) {
            log.error("❌ Error connecting WebSocket", e);
            isConnected = false;
        }
    }
    
    /**
     * WebSocket 연결 종료
     */
    private void disconnectWebSocket() {
        try {
            if (multiKeyWebSocketService.isAnyConnected()) {
                log.info("🔌 Disconnecting WebSocket connections (market closed)");
                multiKeyWebSocketService.disconnectAll();
                isConnected = false;
                log.info("✅ WebSocket connections disconnected successfully");
            } else {
                log.debug("✅ WebSocket already disconnected");
                isConnected = false;
            }
        } catch (Exception e) {
            log.error("❌ Error disconnecting WebSocket", e);
            isConnected = false;
        }
    }
    
    // ===== Public API Methods =====
    
    /**
     * 현재 시장 시간 상태 조회
     */
    public boolean isMarketHours() {
        return isMarketHours;
    }
    
    /**
     * 현재 WebSocket 연결 상태 조회
     */
    public boolean isWebSocketConnected() {
        return isConnected && multiKeyWebSocketService.isAnyConnected();
    }
    
    /**
     * 스케줄링된 WebSocket 서비스 활성화/비활성화
     */
    public void setScheduledWebSocketEnabled(boolean enabled) {
        this.scheduledWebSocketEnabled = enabled;
        log.info("📋 Scheduled WebSocket service {}", enabled ? "enabled" : "disabled");
        
        if (!enabled && isConnected) {
            disconnectWebSocket();
        }
    }
    
    /**
     * 현재 스케줄링 상태 확인
     */
    public boolean isScheduledWebSocketEnabled() {
        return scheduledWebSocketEnabled;
    }
    
    /**
     * 현재 모니터링 간격 조회 (밀리초)
     */
    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }
    
    /**
     * 다음 시장 개장/마감 시간 정보
     */
    public String getNextMarketEvent() {
        ZonedDateTime nowET = ZonedDateTime.now(ZoneId.of("America/New_York"));
        
        if (isUSMarketOpen()) {
            // 시장이 열려있으면 마감 시간
            ZonedDateTime marketClose = nowET.toLocalDate().atTime(16, 0).atZone(ZoneId.of("America/New_York"));
            if (nowET.isAfter(marketClose)) {
                marketClose = marketClose.plusDays(1);
            }
            return "Market closes at: " + marketClose.toString();
        } else {
            // 시장이 닫혀있으면 개장 시간
            ZonedDateTime marketOpen = nowET.toLocalDate().atTime(9, 30).atZone(ZoneId.of("America/New_York"));
            if (nowET.isAfter(marketOpen)) {
                marketOpen = marketOpen.plusDays(1);
            }
            // 주말 건너뛰기
            while (marketOpen.getDayOfWeek() == DayOfWeek.SATURDAY || marketOpen.getDayOfWeek() == DayOfWeek.SUNDAY) {
                marketOpen = marketOpen.plusDays(1);
            }
            return "Market opens at: " + marketOpen.toString();
        }
    }

    @PreDestroy
    public void cleanup() {
        // 서비스 종료 시 스케줄러를 정리하는 로직을 구현해야 합니다.
        log.info("🧹 Cleaning up ScheduledWebSocketService");
        monitoringScheduler.shutdownNow();
    }
} 