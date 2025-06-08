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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stocks/financial-metrics")
@RequiredArgsConstructor
@Slf4j
public class FinancialMetricsController {

    private final FinancialMetricsService financialMetricsService;
    private final FinancialMetricsRepository financialMetricsRepository;

    /**
     * 모든 주식 심볼에 대한 기본 재무 지표를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 500)
     * @return 처리된 재무 지표 수와 성공 여부를 담은 응답
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> fetchAllFinancialMetrics(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs) {
        
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

    /**
     * 특정 주식 심볼에 대한 기본 재무 지표를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @return 재무 지표 데이터와 성공 여부를 담은 응답
     */
    @PostMapping("/{symbol}")
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
    
    /**
     * 데이터베이스에서 특정 주식 심볼에 대한 최신 재무 지표를 조회합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 재무 지표 데이터와 성공 여부를 담은 응답
     */
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

    /**
     * 특정 심볼의 재무 지표 기록을 조회합니다. (선택적 날짜 범위 필터링)
     * @param symbol 주식 심볼 (예: AAPL)
     * @param from 시작 날짜 (선택사항, 형식: YYYY-MM-DD 또는 YYYY-MM-DDTHH:mm:ss)
     * @param to 종료 날짜 (선택사항, 형식: YYYY-MM-DD 또는 YYYY-MM-DDTHH:mm:ss)
     * @return 해당 심볼의 재무 지표 기록 (날짜 범위 내)
     */
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
} 