package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.StockSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    
    /**
     * Finnhub API에서 주식 심볼 데이터를 가져와 데이터베이스에 저장
     * @param exchange 거래소 코드 (기본값: US)
     * @param symbol 특정 심볼 코드. 입력 시 해당 심볼만 저장, 미입력 시 모든 심볼 저장
     * @return 저장된 심볼 개수와 성공 여부를 담은 응답
     */
    @GetMapping("/fetch/symbols")
    public ResponseEntity<Map<String, Object>> fetchStockSymbols(
            @RequestParam(defaultValue = "US") String exchange,
            @RequestParam(required = false) String symbol) {
        
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Received request to fetch single stock symbol: {} for exchange: {}", symbol, exchange);
        } else {
            log.info("Received request to fetch all stock symbols for exchange: {}", exchange);
        }
        
        try {
            int savedCount = stockSymbolService.fetchAndSaveStockSymbols(exchange, symbol);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("exchange", exchange);
            
            if (symbol != null && !symbol.isEmpty()) {
                response.put("symbol", symbol);
                response.put("savedCount", savedCount);
                response.put("message", String.format("Symbol %s %s for exchange %s", 
                        symbol, 
                        savedCount > 0 ? "successfully saved" : "not found", 
                        exchange));
            } else {
                response.put("savedCount", savedCount);
                response.put("message", String.format("Successfully saved %d stock symbols for exchange %s", 
                        savedCount, exchange));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching stock symbols: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("exchange", exchange);
            if (symbol != null && !symbol.isEmpty()) {
                response.put("symbol", symbol);
            }
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * S&P 500에 포함된 모든 심볼을 반환합니다.
     */
    @GetMapping("/symbols/sp500")
    public ResponseEntity<Map<String, Object>> getSp500Symbols() {
        log.info("Received request to get all S&P 500 symbols");
        Map<String, Object> response = new HashMap<>();
        try {
            Set<String> sp500Symbols = stockSymbolService.findAllSp500Symbols();
            response.put("success", true);
            response.put("count", sp500Symbols.size());
            response.put("symbols", sp500Symbols);
            response.put("message", String.format("Found %d S&P 500 symbols", sp500Symbols.size()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting S&P 500 symbols: ", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
} 