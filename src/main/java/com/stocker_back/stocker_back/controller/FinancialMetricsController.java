package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import com.stocker_back.stocker_back.service.FinancialMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/financial-metrics")  // stocks 제거
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Financial Metrics", description = "재무 지표 API (일반 사용자용)")
public class FinancialMetricsController {

    private final FinancialMetricsService financialMetricsService;
    private final FinancialMetricsRepository financialMetricsRepository;

    @Operation(
        summary = "단일 회사 재무 지표 조회",
        description = "특정 주식 심볼의 최신 재무 지표를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "재무 지표 조회 성공"),
        @ApiResponse(responseCode = "404", description = "재무 지표를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getFinancialMetrics(@PathVariable String symbol) {
        log.info("Received request to get financial metrics for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            Optional<FinancialMetrics> metricsOpt = financialMetricsRepository.findTopBySymbolOrderByCreatedAtDesc(symbol.toUpperCase());
            
            if (metricsOpt.isPresent()) {
                response.put("success", true);
                response.put("data", metricsOpt.get());
                response.put("message", String.format("Successfully retrieved financial metrics for %s", symbol));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", String.format("No financial metrics found for %s", symbol));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving financial metrics for symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "재무 지표 기록 조회",
        description = "특정 주식 심볼의 재무 지표 기록을 조회합니다. (선택적 날짜 범위 필터링)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "재무 지표 기록 조회 성공"),
        @ApiResponse(responseCode = "404", description = "재무 지표 기록을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{symbol}/history")
    public ResponseEntity<Map<String, Object>> getFinancialMetricsHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        log.info("Received request to get financial metrics history for symbol: {} (from: {}, to: {})", 
                symbol, from, to);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            List<FinancialMetrics> metricsHistory;
            
            // 날짜 범위가 지정된 경우
            if ((from != null && !from.isEmpty()) || (to != null && !to.isEmpty())) {
                LocalDateTime fromDate = parseDateTime(from, true);  // true = start of day
                LocalDateTime toDate = parseDateTime(to, false);     // false = end of day
                
                log.info("Querying financial metrics history with date range: {} to {}", fromDate, toDate);
                
                metricsHistory = financialMetricsRepository.findBySymbolAndCreatedAtBetweenOrderByCreatedAtDesc(
                        symbol.toUpperCase(), fromDate, toDate);
                
                response.put("fromDate", fromDate);
                response.put("toDate", toDate);
            } else {
                // 날짜 범위가 지정되지 않은 경우 모든 기록 조회
                log.info("Querying all financial metrics history for symbol: {}", symbol);
                metricsHistory = financialMetricsRepository.findBySymbolOrderByCreatedAtDesc(symbol.toUpperCase());
            }
            
            if (!metricsHistory.isEmpty()) {
                response.put("success", true);
                response.put("data", metricsHistory);
                response.put("count", metricsHistory.size());
                
                if ((from != null && !from.isEmpty()) || (to != null && !to.isEmpty())) {
                    response.put("message", String.format("Successfully retrieved %d financial metrics records for %s within date range", 
                            metricsHistory.size(), symbol));
                } else {
                    response.put("message", String.format("Successfully retrieved %d financial metrics records for %s", 
                            metricsHistory.size(), symbol));
                }
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("data", metricsHistory);
                response.put("count", 0);
                
                if ((from != null && !from.isEmpty()) || (to != null && !to.isEmpty())) {
                    response.put("message", String.format("No financial metrics history found for %s within the specified date range", symbol));
                } else {
                    response.put("message", String.format("No financial metrics history found for %s", symbol));
                }
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving financial metrics history for symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 날짜 문자열을 LocalDateTime으로 파싱하는 헬퍼 메서드
     * @param dateStr 날짜 문자열 (YYYY-MM-DD 또는 YYYY-MM-DDTHH:mm:ss)
     * @param isStartOfDay true이면 하루의 시작(00:00:00), false이면 하루의 끝(23:59:59)
     * @return LocalDateTime 객체
     */
    private LocalDateTime parseDateTime(String dateStr, boolean isStartOfDay) {
        if (dateStr == null || dateStr.isEmpty()) {
            // 기본값: from은 과거 1년, to는 현재 시간
            return isStartOfDay ? 
                LocalDateTime.now().minusYears(1) : 
                LocalDateTime.now();
        }
        
        try {
            // YYYY-MM-DD 형식인 경우
            if (dateStr.length() == 10 && dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(dateStr);
                return isStartOfDay ? 
                    date.atStartOfDay() : 
                    date.atTime(23, 59, 59);
            }
            // YYYY-MM-DDTHH:mm:ss 형식인 경우
            else if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            // 기타 형식 시도
            else {
                LocalDate date = LocalDate.parse(dateStr);
                return isStartOfDay ? 
                    date.atStartOfDay() : 
                    date.atTime(23, 59, 59);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date: {}. Using default value.", dateStr);
            return isStartOfDay ? 
                LocalDateTime.now().minusYears(1) : 
                LocalDateTime.now();
        }
    }

    @Operation(
        summary = "재무 지표 일괄 수집",
        description = "여러 주식 심볼의 재무 지표를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "재무 지표 일괄 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/batch")
    public ResponseEntity<Map<String, Object>> fetchAllFinancialMetrics(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch financial metrics for all symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int savedCount = financialMetricsService.fetchAndSaveAllBasicFinancials(batchSize, delayMs);
            
            response.put("success", true);
            response.put("processedCount", savedCount);
            response.put("message", String.format("Successfully processed financial metrics for %d symbols", savedCount));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error fetching financial metrics: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "단일 회사 재무 지표 수집",
        description = "특정 주식 심볼의 재무 지표를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "재무 지표 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/symbol/{symbol}")  // 관리자용 경로
    public ResponseEntity<Map<String, Object>> fetchFinancialMetrics(@PathVariable String symbol) {
        log.info("Received request to fetch financial metrics for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            FinancialMetricsResult result = financialMetricsService.fetchAndSaveBasicFinancials(symbol);
            
            switch (result.getStatus()) {
                case SUCCESS:
                    response.put("success", true);
                    response.put("data", result.getMetrics());
                    response.put("message", String.format("Successfully fetched financial metrics for %s", symbol));
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                case SKIPPED:
                    response.put("success", true);
                    response.put("message", result.getMessage());
                    return ResponseEntity.ok(response);
                case NO_DATA:
                    response.put("success", false);
                    response.put("message", result.getMessage());
                    return ResponseEntity.ok(response);
                case ERROR:
                    response.put("success", false);
                    response.put("error", result.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                default:
                    response.put("success", false);
                    response.put("error", "Unknown status");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            log.error("Error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "S&P 500 재무 지표 일괄 수집",
        description = "S&P 500에 포함된 모든 회사의 재무 지표를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "재무 지표 일괄 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/sp500")  // S&P 500 관련 경로 수정
    public ResponseEntity<Map<String, Object>> fetchSp500FinancialMetrics(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch financial metrics for S&P 500 symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int savedCount = financialMetricsService.fetchAndSaveSp500BasicFinancials(batchSize, delayMs);
            
            response.put("success", true);
            response.put("processedCount", savedCount);
            response.put("message", String.format("Successfully processed financial metrics for %d S&P 500 symbols", savedCount));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error fetching S&P 500 financial metrics: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "S&P 500 재무 지표 조회",
        description = "오늘 날짜의 S&P 500 재무 지표를 조회합니다. 오늘 데이터가 없으면 가장 최근 날짜의 데이터를 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 재무 지표 조회 성공"),
        @ApiResponse(responseCode = "404", description = "S&P 500 재무 지표를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/sp500")
    public ResponseEntity<Map<String, Object>> getSp500FinancialMetrics() {
        log.info("Received request to get S&P 500 financial metrics");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDate today = LocalDate.now();
            List<FinancialMetrics> metricsList = financialMetricsRepository.findSp500FinancialMetricsByDate(today);
            
            // 오늘 데이터가 없으면 가장 최근 날짜의 데이터 조회
            if (metricsList.isEmpty()) {
                Optional<LocalDate> mostRecentDate = financialMetricsRepository.findMostRecentSp500MetricsDate();
                
                if (mostRecentDate.isPresent()) {
                    LocalDate recentDate = mostRecentDate.get();
                    metricsList = financialMetricsRepository.findSp500FinancialMetricsByDate(recentDate);
                    
                    response.put("success", true);
                    response.put("data", metricsList);
                    response.put("count", metricsList.size());
                    response.put("date", recentDate.toString());
                    response.put("isToday", false);
                    response.put("message", String.format("No data for today (%s). Retrieved %d S&P 500 financial metrics from %s", 
                            today, metricsList.size(), recentDate));
                    
                    log.info("No data for today. Retrieved {} S&P 500 financial metrics from {}", 
                            metricsList.size(), recentDate);
                } else {
                    response.put("success", false);
                    response.put("data", metricsList);
                    response.put("count", 0);
                    response.put("date", today.toString());
                    response.put("isToday", true);
                    response.put("message", "No S&P 500 financial metrics found in database");
                    
                    log.warn("No S&P 500 financial metrics found in database");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
            } else {
                response.put("success", true);
                response.put("data", metricsList);
                response.put("count", metricsList.size());
                response.put("date", today.toString());
                response.put("isToday", true);
                response.put("message", String.format("Successfully retrieved %d S&P 500 financial metrics for today (%s)", 
                        metricsList.size(), today));
                
                log.info("Retrieved {} S&P 500 financial metrics for today ({})", metricsList.size(), today);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 financial metrics: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 