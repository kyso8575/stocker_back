package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import com.stocker_back.stocker_back.service.FinancialMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class FinancialMetricsController {

    private final FinancialMetricsService financialMetricsService;
    private final FinancialMetricsRepository financialMetricsRepository;

    /**
     * Fetch basic financial metrics for a specific stock symbol from Finnhub API
     * @param symbol Stock symbol to fetch data for (e.g., AAPL)
     * @param batchSize Number of symbols to process in a batch when fetching all (default: 20)
     * @param delayMs Delay between API calls in milliseconds to avoid rate limits (default: 500)
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
                FinancialMetricsResult result = financialMetricsService.fetchAndSaveBasicFinancials(symbol);
                
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
            int savedCount = financialMetricsService.fetchAndSaveAllBasicFinancials(batchSize, delayMs);
            
            response.put("success", true);
            response.put("processedCount", savedCount);
            response.put("message", String.format("Successfully processed financial metrics for %d symbols", savedCount));
            
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
} 