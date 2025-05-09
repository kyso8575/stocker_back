package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.service.StockSymbolService;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockSymbolController {

    private final StockSymbolService stockSymbolService;
    private final StockSymbolRepository stockSymbolRepository;
    private final FinancialMetricsRepository financialMetricsRepository;
    
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
     * 특정 주식 심볼에 대한 회사 프로필 정보를 가져오는 헬퍼 메서드
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @return 업데이트된 회사 프로필 정보와 성공 여부를 담은 응답
     */
    private ResponseEntity<Map<String, Object>> fetchCompanyProfile(String symbol) {
        
        log.info("Processing company profile for symbol: {}", symbol);
        
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
    
    /**
     * Fetch quote data from Finnhub API and save it to the database
     * If a symbol is provided, fetch quote only for that symbol
     * If no symbol is provided, fetch quotes for all symbols in the database
     * 
     * @param symbol Optional stock symbol (e.g., AAPL)
     * @param batchSize Number of symbols to process in a batch when fetching all (default: 20)
     * @param delayMs Delay between API calls in milliseconds to avoid rate limits (default: 500)
     * @return The quote data and success status
     */
    @GetMapping("/fetch/quotes")
    public ResponseEntity<Map<String, Object>> fetchQuotes(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs) {
        
        Map<String, Object> response = new HashMap<>();
        
        // If a specific symbol is provided, fetch quote for that symbol only
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Received request to fetch quote data for symbol: {}", symbol);
            response.put("symbol", symbol);
            
            try {
                Quote quote = stockSymbolService.fetchAndSaveQuote(symbol);
                
                if (quote != null) {
                    response.put("success", true);
                    response.put("data", quote);
                    response.put("message", String.format("Successfully fetched and saved quote data for %s", symbol));
                    return ResponseEntity.ok(response);
                } else {
                    response.put("success", false);
                    response.put("message", String.format("No quote data found for %s", symbol));
                    return ResponseEntity.badRequest().body(response);
                }
            } catch (Exception e) {
                log.error("Error fetching quote data for symbol {}: ", symbol, e);
                
                response.put("success", false);
                response.put("error", e.getMessage());
                
                return ResponseEntity.badRequest().body(response);
            }
        } 
        // If no symbol is provided, fetch quotes for all symbols
        else {
            log.info("Received request to fetch quotes for all symbols with batchSize={}, delayMs={}", 
                    batchSize, delayMs);
            
            try {
                int successCount = stockSymbolService.fetchAndSaveAllQuotes(batchSize, delayMs);
                
                response.put("success", true);
                response.put("savedCount", successCount);
                response.put("message", String.format("Successfully fetched and saved quotes for %d symbols", 
                        successCount));
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Error fetching quotes for all symbols: ", e);
                
                response.put("success", false);
                response.put("error", e.getMessage());
                
                return ResponseEntity.badRequest().body(response);
            }
        }
    }

    /**
     * Fetch company news from Finnhub API for a specific stock symbol
     * 
     * @param symbol Stock symbol (e.g., AAPL)
     * @param from Start date in format YYYY-MM-DD
     * @param to End date in format YYYY-MM-DD
     * @param count Optional limit on number of news items to return
     * @return Company news data and success status
     */
    @GetMapping("/info/company_news")
    public ResponseEntity<Map<String, Object>> getCompanyNews(
            @RequestParam String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get company news for symbol: {}, from: {}, to: {}, count: {}", 
                symbol, from, to, count);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = stockSymbolService.fetchCompanyNews(symbol, from, to, count);
            
            response.put("success", true);
            response.put("data", newsItems);
            
            if (newsItems.isEmpty()) {
                response.put("message", String.format("No news found for %s in date range %s to %s", 
                        symbol, from, to));
            } else {
                response.put("message", String.format("Successfully fetched %d news items for %s", 
                        newsItems.size(), symbol));
                response.put("count", newsItems.size());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting company news: ", e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Fetch general market news from Finnhub API
     * 
     * @param from Start date in format YYYY-MM-DD
     * @param to End date in format YYYY-MM-DD
     * @param count Optional limit on number of news items to return
     * @return Market news data and success status
     */
    @GetMapping("/info/market_news")
    public ResponseEntity<Map<String, Object>> getMarketNews(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get market news from: {}, to: {}, count: {}", 
                from, to, count);
        
        Map<String, Object> response = new HashMap<>();
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = stockSymbolService.fetchMarketNews(from, to, count);
            
            response.put("success", true);
            response.put("data", newsItems);
            
            if (newsItems.isEmpty()) {
                response.put("message", String.format("No market news found in date range %s to %s", 
                        from, to));
            } else {
                response.put("message", String.format("Successfully fetched %d market news items", 
                        newsItems.size()));
                response.put("count", newsItems.size());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting market news: ", e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Fetch basic financial metrics for a specific stock symbol from Finnhub API
     * @param symbol Stock symbol to fetch data for (e.g., AAPL)
     * @return Financial metrics data and success status
     */
    @GetMapping("/fetch/basic_financials")
    public ResponseEntity<Map<String, Object>> fetchBasicFinancials(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs) {
        
        Map<String, Object> response = new HashMap<>();
        
        // If a specific symbol is provided, fetch financial metrics for that symbol only
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Received request to fetch financial metrics for symbol: {}", symbol);
            response.put("symbol", symbol);
            
            try {
                FinancialMetricsResult result = stockSymbolService.fetchAndSaveBasicFinancials(symbol);
                
                switch (result.getStatus()) {
                    case SUCCESS:
                        response.put("success", true);
                        response.put("data", result.getMetrics());
                        response.put("message", String.format("Successfully fetched financial metrics for %s", symbol));
                        break;
                    case SKIPPED:
                        response.put("success", true);
                        response.put("message", result.getMessage());
                        break;
                    case NO_DATA:
                        response.put("success", false);
                        response.put("message", result.getMessage());
                        break;
                    case ERROR:
                        response.put("success", false);
                        response.put("error", result.getMessage());
                        break;
                }
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
                response.put("success", false);
                response.put("error", e.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
        }
        
        // If no symbol is specified, fetch financial metrics for all symbols with valid company profiles
        log.info("Received request to fetch financial metrics for all symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        try {
            int processedCount = stockSymbolService.fetchAndSaveAllBasicFinancials(batchSize, delayMs);
            
            response.put("success", true);
            response.put("processedCount", processedCount);
            response.put("message", String.format("Successfully processed financial metrics for %d symbols", processedCount));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching financial metrics: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get the most recent financial metrics for a specific symbol from the database
     * @param symbol Stock symbol to get data for (e.g., AAPL)
     * @return Financial metrics data and success status
     */
    @GetMapping("/info/basic_financials")
    public ResponseEntity<Map<String, Object>> getBasicFinancials(@RequestParam String symbol) {
        log.info("Received request to get financial metrics for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            Optional<FinancialMetrics> metricsOpt = financialMetricsRepository.findTopBySymbolOrderByCreatedAtDesc(symbol);
            
            if (metricsOpt.isPresent()) {
                response.put("success", true);
                response.put("data", metricsOpt.get());
                response.put("message", String.format("Successfully retrieved financial metrics for %s", symbol));
            } else {
                response.put("success", false);
                response.put("message", String.format("No financial metrics found for %s", symbol));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving financial metrics for symbol {}: {}", symbol, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 데이터베이스에서 특정 주식 심볼에 대한 회사 프로필 정보를 조회합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 회사 프로필 정보와 성공 여부를 담은 응답
     */
    @GetMapping("/info/company_profile")
    public ResponseEntity<Map<String, Object>> getCompanyProfile(@RequestParam String symbol) {
        log.info("Received request to get company profile for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            // 데이터베이스에서 심볼 정보 조회
            Optional<StockSymbol> stockSymbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
            
            if (stockSymbolOpt.isPresent()) {
                StockSymbol stockSymbol = stockSymbolOpt.get();
                
                // 프로필 정보가 없는 경우 확인
                if (Boolean.TRUE.equals(stockSymbol.getProfileEmpty())) {
                    response.put("success", false);
                    response.put("message", String.format("No profile information available for %s", symbol));
                    return ResponseEntity.ok(response);
                }
                
                response.put("success", true);
                response.put("data", stockSymbol);
                response.put("message", String.format("Successfully retrieved company profile for %s", symbol));
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", String.format("Symbol %s not found in database", symbol));
                
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving company profile for symbol {}: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 