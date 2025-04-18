package com.stocker.stocker.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stocker.stocker.domain.Stock;
import com.stocker.stocker.service.StockService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private static final Logger logger = LoggerFactory.getLogger(StockController.class);
    private final StockService stockService;
    
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }
    
    /**
     * 특정 주식 심볼에 대한 상세 정보를 가져옵니다.
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 주식 상세 정보
     */
    @GetMapping("/detail/{symbol}")
    public ResponseEntity<?> getStockDetail(@PathVariable String symbol) {
        logger.info("주식 심볼 {} 상세 정보 요청", symbol);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 대문자로 변환
            symbol = symbol.toUpperCase();
            
            // 주식 정보 조회
            Optional<Stock> stockOptional = stockService.getStockByTicker(symbol);
            
            if (stockOptional.isPresent()) {
                Stock stock = stockOptional.get();
                logger.info("주식 {} 상세 정보 조회 성공", symbol);
                
                response.put("success", true);
                response.put("data", stock);
                response.put("message", "Successfully fetched stock details for " + symbol);
                
                return ResponseEntity.ok(response);
            } else {
                logger.warn("주식 {} 정보를 찾을 수 없습니다", symbol);
                
                response.put("success", false);
                response.put("error", "Stock not found");
                response.put("message", "Could not find stock with symbol: " + symbol);
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("주식 {} 상세 정보 조회 오류: {}", symbol, e.getMessage(), e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Error fetching stock details for " + symbol);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/fetch/symbols")
    public ResponseEntity<String> fetchStockSymbols() {
        logger.info("주식 심볼 데이터 수집 시작");
        int savedCount = stockService.fetchUSStockSymbols();
        logger.info("주식 심볼 {} 개 저장 완료", savedCount);
        return ResponseEntity.ok("주식 심볼 데이터 가져오기 완료. " + savedCount + "개의 새로운 주식 데이터가 저장되었습니다.");
    }
    
    @GetMapping("/fetch/profiles")
    public ResponseEntity<String> fetchStockProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "200") int delayMs) {
        
        logger.info("모든 주식 프로필 수집 시작 (batchSize={}, delayMs={})", batchSize, delayMs);
        int updatedCount = stockService.fetchAndUpdateAllStockProfiles(batchSize, delayMs);
        logger.info("주식 프로필 {} 개 업데이트 완료", updatedCount);
        return ResponseEntity.ok("주식 상세 정보 업데이트 완료. " + updatedCount + "개의 주식 상세 정보가 업데이트되었습니다.");
    }
    
    @GetMapping("/fetch/profiles/null-country")
    public ResponseEntity<String> fetchNullCountryStockProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs) {
        
        logger.info("Country=null 주식 프로필 수집 시작 (batchSize={}, delayMs={})", batchSize, delayMs);
        int updatedCount = stockService.fetchAndUpdateNullCountryStockProfiles(batchSize, delayMs);
        logger.info("Country=null 주식 프로필 {} 개 업데이트 완료", updatedCount);
        return ResponseEntity.ok("Country가 null인 주식 상세 정보 업데이트 완료. " + updatedCount + "개의 주식 상세 정보가 업데이트되었습니다.");
    }
    
    /**
     * 특정 주식의 실시간 시세 정보를 가져와 저장하고 반환합니다.
     * @param ticker 주식 심볼 (예: AAPL, MSFT)
     * @return 주식 시세 정보
     */
    @GetMapping("/quote/{ticker}")
    public ResponseEntity<?> fetchStockQuote(@PathVariable String ticker) {
        logger.info("주식 {} 실시간 시세 정보 요청", ticker);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 대문자로 변환
            ticker = ticker.toUpperCase();
            
            // 서비스 호출하여 시세 정보 가져오기
            var stockQuote = stockService.fetchAndSaveStockQuote(ticker);
            logger.info("주식 {} 실시간 시세 정보 조회 성공: {}", ticker, stockQuote);
            
            response.put("success", true);
            response.put("data", stockQuote);
            response.put("message", "Successfully fetched quote for " + ticker);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("주식 {} 정보 조회 실패: {}", ticker, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Stock not found: " + ticker);
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("주식 {} 실시간 시세 정보 조회 오류: {}", ticker, e.getMessage(), e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Error fetching quote for " + ticker);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}