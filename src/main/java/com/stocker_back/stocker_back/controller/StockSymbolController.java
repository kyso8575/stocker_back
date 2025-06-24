package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.service.StockSymbolService;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/symbols")  // stocks 제거
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Symbol", description = "주식 심볼 API (GET: 일반 사용자, POST: 관리자 전용)")
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    private final StockSymbolRepository stockSymbolRepository;
    
    @Operation(
        summary = "단일 주식 심볼 추가",
        description = "새로운 주식 심볼을 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "주식 심볼 추가 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/symbol/{symbol}")  // 관리자용 엔드포인트 경로 수정
    public ResponseEntity<Map<String, Object>> addStockSymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US") String exchange) {
        log.info("Received request to add stock symbol: {} for exchange: {}", symbol, exchange);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol.toUpperCase());
        response.put("exchange", exchange);
        
        try {
            // 먼저 이미 존재하는지 확인
            boolean symbolExists = stockSymbolRepository.existsBySymbol(symbol.toUpperCase());
            
            if (symbolExists) {
                response.put("success", true);
                response.put("savedCount", 0);
                response.put("message", String.format("Symbol %s already exists in database", symbol.toUpperCase()));
                return ResponseEntity.ok(response);
            }
            
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, symbol.toUpperCase());
            
            response.put("success", true);
            response.put("savedCount", savedCount);
            
            if (savedCount > 0) {
                response.put("message", String.format("Symbol %s successfully saved for exchange %s", symbol.toUpperCase(), exchange));
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                response.put("message", String.format("Symbol %s not found in exchange %s", symbol.toUpperCase(), exchange));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error adding stock symbol {} for exchange {}: {}", symbol, exchange, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "주식 심볼 일괄 추가",
        description = "여러 주식 심볼을 Finnhub API에서 가져와 데이터베이스에 일괄 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "주식 심볼 일괄 추가 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/batch")  // 관리자용 엔드포인트 경로 수정
    public ResponseEntity<Map<String, Object>> addStockSymbolsBatch(
            @RequestParam(defaultValue = "US") String exchange,
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        log.info("Received request to add stock symbols in batch for exchange: {} with batchSize={}, delayMs={}", 
                exchange, batchSize, delayMs);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exchange", exchange);
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, null);
            
            response.put("success", true);
            response.put("savedCount", savedCount);
            response.put("message", String.format("Successfully saved %d stock symbols for exchange %s", savedCount, exchange));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error adding stock symbols in batch for exchange {}: {}", exchange, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "단일 주식 심볼 조회",
        description = "특정 주식 심볼의 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "주식 심볼 조회 성공"),
        @ApiResponse(responseCode = "404", description = "주식 심볼을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{symbol}")  // 일반용 엔드포인트는 그대로 유지
    public ResponseEntity<Map<String, Object>> getStockSymbol(@PathVariable String symbol) {
        log.info("Received request to get stock symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            Optional<StockSymbol> symbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
            
            if (symbolOpt.isPresent()) {
                response.put("success", true);
                response.put("data", symbolOpt.get());
                response.put("message", String.format("Successfully retrieved stock symbol %s", symbol));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", String.format("No stock symbol found for %s", symbol));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving stock symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 