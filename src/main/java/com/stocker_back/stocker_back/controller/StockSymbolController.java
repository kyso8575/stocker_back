package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.StockSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks/symbols")
@RequiredArgsConstructor
@Slf4j
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    
    /**
     * Finnhub API에서 모든 주식 심볼 데이터를 배치로 가져와 데이터베이스에 저장합니다.
     * @param exchange 거래소 코드 (기본값: US)
     * @return 저장된 심볼 개수와 성공 여부를 담은 응답
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> fetchAllStockSymbols(
            @RequestParam(defaultValue = "US") String exchange) {
        
        log.info("Received request to fetch all stock symbols for exchange: {}", exchange);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exchange", exchange);
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, null);
            
            response.put("success", true);
            response.put("savedCount", savedCount);
            response.put("message", String.format("Successfully saved %d stock symbols for exchange %s", savedCount, exchange));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error fetching all stock symbols for exchange {}: {}", exchange, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Finnhub API에서 특정 주식 심볼 데이터를 가져와 데이터베이스에 저장합니다.
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @param exchange 거래소 코드 (기본값: US)
     * @return 저장된 심볼 정보와 성공 여부를 담은 응답
     */
    @PostMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> fetchStockSymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "US") String exchange) {
        
        log.info("Received request to fetch stock symbol: {} for exchange: {}", symbol, exchange);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("exchange", exchange);
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, symbol);
            
            response.put("success", true);
            response.put("savedCount", savedCount);
            
            if (savedCount > 0) {
                response.put("message", String.format("Symbol %s successfully saved for exchange %s", symbol, exchange));
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                response.put("message", String.format("Symbol %s not found for exchange %s", symbol, exchange));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error fetching stock symbol {} for exchange {}: {}", symbol, exchange, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 