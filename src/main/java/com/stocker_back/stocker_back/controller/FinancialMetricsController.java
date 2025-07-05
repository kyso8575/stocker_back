package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/financial-metrics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Financial Metrics", description = "재무 지표 API (일반 사용자용)")
public class FinancialMetricsController {

    private final FinancialMetricsService financialMetricsService;

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
    public ResponseEntity<?> getFinancialMetrics(@PathVariable String symbol) {
        log.info("Received request to get financial metrics for symbol: {}", symbol);
        
        try {
            Optional<FinancialMetrics> metricsOpt = financialMetricsService.getLatestFinancialMetrics(symbol);
            
            if (metricsOpt.isPresent()) {
                return ResponseEntity.ok(AuthResponseDto.success(
                    ResponseMessages.SUCCESS,
                    Map.of(
                        "symbol", symbol,
                        "data", metricsOpt.get()
                    )
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    String.format("No financial metrics found for %s", symbol)
                ));
            }
        } catch (Exception e) {
            log.error("Error retrieving financial metrics for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    public ResponseEntity<?> getFinancialMetricsHistory(
            @PathVariable String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        log.info("Received request to get financial metrics history for symbol: {} (from: {}, to: {})", 
                symbol, from, to);
        
        try {
            List<FinancialMetrics> metricsHistory = financialMetricsService.getFinancialMetricsHistory(symbol, from, to);
            
            if (!metricsHistory.isEmpty()) {
                Map<String, Object> data = Map.of(
                    "symbol", symbol,
                    "data", metricsHistory,
                    "count", metricsHistory.size()
                );
                
                String message = ((from != null && !from.isEmpty()) || (to != null && !to.isEmpty())) ?
                    String.format("%d records found for %s in range", metricsHistory.size(), symbol) :
                    String.format("%d records found for %s", metricsHistory.size(), symbol);
                
                return ResponseEntity.ok(AuthResponseDto.success(message, data));
            } else {
                String message = ((from != null && !from.isEmpty()) || (to != null && !to.isEmpty())) ?
                    String.format("No records found for %s in range", symbol) :
                    String.format("No records found for %s", symbol);
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(message));
            }
        } catch (Exception e) {
            log.error("Error retrieving financial metrics history for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    public ResponseEntity<?> fetchAllFinancialMetrics(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch financial metrics for all symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        try {
            int savedCount = financialMetricsService.fetchAndSaveAllBasicFinancials(batchSize, delayMs);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                String.format("%d financial metrics processed", savedCount),
                Map.of("processedCount", savedCount)
            ));
        } catch (Exception e) {
            log.error("Error fetching financial metrics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @PostMapping("/admin/symbol/{symbol}")
    public ResponseEntity<?> fetchFinancialMetrics(@PathVariable String symbol) {
        log.info("Received request to fetch financial metrics for symbol: {}", symbol);
        
        try {
            FinancialMetricsResult result = financialMetricsService.fetchAndSaveBasicFinancials(symbol);
            
            switch (result.getStatus()) {
                case SUCCESS:
                    return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                        String.format("Financial metrics fetched for %s", symbol),
                        Map.of(
                            "symbol", symbol,
                            "data", result.getMetrics()
                        )
                    ));
                case SKIPPED:
                    return ResponseEntity.ok(AuthResponseDto.success(result.getMessage()));
                case NO_DATA:
                    return ResponseEntity.ok(AuthResponseDto.error(result.getMessage()));
                case ERROR:
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(AuthResponseDto.error(result.getMessage()));
                default:
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(AuthResponseDto.error("Unknown status"));
            }
        } catch (Exception e) {
            log.error("Error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @PostMapping("/admin/sp500")
    public ResponseEntity<?> fetchSp500FinancialMetrics(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch financial metrics for S&P 500 symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        try {
            int savedCount = financialMetricsService.fetchAndSaveSp500BasicFinancials(batchSize, delayMs);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                String.format("%d financial metrics processed", savedCount),
                Map.of("processedCount", savedCount)
            ));
        } catch (Exception e) {
            log.error("Error fetching S&P 500 financial metrics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    public ResponseEntity<?> getSp500FinancialMetrics() {
        log.info("Received request to get S&P 500 financial metrics");
        
        try {
            Map<String, Object> result = financialMetricsService.getSp500FinancialMetrics();
            
            @SuppressWarnings("unchecked")
            List<FinancialMetrics> metricsList = (List<FinancialMetrics>) result.get("data");
            int count = (Integer) result.get("count");
            String date = (String) result.get("date");
            boolean isToday = (Boolean) result.get("isToday");
            
            if (count > 0) {
                String message = isToday ?
                    String.format("%d records found for S&P 500 on %s", count, date) :
                    String.format("%d records found for S&P 500 as of %s", count, date);
                
                return ResponseEntity.ok(AuthResponseDto.success(message, result));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    String.format("No records found for S&P 500 on %s", date)
                ));
            }
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 financial metrics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 