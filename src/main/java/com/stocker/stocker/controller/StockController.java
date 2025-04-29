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
import com.stocker.stocker.domain.StockQuote;
import java.util.List;
import com.stocker.stocker.domain.NewsItem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import com.stocker.stocker.dto.BasicFinancialsDto;

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
                response.put("message", "Could not find stock with symbol: " + symbol);
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("주식 {} 상세 정보 조회 오류: {}", symbol, e.getMessage(), e);
            
            response.put("success", false);
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
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 주식 시세 정보
     */
    @GetMapping("fetch/quotes/{symbol}")
    public ResponseEntity<?> fetchStockQuote(@PathVariable String symbol) {
        logger.info("주식 {} 실시간 시세 정보 요청", symbol);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 대문자로 변환
            symbol = symbol.toUpperCase();
            
            // 서비스 호출하여 시세 정보 가져오기
            var stockQuote = stockService.fetchAndSaveStockQuote(symbol);
            logger.info("주식 {} 실시간 시세 정보 조회 성공: {}", symbol, stockQuote);
            
            response.put("success", true);
            response.put("data", stockQuote);
            response.put("message", "Successfully fetched quote for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("주식 {} 정보 조회 실패: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("message", "Stock not found: " + symbol);
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("주식 {} 실시간 시세 정보 조회 오류: {}", symbol, e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error fetching quote for " + symbol);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 기존 엔드포인트의 호환성을 위해 유지 (리디렉션)
     */
    @GetMapping("quote/{ticker}")
    public ResponseEntity<?> fetchStockQuoteOld(@PathVariable String ticker) {
        return fetchStockQuote(ticker);
    }
    
    /**
     * 모든 주식의 실시간 시세 정보를 가져와 저장하고 반환합니다.
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 300)
     * @return 모든 주식 시세 정보 목록
     */
    @GetMapping("fetch/quotes/all")
    public ResponseEntity<?> fetchAllStockQuotes(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "300") int delayMs) {
        
        logger.info("모든 주식 실시간 시세 정보 요청 (batchSize={}, delayMs={})", batchSize, delayMs);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 서비스 호출하여 모든 주식 시세 정보 가져오기
            List<StockQuote> quotes = stockService.fetchAndSaveAllStockQuotes(batchSize, delayMs);
            logger.info("{}개 주식 실시간 시세 정보 조회 성공", quotes.size());
            
            response.put("success", true);
            response.put("data", quotes);
            response.put("totalCount", quotes.size());
            response.put("message", "Successfully fetched quotes for " + quotes.size() + " stocks");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("모든 주식 실시간 시세 정보 조회 오류: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error fetching quotes for all stocks");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 특정 주식 심볼에 대한 상세 프로필 정보를 Finnhub API에서 가져와 저장하고 반환합니다.
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 업데이트된 주식 상세 정보
     */
    @GetMapping("/fetch/profile/{symbol}")
    public ResponseEntity<?> fetchAndSaveStockProfile(@PathVariable String symbol) {
        logger.info("주식 {} 상세 프로필 정보 업데이트 요청", symbol);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 대문자로 변환
            symbol = symbol.toUpperCase();
            
            // 서비스 호출하여 상세 프로필 정보 가져오기
            Stock updatedStock = stockService.fetchAndSaveStockProfile(symbol);
            logger.info("주식 {} 상세 프로필 정보 업데이트 성공", symbol);
            
            response.put("success", true);
            response.put("data", updatedStock);
            response.put("message", "Successfully updated profile for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("주식 {} 정보 업데이트 실패: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("message", "Stock not found: " + symbol);
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("주식 {} 상세 프로필 정보 업데이트 오류: {}", symbol, e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error updating profile for " + symbol);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 특정 기간 동안의 주식 관련 뉴스를 가져옵니다.
     * @param symbol 주식 심볼 (선택 사항, 특정 회사의 뉴스만 가져오려면 제공)
     * @param from 시작 날짜 (yyyy-MM-dd 형식, 기본값: 오늘)
     * @param to 종료 날짜 (yyyy-MM-dd 형식, 기본값: 오늘)
     * @param count 가져올 뉴스 항목 수 (기본값: 10)
     * @return 뉴스 항목 목록
     */
    @GetMapping("/news")
    public ResponseEntity<?> getStockNews(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "10") int count) {
        
        logger.info("주식 뉴스 요청 - symbol: {}, from: {}, to: {}, count: {}", 
                    symbol, from, to, count);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 날짜 파라미터 처리 (제공되지 않은 경우 오늘 날짜 사용)
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            if (from != null && !from.isEmpty()) {
                try {
                    fromDate = LocalDate.parse(from, formatter);
                } catch (DateTimeParseException e) {
                    logger.warn("잘못된 from 날짜 형식: {}", from);
                    response.put("success", false);
                    response.put("message", "Invalid date format for 'from'. Expected format: yyyy-MM-dd");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
            if (to != null && !to.isEmpty()) {
                try {
                    toDate = LocalDate.parse(to, formatter);
                } catch (DateTimeParseException e) {
                    logger.warn("잘못된 to 날짜 형식: {}", to);
                    response.put("success", false);
                    response.put("message", "Invalid date format for 'to'. Expected format: yyyy-MM-dd");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
            // 날짜 유효성 검사 (from이 to보다 이후인 경우)
            if (fromDate.isAfter(toDate)) {
                logger.warn("잘못된 날짜 범위: from({})이 to({})보다 이후", fromDate, toDate);
                response.put("success", false);
                response.put("message", "Invalid date range: 'from' date should be before or equal to 'to' date");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // 카운트가 음수인 경우 기본값으로 설정
            if (count <= 0) {
                count = 10;
            }
            
            // 서비스 호출하여 뉴스 정보 가져오기
            List<NewsItem> newsItems = stockService.fetchCompanyNews(
                symbol, 
                fromDate.format(formatter),
                toDate.format(formatter),
                count
            );
            
            logger.info("주식 뉴스 {} 개 조회 성공", newsItems.size());
            
            response.put("success", true);
            response.put("data", newsItems);
            response.put("count", newsItems.size());
            response.put("message", "Successfully fetched " + newsItems.size() + " news items");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("주식 뉴스 조회 오류: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error fetching stock news");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 마켓 뉴스를 가져옵니다.
     * @param from 시작 날짜 (yyyy-MM-dd 형식, 기본값: 오늘)
     * @param to 종료 날짜 (yyyy-MM-dd 형식, 기본값: 오늘)
     * @param count 가져올 뉴스 항목 수 (기본값: 10)
     * @return 마켓 뉴스 항목 목록
     */
    @GetMapping("/market_news")
    public ResponseEntity<?> getMarketNews(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "10") int count) {
        
        logger.info("마켓 뉴스 요청 - from: {}, to: {}, count: {}", from, to, count);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 날짜 파라미터 처리 (제공되지 않은 경우 오늘 날짜 사용)
            LocalDate fromDate = LocalDate.now();
            LocalDate toDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            if (from != null && !from.isEmpty()) {
                try {
                    fromDate = LocalDate.parse(from, formatter);
                } catch (DateTimeParseException e) {
                    logger.warn("잘못된 from 날짜 형식: {}", from);
                    response.put("success", false);
                    response.put("message", "Invalid date format for 'from'. Expected format: yyyy-MM-dd");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
            if (to != null && !to.isEmpty()) {
                try {
                    toDate = LocalDate.parse(to, formatter);
                } catch (DateTimeParseException e) {
                    logger.warn("잘못된 to 날짜 형식: {}", to);
                    response.put("success", false);
                    response.put("message", "Invalid date format for 'to'. Expected format: yyyy-MM-dd");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
            // 날짜 유효성 검사 (from이 to보다 이후인 경우)
            if (fromDate.isAfter(toDate)) {
                logger.warn("잘못된 날짜 범위: from({})이 to({})보다 이후", fromDate, toDate);
                response.put("success", false);
                response.put("message", "Invalid date range: 'from' date should be before or equal to 'to' date");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // 카운트가 음수인 경우 기본값으로 설정
            if (count <= 0) {
                count = 10;
            }
            
            // 서비스 호출하여 마켓 뉴스 정보 가져오기
            List<NewsItem> newsItems = stockService.fetchMarketNews(
                fromDate.format(formatter),
                toDate.format(formatter),
                count
            );
            
            logger.info("마켓 뉴스 {} 개 조회 성공", newsItems.size());
            
            response.put("success", true);
            response.put("data", newsItems);
            response.put("count", newsItems.size());
            response.put("message", "Successfully fetched " + newsItems.size() + " market news items");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("마켓 뉴스 조회 오류: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error fetching market news");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 특정 주식 심볼의 주요 재무 지표를 조회합니다.
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 주요 재무 지표
     */
    @GetMapping("/basic-financials/{symbol}")
    public ResponseEntity<?> getBasicFinancials(@PathVariable String symbol) {
        logger.info("주식 {} 기본 재무 지표 요청", symbol);
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 대문자로 변환
            symbol = symbol.toUpperCase();
            
            // 서비스 호출하여 재무 지표 가져오기
            BasicFinancialsDto financials = stockService.fetchBasicFinancials(symbol);
            
            response.put("success", true);
            response.put("data", financials);
            response.put("message", "Successfully fetched financial metrics for " + symbol);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("주식 {} 정보 조회 실패: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("message", "Stock not found: " + symbol);
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("주식 {} 재무 지표 조회 오류: {}", symbol, e.getMessage(), e);
            
            response.put("success", false);
            response.put("message", "Error fetching financial metrics for " + symbol);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 