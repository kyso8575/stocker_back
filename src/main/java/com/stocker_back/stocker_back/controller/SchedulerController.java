package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.FinancialMetricsSchedulerService;
import com.stocker_back.stocker_back.service.MonthlyDataSchedulerService;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Scheduler", description = "자동화된 데이터 수집 스케줄러 관리 API")
public class SchedulerController {

    private final FinancialMetricsSchedulerService financialMetricsSchedulerService;
    private final MonthlyDataSchedulerService monthlyDataSchedulerService;
    private final ScheduledWebSocketService webSocketSchedulerService;

    @Operation(
        summary = "통합 스케줄러 상태 조회",
        description = "모든 자동화된 스케줄러(재무 지표, 월간 데이터, 웹소켓)의 상태와 설정을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "스케줄러 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        log.info("Received request to get comprehensive scheduler status");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentTime = financialMetricsSchedulerService.getCurrentEasternTime();
            
            // Financial Metrics Scheduler 상태 + 설정
            Map<String, Object> financialMetrics = new HashMap<>();
            financialMetrics.put("currentEasternTime", currentTime);
            financialMetrics.put("nextScheduleInfo", financialMetricsSchedulerService.getNextScheduleInfo());
            financialMetrics.put("schedule", "Daily at 9:00 AM ET (Mon-Fri)");
            financialMetrics.put("purpose", "S&P 500 financial metrics collection");
            financialMetrics.put("mode", "FULLY_AUTOMATED");
            
            // Financial Metrics 설정 정보
            Map<String, Object> financialConfig = new HashMap<>();
            financialConfig.put("cronExpression", "0 0 9 * * MON-FRI");
            financialConfig.put("timezone", "America/New_York");
            financialConfig.put("description", "Every weekday at 9:00 AM Eastern Time");
            financialConfig.put("targetSymbols", "S&P 500 stocks (503 symbols)");
            financialConfig.put("batchSize", 20);
            financialConfig.put("rateLimit", "60 requests/minute per API key");
            financialConfig.put("estimatedDuration", "~8.4 minutes (503 symbols × 1 second)");
            financialConfig.put("automation", "No manual intervention required");
            financialMetrics.put("config", financialConfig);
            
            // Monthly Data Scheduler 상태 + 설정 (NEW!)
            Map<String, Object> monthlyData = new HashMap<>();
            monthlyData.put("currentEasternTime", currentTime);
            monthlyData.put("nextScheduleInfo", monthlyDataSchedulerService.getNextScheduleInfo());
            monthlyData.put("schedule", "Twice monthly: 1st & 15th at 8:00 AM ET");
            monthlyData.put("purpose", "S&P 500 list update & company profiles collection");
            monthlyData.put("mode", "FULLY_AUTOMATED");
            
            // Monthly Data 설정 정보
            Map<String, Object> monthlyConfig = new HashMap<>();
            monthlyConfig.put("cronExpression", "0 0 8 1,15 * ?");
            monthlyConfig.put("timezone", "America/New_York");
            monthlyConfig.put("description", "Every 1st and 15th day of month at 8:00 AM Eastern Time");
            monthlyConfig.put("targetActions", "S&P 500 list scraping + Company profiles (503 symbols)");
            monthlyConfig.put("batchSize", 20);
            monthlyConfig.put("rateLimit", "60 requests/minute per API key");
            monthlyConfig.put("estimatedDuration", "~10-15 minutes (list update + 503 profiles)");
            monthlyConfig.put("automation", "No manual intervention required");
            monthlyData.put("config", monthlyConfig);
            
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
                // 헬스체크 로직
                financialMetricsSchedulerService.getCurrentEasternTime();
                monthlyDataSchedulerService.getCurrentEasternTime();
                webSocketSchedulerService.isScheduledWebSocketEnabled();
                
                healthInfo.put("status", "healthy");
                healthInfo.put("financialMetricsService", "active");
                healthInfo.put("monthlyDataService", "active");
                healthInfo.put("webSocketService", "active");
                healthInfo.put("schedulerEnabled", true);
                healthInfo.put("automationLevel", "FULL");
                healthInfo.put("totalSchedulers", 3);
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
            response.put("monthlyDataScheduler", monthlyData);
            response.put("webSocketScheduler", webSocketStatus);
            response.put("currentTime", currentTime);
            response.put("message", healthMessage);
            response.put("note", "This is a fully automated system with 3 schedulers - no manual intervention required");
            
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