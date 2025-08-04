package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.dto.ApiResponse;
import com.stocker_back.stocker_back.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "S&P 500 시세 일괄 수집 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/sp500")
    public ResponseEntity<?> fetchSp500Quotes(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch quotes for S&P 500 symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        try {
            int savedCount = quoteService.fetchAndSaveSp500Quotes(batchSize, delayMs);
            
            return ResponseEntity.ok(ApiResponse.success(Map.of("savedCount", savedCount)));
        } catch (Exception e) {
            log.error("Error fetching S&P 500 quotes: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }

    @Operation(
        summary = "단일 주식 시세 수집",
        description = "특정 주식 심볼의 시세 데이터를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "주식 시세 수집 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/symbol/{symbol}")
    public ResponseEntity<?> fetchQuote(@PathVariable String symbol) {
        log.info("Received request to fetch quote for symbol: {}", symbol);
        
        try {
            Quote savedQuote = quoteService.fetchAndSaveQuote(symbol);
            
            if (savedQuote != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    String.format("Quote fetched for %s", symbol),
                    Map.of(
                        "symbol", symbol,
                        "data", savedQuote
                    )
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("요청한 데이터를 찾을 수 없습니다"));
            }
        } catch (Exception e) {
            log.error("Error fetching quote for symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
} 