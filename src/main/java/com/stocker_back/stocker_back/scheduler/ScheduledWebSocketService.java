package com.stocker_back.stocker_back.scheduler;

import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
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
 * - 시장 개장 30분 전 (9:00 AM ET): WebSocket 연결 및 구독 완료
 * - 시장 개장 시간 (9:30 AM ET): 데이터 저장 시작
 * - 시장 마감 후 (4:00 PM ET): WebSocket 연결 해제
 * - 설정 가능한 간격으로 연결 상태 모니터링
 * 
 * 시장 시간: 9:30 AM - 4:00 PM ET (월-금)
 * 준비 시간: 9:00 AM - 9:30 AM ET (연결/구독 완료)
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
    private volatile boolean isPreMarketSetup = false; // 시장 전 준비 시간
    private volatile boolean isConnected = false;
    private volatile boolean isDataSavingActive = false; // 데이터 저장 활성화 상태
    private ScheduledExecutorService monitoringScheduler;
    
    @PostConstruct
    public void initializeMonitoring() {
        this.monitoringScheduler = Executors.newScheduledThreadPool(1);
        
        log.info("🔧 ScheduledWebSocketService initialized");
        log.info("⏰ Pre-market setup: 9:00 AM ET (connection & subscription)");
        log.info("⏰ Market hours data saving: 9:30 AM - 4:00 PM ET");
        log.info("⏰ Market status check: every 60 seconds");
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
     * 미국 주식 시장 시간 확인 및 상태 관리 (매분마다 체크)
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void checkMarketHours() {
        if (!scheduledWebSocketEnabled) {
            return;
        }
        
        boolean currentPreMarketStatus = isPreMarketSetupTime();
        boolean currentMarketStatus = isUSMarketOpen();
        
        // 1. Pre-market setup 시간 (9:00 AM ET) - 연결 및 구독
        if (currentPreMarketStatus && !isPreMarketSetup) {
            isPreMarketSetup = true;
            log.info("🟡 PRE-MARKET SETUP TIME - Starting WebSocket connections and subscriptions");
            connectAndSubscribeWebSocket();
            
        // 2. Market open 시간 (9:30 AM ET) - 데이터 저장 시작  
        } else if (currentMarketStatus && !isMarketHours) {
            isMarketHours = true;
            log.info("🟢 US MARKET OPENED - Starting data saving (connections should already be ready)");
            startDataSaving();
            
        // 3. Market close 시간 (4:00 PM ET) - 저장 중단 및 연결 해제
        } else if (!currentMarketStatus && isMarketHours) {
            isMarketHours = false;
            isPreMarketSetup = false;
            log.info("🔴 US MARKET CLOSED - Stopping data saving and disconnecting WebSocket");
            stopDataSavingAndDisconnect();
        }
    }
    
    /**
     * 설정 가능한 간격으로 WebSocket 연결 상태 확인 및 재연결
     */
    public void monitorWebSocketConnection() {
        if (!scheduledWebSocketEnabled) {
            return;
        }
        
        // 현재 시장 상태 업데이트
        boolean currentPreMarketStatus = isPreMarketSetupTime();
        boolean currentMarketStatus = isUSMarketOpen();
        
        // 시장 상태가 변경되었으면 업데이트
        if (currentPreMarketStatus && !isPreMarketSetup) {
            isPreMarketSetup = true;
            log.info("🟡 PRE-MARKET SETUP DETECTED - Starting WebSocket connections");
            connectAndSubscribeWebSocket();
        } else if (currentMarketStatus && !isMarketHours) {
            isMarketHours = true;
            log.info("🟢 MARKET HOURS DETECTED - Starting data saving");
            startDataSaving();
        }
        
        // Pre-market이나 market 시간에만 모니터링
        if (!isPreMarketSetup && !isMarketHours) {
            return;
        }
        
        boolean actuallyConnected = multiKeyWebSocketService.isAnyConnected();
        
        // 연결이 안 되어 있고 활성 시간이면 연결 시도
        if (!actuallyConnected) {
            if (isConnected) {
                log.warn("⚠️ WebSocket connection lost during market/pre-market hours - attempting reconnection");
            } else {
                log.warn("⚠️ WebSocket not connected during active hours - attempting initial connection");
            }
            connectAndSubscribeWebSocket();
        } else if (actuallyConnected && !isConnected) {
            log.info("✅ WebSocket connection restored");
            isConnected = true;
        }
        
        // 시장 시간이고 연결되어 있지만 데이터 저장이 비활성화되어 있으면 활성화
        if (isMarketHours && actuallyConnected && !isDataSavingActive) {
            log.info("💾 Market hours detected with active connection - enabling data saving");
            startDataSaving();
        }
        
        // 연결 상태 로깅 (디버그용)
        if (actuallyConnected) {
            log.debug("📡 WebSocket monitoring: Connected (pre-market: {}, market: {}, data saving: {})", 
                    isPreMarketSetup, isMarketHours, isDataSavingActive);
        } else {
            log.warn("❌ WebSocket monitoring: Not connected during active hours - will retry on next check");
        }
    }
    
    /**
     * Pre-market setup 시간인지 확인 (9:00 AM - 9:30 AM ET)
     * @return true if it's pre-market setup time
     */
    private boolean isPreMarketSetupTime() {
        ZonedDateTime nowET = ZonedDateTime.now(ZoneId.of("America/New_York"));
        DayOfWeek dayOfWeek = nowET.getDayOfWeek();
        LocalTime timeET = nowET.toLocalTime();
        
        // 주말 제외
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        // Pre-market setup 시간: 9:00 AM - 9:30 AM ET
        LocalTime preMarketStart = LocalTime.of(9, 0);
        LocalTime marketOpen = LocalTime.of(9, 30);
        
        return (timeET.equals(preMarketStart) || timeET.isAfter(preMarketStart)) && timeET.isBefore(marketOpen);
    }
    
    /**
     * 미국 주식 시장 개장 시간 확인 (9:30 AM - 4:00 PM ET)
     * @return true if US market is open
     */
    private boolean isUSMarketOpen() {
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
     * WebSocket 연결 및 구독 시작 (Pre-market setup 시간)
     */
    private void connectAndSubscribeWebSocket() {
        try {
            if (!multiKeyWebSocketService.isAnyConnected()) {
                log.info("🔌 Connecting to Finnhub WebSocket for pre-market setup");
                multiKeyWebSocketService.connectAll();
                
                // 연결 및 구독 완료 확인을 위한 대기
                Thread.sleep(5000);
                
                if (multiKeyWebSocketService.isAnyConnected()) {
                    log.info("✅ WebSocket connections and subscriptions established successfully");
                    log.info("📡 Ready for market open at 9:30 AM ET");
                    isConnected = true;
                } else {
                    log.error("❌ Failed to establish WebSocket connections during pre-market setup");
                    isConnected = false;
                }
            } else {
                log.debug("✅ WebSocket already connected");
                isConnected = true;
            }
        } catch (Exception e) {
            log.error("❌ Error during WebSocket connection setup: {}", e.getMessage(), e);
            isConnected = false;
        }
    }
    
    /**
     * 데이터 저장 활성화 (시장 개장 시간)
     */
    private void startDataSaving() {
        try {
            if (multiKeyWebSocketService.isAnyConnected()) {
                log.info("💾 Enabling data saving for real-time trade data");
                multiKeyWebSocketService.enableDataSaving();
                isDataSavingActive = true;
                log.info("✅ Data saving enabled - collecting real-time trade data");
            } else {
                log.warn("⚠️ Cannot enable data saving - WebSocket not connected");
                isDataSavingActive = false;
            }
        } catch (Exception e) {
            log.error("❌ Error enabling data saving: {}", e.getMessage(), e);
            isDataSavingActive = false;
        }
    }
    
    /**
     * 데이터 저장 중단 및 WebSocket 연결 해제 (시장 마감 시간)
     */
    private void stopDataSavingAndDisconnect() {
        try {
            log.info("🛑 Stopping data saving and disconnecting WebSocket");
            
            // 데이터 저장 중단
            multiKeyWebSocketService.disableDataSaving();
            isDataSavingActive = false;
            
            // WebSocket 연결 해제
            multiKeyWebSocketService.disconnectAll();
            isConnected = false;
            
            log.info("✅ Data saving stopped and WebSocket disconnected");
            log.info("📅 Next market session: Tomorrow at 9:00 AM ET (pre-market setup)");
            
        } catch (Exception e) {
            log.error("❌ Error during WebSocket cleanup: {}", e.getMessage(), e);
        }
    }
    
    // ===== Public Status Methods =====
    
    public boolean isMarketHours() {
        return isMarketHours;
    }
    
    public boolean isPreMarketSetup() {
        return isPreMarketSetup;
    }
    
    public boolean isDataSavingActive() {
        return isDataSavingActive;
    }
    
    public boolean isWebSocketConnected() {
        return isConnected && multiKeyWebSocketService.isAnyConnected();
    }
    
    public void setScheduledWebSocketEnabled(boolean enabled) {
        this.scheduledWebSocketEnabled = enabled;
        log.info("Scheduled WebSocket service {} by admin", enabled ? "enabled" : "disabled");
    }
    
    public boolean isScheduledWebSocketEnabled() {
        return scheduledWebSocketEnabled;
    }
    
    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }
    
    public String getNextMarketEvent() {
        ZonedDateTime nowET = ZonedDateTime.now(ZoneId.of("America/New_York"));
        
        if (isPreMarketSetup) {
            // Pre-market setup 중이면 시장 개장 시간
            ZonedDateTime marketOpen = nowET.toLocalDate().atTime(9, 30).atZone(ZoneId.of("America/New_York"));
            if (nowET.isAfter(marketOpen)) {
                marketOpen = marketOpen.plusDays(1);
            }
            return "Market opens at: " + marketOpen.toString();
        } else if (isMarketHours) {
            // 시장이 열려있으면 마감 시간
            ZonedDateTime marketClose = nowET.toLocalDate().atTime(16, 0).atZone(ZoneId.of("America/New_York"));
            if (nowET.isAfter(marketClose)) {
                marketClose = marketClose.plusDays(1);
            }
            return "Market closes at: " + marketClose.toString();
        } else {
            // 시장이 닫혀있으면 다음 pre-market setup 시간
            ZonedDateTime preMarketSetup = nowET.toLocalDate().atTime(9, 0).atZone(ZoneId.of("America/New_York"));
            if (nowET.isAfter(preMarketSetup)) {
                preMarketSetup = preMarketSetup.plusDays(1);
            }
            // 주말 건너뛰기
            while (preMarketSetup.getDayOfWeek() == DayOfWeek.SATURDAY || preMarketSetup.getDayOfWeek() == DayOfWeek.SUNDAY) {
                preMarketSetup = preMarketSetup.plusDays(1);
            }
            return "Pre-market setup starts at: " + preMarketSetup.toString();
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("🧹 Cleaning up ScheduledWebSocketService");
        if (monitoringScheduler != null) {
            monitoringScheduler.shutdownNow();
        }
    }
} 