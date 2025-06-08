package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.FinancialMetricsSchedulerService;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 스케줄러 상태 조회 컨트롤러
 * 자동 스케줄링 시스템의 완전 통합 모니터링
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Slf4j
public class SchedulerController {

    private final FinancialMetricsSchedulerService schedulerService;
    private final ScheduledWebSocketService webSocketSchedulerService;

    /**
     * 통합 스케줄러 상태 조회 (Health + Financial Metrics + WebSocket + Config)
     * @return 모든 스케줄러 정보 통합
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        log.info("Received request to get comprehensive scheduler status");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentTime = schedulerService.getCurrentEasternTime();
            
            // Financial Metrics Scheduler 상태 + 설정
            Map<String, Object> financialMetrics = new HashMap<>();
            financialMetrics.put("currentEasternTime", currentTime);
            financialMetrics.put("nextScheduleInfo", schedulerService.getNextScheduleInfo());
            financialMetrics.put("schedule", "Daily at 9:00 AM ET (Mon-Fri)");
            financialMetrics.put("purpose", "S&P 500 financial metrics collection");
            financialMetrics.put("mode", "FULLY_AUTOMATED");
            
            // 설정 정보 추가
            Map<String, Object> config = new HashMap<>();
            config.put("cronExpression", "0 0 9 * * MON-FRI");
            config.put("timezone", "America/New_York");
            config.put("description", "Every weekday at 9:00 AM Eastern Time");
            config.put("targetSymbols", "S&P 500 stocks (503 symbols)");
            config.put("batchSize", 20);
            config.put("rateLimit", "60 requests/minute per API key");
            config.put("estimatedDuration", "~8.4 minutes (503 symbols × 1 second)");
            config.put("automation", "No manual intervention required");
            financialMetrics.put("config", config);
            
            // WebSocket Scheduler 상태
            Map<String, Object> webSocketStatus = new HashMap<>();
            webSocketStatus.put("isPreMarketSetup", webSocketSchedulerService.isPreMarketSetup());
            webSocketStatus.put("isMarketHours", webSocketSchedulerService.isMarketHours());
            webSocketStatus.put("isDataSavingActive", webSocketSchedulerService.isDataSavingActive());
            webSocketStatus.put("isWebSocketConnected", webSocketSchedulerService.isWebSocketConnected());
            webSocketStatus.put("nextMarketEvent", webSocketSchedulerService.getNextMarketEvent());
            webSocketStatus.put("schedule", "Pre-market: 9:00 AM, Market: 9:30 AM - 4:00 PM ET (Mon-Fri)");
            webSocketStatus.put("purpose", "Real-time trade data collection");
            webSocketStatus.put("mode", "FULLY_AUTOMATED");
            
            // Health 정보 추가
            Map<String, Object> healthInfo = new HashMap<>();
            boolean isHealthy = true;
            String healthMessage = "All automated scheduler services are running normally";
            
            try {
                // 간단한 헬스체크 로직
                schedulerService.getCurrentEasternTime();
                webSocketSchedulerService.isScheduledWebSocketEnabled();
                
                healthInfo.put("status", "healthy");
                healthInfo.put("financialMetricsService", "active");
                healthInfo.put("webSocketService", "active");
                healthInfo.put("schedulerEnabled", true);
                healthInfo.put("automationLevel", "FULL");
            } catch (Exception e) {
                isHealthy = false;
                healthMessage = "Some automated scheduler services have issues: " + e.getMessage();
                healthInfo.put("status", "unhealthy");
                healthInfo.put("error", e.getMessage());
                healthInfo.put("automationLevel", "DEGRADED");
            }
            
            response.put("success", isHealthy);
            response.put("health", healthInfo);
            response.put("financialMetricsScheduler", financialMetrics);
            response.put("webSocketScheduler", webSocketStatus);
            response.put("currentTime", currentTime);
            response.put("message", healthMessage);
            response.put("note", "This is a fully automated system - no manual intervention required");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting comprehensive scheduler status: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("health", Map.of("status", "unhealthy", "error", e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 