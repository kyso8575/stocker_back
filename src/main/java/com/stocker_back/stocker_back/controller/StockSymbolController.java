package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.StockSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/symbols")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Symbol", description = "주식 심볼 API (GET: 일반 사용자, POST: 관리자 전용)")
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    
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
    @PostMapping("/admin/symbol/{symbol}")
    public ResponseEntity<?> addStockSymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US") String exchange) {
        log.info("Received request to add stock symbol: {} for exchange: {}", symbol, exchange);
        
        try {
            String upperSymbol = symbol.toUpperCase();
            
            // 먼저 이미 존재하는지 확인
            if (stockSymbolService.symbolExists(upperSymbol)) {
                Map<String, Object> data = Map.of(
                    "symbol", upperSymbol,
                    "exchange", exchange,
                    "savedCount", 0
                );
                
                return ResponseEntity.ok(AuthResponseDto.success(
                    ResponseMessages.format("Symbol %s already exists", upperSymbol),
                    data
                ));
            }
            
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, upperSymbol);
            
            Map<String, Object> data = Map.of(
                "symbol", upperSymbol,
                "exchange", exchange,
                "savedCount", savedCount
            );
            
            if (savedCount > 0) {
                return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                    ResponseMessages.format("Symbol %s added for exchange %s", upperSymbol, exchange),
                    data
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    ResponseMessages.format("Symbol %s not found for exchange %s", upperSymbol, exchange)
                ));
            }
        } catch (Exception e) {
            log.error("Error adding stock symbol {} for exchange {}: {}", symbol, exchange, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @PostMapping("/admin/batch")
    public ResponseEntity<?> addStockSymbolsBatch(
            @RequestParam(defaultValue = "US") String exchange,
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        log.info("Received request to add stock symbols in batch for exchange: {} with batchSize={}, delayMs={}", 
                exchange, batchSize, delayMs);
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, null);
            
            Map<String, Object> data = Map.of(
                "exchange", exchange,
                "savedCount", savedCount,
                "batchSize", batchSize,
                "delayMs", delayMs
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_PROCESSED_ITEMS, savedCount),
                data
            ));
        } catch (Exception e) {
            log.error("Error adding stock symbols in batch for exchange {}: {}", exchange, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @GetMapping("/{symbol}")
    public ResponseEntity<?> getStockSymbol(@PathVariable String symbol) {
        log.info("Received request to get stock symbol: {}", symbol);
        
        try {
            Optional<StockSymbol> symbolOpt = stockSymbolService.findBySymbol(symbol);
            
            if (symbolOpt.isPresent()) {
                Map<String, Object> data = Map.of(
                    "symbol", symbol,
                    "data", symbolOpt.get()
                );
                
                return ResponseEntity.ok(AuthResponseDto.success(
                    ResponseMessages.format("Symbol %s retrieved successfully", symbol),
                    data
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    ResponseMessages.format("Symbol %s not found in database", symbol)
                ));
            }
        } catch (Exception e) {
            log.error("Error retrieving stock symbol {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 