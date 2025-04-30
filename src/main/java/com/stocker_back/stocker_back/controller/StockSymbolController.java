package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.StockSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    
    /**
     * Finnhub API에서 주식 심볼 데이터를 가져와 데이터베이스에 저장
     * @param exchange 거래소 코드 (기본값: US)
     * @return 저장된 심볼 개수와 성공 여부를 담은 응답
     */
    @GetMapping("/fetch/symbols")
    public ResponseEntity<Map<String, Object>> fetchStockSymbols(
            @RequestParam(defaultValue = "US") String exchange) {
        
        log.info("Received request to fetch stock symbols for exchange: {}", exchange);
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("exchange", exchange);
            response.put("savedCount", savedCount);
            response.put("message", String.format("Successfully saved %d stock symbols for exchange %s", 
                    savedCount, exchange));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching stock symbols: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("exchange", exchange);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 