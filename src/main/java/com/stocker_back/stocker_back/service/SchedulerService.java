package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.scheduler.FinancialMetricsSchedulerService;
import com.stocker_back.stocker_back.scheduler.MonthlyDataSchedulerService;
import com.stocker_back.stocker_back.scheduler.QuoteSchedulerService;
import com.stocker_back.stocker_back.scheduler.ScheduledWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final FinancialMetricsSchedulerService financialMetricsSchedulerService;
    private final MonthlyDataSchedulerService monthlyDataSchedulerService;
    private final QuoteSchedulerService quoteSchedulerService;
    private final ScheduledWebSocketService webSocketSchedulerService;

    public Map<String, Object> getComprehensiveSchedulerStatus() {
        String currentTime = financialMetricsSchedulerService.getCurrentEasternTime();
        
        Map<String, Object> response = new HashMap<>();
        
        // Financial Metrics Scheduler 상태 + 설정
        Map<String, Object> financialMetrics = createFinancialMetricsStatus(currentTime);
        
        // Monthly Data Scheduler 상태 + 설정
        Map<String, Object> monthlyData = createMonthlyDataStatus(currentTime);
        
        // Quote Scheduler 상태 + 설정
        Map<String, Object> quoteData = createQuoteStatus(currentTime);
        
        // WebSocket Scheduler 상태
        Map<String, Object> webSocketStatus = createWebSocketStatus();
        
        // Health 정보 추가
        Map<String, Object> healthInfo = performHealthCheck();
        boolean isHealthy = "healthy".equals(healthInfo.get("status"));
        
        response.put("success", isHealthy);
        response.put("health", healthInfo);
        response.put("financialMetricsScheduler", financialMetrics);
        response.put("monthlyDataScheduler", monthlyData);
        response.put("quoteScheduler", quoteData);
        response.put("webSocketScheduler", webSocketStatus);
        response.put("currentTime", currentTime);
        
        return response;
    }

    private Map<String, Object> createFinancialMetricsStatus(String currentTime) {
        Map<String, Object> financialMetrics = new HashMap<>();
        financialMetrics.put("currentEasternTime", currentTime);
        financialMetrics.put("nextScheduleInfo", financialMetricsSchedulerService.getNextScheduleInfo());
        financialMetrics.put("schedule", "Daily at 9:00 AM ET (Mon-Fri)");
        financialMetrics.put("purpose", "S&P 500 financial metrics collection");
        financialMetrics.put("mode", "FULLY_AUTOMATED");
        
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
        
        return financialMetrics;
    }

    private Map<String, Object> createMonthlyDataStatus(String currentTime) {
        Map<String, Object> monthlyData = new HashMap<>();
        monthlyData.put("currentEasternTime", currentTime);
        monthlyData.put("nextScheduleInfo", monthlyDataSchedulerService.getNextScheduleInfo());
        monthlyData.put("schedule", "Twice monthly: 1st & 15th at 8:00 AM ET");
        monthlyData.put("purpose", "S&P 500 list update & company profiles collection");
        monthlyData.put("mode", "FULLY_AUTOMATED");
        
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
        
        return monthlyData;
    }

    private Map<String, Object> createQuoteStatus(String currentTime) {
        Map<String, Object> quoteData = new HashMap<>();
        quoteData.put("currentEasternTime", currentTime);
        quoteData.put("nextScheduleInfo", quoteSchedulerService.getNextScheduleInfo());
        quoteData.put("schedule", "Daily at 4:30 PM ET (Mon-Fri)");
        quoteData.put("purpose", "S&P 500 daily closing quote collection");
        quoteData.put("mode", "FULLY_AUTOMATED");
        
        Map<String, Object> quoteConfig = new HashMap<>();
        quoteConfig.put("cronExpression", "0 30 16 * * MON-FRI");
        quoteConfig.put("timezone", "America/New_York");
        quoteConfig.put("description", "Every weekday at 4:30 PM Eastern Time (30 minutes after market close)");
        quoteConfig.put("targetSymbols", "S&P 500 stocks (503 symbols)");
        quoteConfig.put("dataType", "Daily closing quotes");
        quoteConfig.put("rateLimit", "60 requests/minute per API key");
        quoteConfig.put("estimatedDuration", "~8.4 minutes (503 symbols × 1 second)");
        quoteConfig.put("automation", "No manual intervention required");
        quoteData.put("config", quoteConfig);
        
        return quoteData;
    }

    private Map<String, Object> createWebSocketStatus() {
        Map<String, Object> webSocketStatus = new HashMap<>();
        webSocketStatus.put("isPreMarketSetup", webSocketSchedulerService.isPreMarketSetup());
        webSocketStatus.put("isMarketHours", webSocketSchedulerService.isMarketHours());
        webSocketStatus.put("isDataSavingActive", webSocketSchedulerService.isDataSavingActive());
        webSocketStatus.put("isWebSocketConnected", webSocketSchedulerService.isWebSocketConnected());
        webSocketStatus.put("nextMarketEvent", webSocketSchedulerService.getNextMarketEvent());
        webSocketStatus.put("schedule", "Pre-market: 9:00 AM, Market: 9:30 AM - 4:00 PM ET (Mon-Fri)");
        webSocketStatus.put("purpose", "Real-time trade data collection");
        webSocketStatus.put("mode", "FULLY_AUTOMATED");
        
        return webSocketStatus;
    }

    private Map<String, Object> performHealthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            // 헬스체크 로직
            financialMetricsSchedulerService.getCurrentEasternTime();
            monthlyDataSchedulerService.getCurrentEasternTime();
            quoteSchedulerService.getCurrentEasternTime();
            webSocketSchedulerService.isScheduledWebSocketEnabled();
            
            healthInfo.put("status", "healthy");
            healthInfo.put("financialMetricsService", "active");
            healthInfo.put("monthlyDataService", "active");
            healthInfo.put("quoteService", "active");
            healthInfo.put("webSocketService", "active");
            healthInfo.put("schedulerEnabled", true);
            healthInfo.put("automationLevel", "FULL");
            healthInfo.put("totalSchedulers", 4);
        } catch (Exception e) {
            healthInfo.put("status", "unhealthy");
            healthInfo.put("error", e.getMessage());
            healthInfo.put("automationLevel", "DEGRADED");
        }
        
        return healthInfo;
    }
} 