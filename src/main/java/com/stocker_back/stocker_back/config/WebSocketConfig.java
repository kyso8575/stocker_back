package com.stocker_back.stocker_back.config;

import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * WebSocket 연결 설정 및 초기화 관리
 * 
 * 애플리케이션 시작시 두 가지 모드 중 선택:
 * 1. 스케줄링된 서비스 (권장): 시장 시간 기반 자동 관리
 * 2. 수동 연결 (테스트용): 즉시 연결 시작
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketConfig {
    
    private final MultiKeyFinnhubWebSocketService multiKeyFinnhubWebSocketService;
    private final ScheduledWebSocketService scheduledWebSocketService;
    
    @Value("${finnhub.websocket.auto-connect:false}")
    private boolean autoConnectWebSocket;
    
    @Value("${finnhub.scheduled.websocket.enabled:true}")
    private boolean scheduledWebSocketEnabled;
    
    /**
     * 애플리케이션 시작 후 데이터 수집 방식 초기화
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Application ready, initializing trade data collection...");
        
        if (scheduledWebSocketEnabled) {
            log.info("📅 Scheduled WebSocket service is enabled");
            log.info("   ├─ 🕐 Market hours monitoring: ACTIVE");
            log.info("   ├─ 🔄 WebSocket connection management: AUTOMATIC");
            log.info("   ├─ ⏱️  Data collection interval: Every 10 seconds during market hours");
            log.info("   └─ 🇺🇸 Market hours: 9:30 AM - 4:00 PM ET (Monday-Friday)");
            
            scheduledWebSocketService.setScheduledWebSocketEnabled(true);
        }
        
        if (autoConnectWebSocket) {
            log.info("🔧 Manual WebSocket auto-connect is enabled (will run alongside scheduled service)");
            try {
                Thread.sleep(3000);
                multiKeyFinnhubWebSocketService.connectAll();
            } catch (Exception e) {
                log.error("❌ Failed to auto-connect WebSocket", e);
            }
        } else {
            log.info("⚙️  Manual WebSocket auto-connect is disabled. Use scheduled service or API endpoints to connect.");
        }
        
        log.info("✅ WebSocket service initialization completed");
    }
} 