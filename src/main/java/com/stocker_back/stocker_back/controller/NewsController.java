package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.service.NewsService;
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

@RestController
@RequestMapping("/api/stocks/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;

    /**
     * Fetch company news from Finnhub API for a specific stock symbol
     * 
     * @param symbol Stock symbol (e.g., AAPL)
     * @param from Start date in format YYYY-MM-DD
     * @param to End date in format YYYY-MM-DD
     * @param countStr Optional limit on number of news items to return
     * @return Company news data and success status
     */
    @GetMapping("/company")
    public ResponseEntity<Map<String, Object>> getCompanyNews(
            @RequestParam String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String countStr) {
        
        log.info("Received request to get company news for symbol: {}, from: {}, to: {}, count: {}", 
                symbol, from, to, countStr);
        
        // Validate and parse the count parameter
        Integer count = null;
        if (countStr != null && !countStr.isEmpty()) {
            try {
                count = Integer.parseInt(countStr);
                if (count <= 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Parameter 'count' must be a positive integer");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            } catch (NumberFormatException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Parameter 'count' must be a valid integer, received: '" + countStr + "'");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchCompanyNews(symbol, from, to, count);
            
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
     * @param countStr Optional limit on number of news items to return
     * @return Market news data and success status
     */
    @GetMapping("/market")
    public ResponseEntity<Map<String, Object>> getMarketNews(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String countStr) {
        
        log.info("Received request to get market news from: {}, to: {}, count: {}", 
                from, to, countStr);
        
        // Validate and parse the count parameter
        Integer count = null;
        if (countStr != null && !countStr.isEmpty()) {
            try {
                count = Integer.parseInt(countStr);
                if (count <= 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Parameter 'count' must be a positive integer");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            } catch (NumberFormatException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Parameter 'count' must be a valid integer, received: '" + countStr + "'");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchMarketNews(from, to, count);
            
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
} 