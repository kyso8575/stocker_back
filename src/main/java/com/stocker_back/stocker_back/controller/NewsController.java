package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;

    /**
     * 특정 주식 심볼의 회사 뉴스를 조회합니다.
     * 
     * @param symbol 주식 심볼 (예: AAPL)
     * @param from 시작 날짜 (YYYY-MM-DD 형식)
     * @param to 종료 날짜 (YYYY-MM-DD 형식)
     * @param count 반환할 뉴스 항목 수 제한 (선택사항)
     * @return 회사 뉴스 데이터와 성공 여부
     */
    @GetMapping("/companies/{symbol}")
    public ResponseEntity<Map<String, Object>> getCompanyNews(
            @PathVariable String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get company news for symbol: {}, from: {}, to: {}, count: {}", 
                symbol, from, to, count);
        
        // count 파라미터 검증
        if (count != null && count <= 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Parameter 'count' must be a positive integer");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchCompanyNews(symbol, from, to, count);
            
            response.put("success", true);
            response.put("data", newsItems);
            response.put("count", newsItems.size());
            
            if (newsItems.isEmpty()) {
                response.put("message", String.format("No news found for %s in date range %s to %s", 
                        symbol, from, to));
                return ResponseEntity.ok(response);
            } else {
                response.put("message", String.format("Successfully fetched %d news items for %s", 
                        newsItems.size(), symbol));
            return ResponseEntity.ok(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for company news request: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error getting company news for symbol {}: ", symbol, e);
            response.put("success", false);
            response.put("error", "Failed to fetch company news: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 전체 시장 뉴스를 조회합니다.
     * 
     * @param from 시작 날짜 (YYYY-MM-DD 형식)
     * @param to 종료 날짜 (YYYY-MM-DD 형식)
     * @param count 반환할 뉴스 항목 수 제한 (선택사항)
     * @return 시장 뉴스 데이터와 성공 여부
     */
    @GetMapping("/market")
    public ResponseEntity<Map<String, Object>> getMarketNews(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get market news from: {}, to: {}, count: {}", 
                from, to, count);
        
        // count 파라미터 검증
        if (count != null && count <= 0) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Parameter 'count' must be a positive integer");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("from", from);
        response.put("to", to);
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchMarketNews(from, to, count);
            
            response.put("success", true);
            response.put("data", newsItems);
            response.put("count", newsItems.size());
            
            if (newsItems.isEmpty()) {
                response.put("message", String.format("No market news found in date range %s to %s", 
                        from, to));
                return ResponseEntity.ok(response);
            } else {
                response.put("message", String.format("Successfully fetched %d market news items", 
                        newsItems.size()));
            return ResponseEntity.ok(response);
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for market news request: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error getting market news: ", e);
            response.put("success", false);
            response.put("error", "Failed to fetch market news: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 