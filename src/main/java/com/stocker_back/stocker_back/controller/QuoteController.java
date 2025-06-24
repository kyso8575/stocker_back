package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/quote")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Quote", description = "주식 시세 데이터 API (관리자 전용)")
public class QuoteController {

    private final QuoteService quoteService;

    @Operation(
        summary = "S&P 500 시세 일괄 수집",
        description = "S&P 500에 포함된 모든 주식의 시세 데이터를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "S&P 500 시세 일괄 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/sp500")
    public ResponseEntity<Map<String, Object>> fetchSp500Quotes(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch quotes for S&P 500 symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int savedCount = quoteService.fetchAndSaveSp500Quotes(batchSize, delayMs);
            
            response.put("success", true);
            response.put("processedCount", savedCount);
            response.put("batchSize", batchSize);
            response.put("delayMs", delayMs);
            response.put("estimatedTime", String.format("%.1f minutes", (savedCount / 60.0)));
            response.put("message", String.format("Successfully processed quotes for %d S&P 500 symbols (batch size: %d)", savedCount, batchSize));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error fetching S&P 500 quotes: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "단일 주식 시세 수집",
        description = "특정 주식 심볼의 시세 데이터를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "주식 시세 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/symbol/{symbol}")
    public ResponseEntity<Map<String, Object>> fetchQuote(@PathVariable String symbol) {
        log.info("Received request to fetch quote for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            Quote savedQuote = quoteService.fetchAndSaveQuote(symbol);
            
            if (savedQuote != null) {
                response.put("success", true);
                response.put("data", savedQuote);
                response.put("message", String.format("Successfully fetched quote for %s", symbol));
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                response.put("success", false);
                response.put("message", String.format("No quote data available for %s", symbol));
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("Error fetching quote for symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 