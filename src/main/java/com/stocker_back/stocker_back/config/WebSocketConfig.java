package com.stocker_back.stocker_back.config;

import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import com.stocker_back.stocker_back.scheduler.ScheduledWebSocketService;
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
    
    private static final int AUTO_CONNECT_DELAY_MS = 3000;
    private static final String MARKET_HOURS = "9:30 AM - 4:00 PM ET (Monday-Friday)";
    private static final int DATA_COLLECTION_INTERVAL_SEC = 10;
    
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
        log.info("WebSocket 서비스 초기화 시작");
        if (scheduledWebSocketEnabled) {
            initializeScheduledService();
        }
        if (autoConnectWebSocket) {
            initializeManualAutoConnect();
        } else {
            log.info("수동 WebSocket 자동 연결 비활성화됨");
        }
        log.info("WebSocket 서비스 초기화 완료");
    }

    private void initializeScheduledService() {
        log.info("스케줄링 WebSocket 서비스 활성화 (시장시간: {}, 수집주기: {}초)", MARKET_HOURS, DATA_COLLECTION_INTERVAL_SEC);
        scheduledWebSocketService.setScheduledWebSocketEnabled(true);
    }

    private void initializeManualAutoConnect() {
        log.info("수동 WebSocket 자동 연결 활성화");
        try {
            Thread.sleep(AUTO_CONNECT_DELAY_MS);
            multiKeyFinnhubWebSocketService.connectAll();
        } catch (Exception e) {
            log.error("WebSocket 자동 연결 실패", e);
        }
    }
} 