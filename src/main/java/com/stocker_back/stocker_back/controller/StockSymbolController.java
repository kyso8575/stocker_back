package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.StockSymbolService;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final StockSymbolRepository stockSymbolRepository;
    
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
     * Finnhub API에서 모든 주식 심볼에 대한 회사 프로필 정보를 가져와 데이터베이스에 저장
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 500)
     * @param symbol 선택적으로 특정 심볼을 지정하면 해당 심볼만 처리
     * @return 업데이트된 회사 프로필 수와 성공 여부를 담은 응답
     */
    @GetMapping("/fetch/company_profiles")
    public ResponseEntity<Map<String, Object>> fetchCompanyProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs,
            @RequestParam(required = false) String symbol) {
        
        // 단일 심볼이 지정된 경우 fetchCompanyProfile 메소드 호출
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Received request to fetch company profile for symbol: {} via company_profiles endpoint", symbol);
            return fetchCompanyProfile(symbol);
        }
        
        log.info("Received request to fetch all company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        try {
            int updatedCount = stockSymbolService.fetchAndSaveAllCompanyProfiles(batchSize, delayMs);
            
            // 빈 프로필과 유효한 프로필 개수 조회
            long validProfilesCount = stockSymbolRepository.countByProfileEmptyFalse();
            long emptyProfilesCount = stockSymbolRepository.countByProfileEmptyTrue();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalProcessed", updatedCount);
            response.put("validProfiles", validProfilesCount);
            response.put("emptyProfiles", emptyProfilesCount);
            response.put("message", String.format("Successfully processed %d company profiles (valid: %d, empty: %d)", 
                    updatedCount, validProfilesCount, emptyProfilesCount));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching company profiles: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Finnhub API에서 특정 주식 심볼에 대한 회사 프로필 정보를 가져와 데이터베이스에 저장
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @return 업데이트된 회사 프로필 정보와 성공 여부를 담은 응답
     */
    @GetMapping("/fetch/company_profile/{symbol}")
    public ResponseEntity<Map<String, Object>> fetchCompanyProfile(@PathVariable String symbol) {
        
        log.info("Received request to fetch company profile for symbol: {}", symbol);
        
        try {
            var updatedSymbol = stockSymbolService.fetchAndSaveSingleCompanyProfile(symbol);
            
            Map<String, Object> response = new HashMap<>();
            
            if (updatedSymbol != null) {
                response.put("success", true);
                response.put("data", updatedSymbol);
                
                // 빈 프로필인지 여부에 따라 다른 메시지 설정
                if (Boolean.FALSE.equals(updatedSymbol.getProfileEmpty())) {
                    response.put("message", String.format("Successfully updated company profile for %s", symbol));
                } else {
                    response.put("message", String.format("No profile data found for %s, marked as empty", symbol));
                    response.put("isEmpty", true);
                }
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("symbol", symbol);
                response.put("message", String.format("Symbol %s not found in database", symbol));
                
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error fetching company profile for symbol {}: ", symbol, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("symbol", symbol);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 