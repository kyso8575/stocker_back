package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.FinancialMetricsSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 스케줄러 관리 컨트롤러
 * 자동 수집 스케줄 상태 확인 및 수동 실행
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Slf4j
public class SchedulerController {

    private final FinancialMetricsSchedulerService schedulerService;

    /**
     * 스케줄러 상태 및 다음 실행 시간 정보 조회
     * @return 스케줄러 상태 정보
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        log.info("Received request to get scheduler status");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("currentEasternTime", schedulerService.getCurrentEasternTime());
            response.put("nextScheduleInfo", schedulerService.getNextScheduleInfo());
            response.put("schedule", "Daily at 9:00 AM ET (Mon-Fri)");
            response.put("purpose", "S&P 500 financial metrics collection 30 minutes before market open");
            response.put("message", "Scheduler is active and running");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting scheduler status: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 수동으로 재무 지표 수집 실행
     * @return 실행 결과
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerManualCollection() {
        log.info("Received request to manually trigger financial metrics collection");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int processedCount = schedulerService.triggerManualCollection();
            
            response.put("success", true);
            response.put("processedCount", processedCount);
            response.put("executionTime", schedulerService.getCurrentEasternTime());
            response.put("message", String.format("Successfully processed %d S&P 500 symbols", processedCount));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error during manual collection trigger: {}", e.getMessage());
            
            response.put("success", false);
            response.put("executionTime", schedulerService.getCurrentEasternTime());
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 스케줄러 설정 정보 조회
     * @return 스케줄러 설정 정보
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getSchedulerConfig() {
        log.info("Received request to get scheduler configuration");
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        
        config.put("cronExpression", "0 0 9 * * MON-FRI");
        config.put("timezone", "America/New_York");
        config.put("description", "Every weekday at 9:00 AM Eastern Time");
        config.put("targetSymbols", "S&P 500 stocks (503 symbols)");
        config.put("batchSize", 20);
        config.put("rateLimit", "60 requests/minute per API key");
        config.put("estimatedDuration", "~8.4 minutes (503 symbols × 1 second)");
        
        response.put("success", true);
        response.put("config", config);
        response.put("currentTime", schedulerService.getCurrentEasternTime());
        response.put("nextExecution", schedulerService.getNextScheduleInfo());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 스케줄러 헬스체크
     * @return 시스템 상태
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String currentTime = schedulerService.getCurrentEasternTime();
            
            response.put("status", "healthy");
            response.put("service", "FinancialMetricsScheduler");
            response.put("currentTime", currentTime);
            response.put("schedulerActive", true);
            response.put("message", "Scheduler service is running normally");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Scheduler health check failed: {}", e.getMessage());
            
            response.put("status", "unhealthy");
            response.put("service", "FinancialMetricsScheduler");
            response.put("schedulerActive", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
} 