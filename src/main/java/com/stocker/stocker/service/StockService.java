package com.stocker.stocker.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocker.stocker.domain.Stock;
import com.stocker.stocker.dto.StockSymbolDto;
import com.stocker.stocker.repository.StockRepository;
import com.stocker.stocker.domain.StockQuote;
import com.stocker.stocker.dto.StockQuoteDto;
import com.stocker.stocker.repository.StockQuoteRepository;
import com.stocker.stocker.domain.NewsItem;
import com.stocker.stocker.dto.BasicFinancialsDto;

import jakarta.transaction.Transactional;

@Service
public class StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockService.class);
    private final RestTemplate restTemplate;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;
    private final StockQuoteRepository stockQuoteRepository;
    
    @Value("${finnhub.api.key}")
    private String apiKey;
    
    public StockService(StockRepository stockRepository, RestTemplate restTemplate, ObjectMapper objectMapper, StockQuoteRepository stockQuoteRepository) {
        this.stockRepository = stockRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.stockQuoteRepository = stockQuoteRepository;
    }
    
    /**
     * Finnhub API를 호출하여 US 주식 심볼 데이터를 가져옵니다.
     * @return 가져온 주식 데이터 개수
     */
    public int fetchUSStockSymbols() {
        logger.info("Fetching US stock symbols from Finnhub API");
        
        String url = "https://finnhub.io/api/v1/stock/symbol?exchange=US";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Object[]> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                Object[].class
            );
            
            Object[] stockSymbols = response.getBody();
            
            if (stockSymbols != null) {
                logger.info("Successfully fetched {} US stock symbols", stockSymbols.length);
                // 처음 5개 항목만 로깅
                for (int i = 0; i < Math.min(5, stockSymbols.length); i++) {
                    logger.info("Stock symbol {}: {}", i+1, stockSymbols[i]);
                }
                
                // 데이터베이스에 저장
                return saveStocksToDatabase(stockSymbols);
            } else {
                logger.warn("No data returned from Finnhub API");
                return 0;
            }
        } catch (Exception e) {
            logger.error("Error fetching stock symbols from Finnhub API: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 모든 주식 심볼에 대해 상세 정보를 가져와 업데이트합니다.
     * @param batchSize 한 번에 처리할 주식 수
     * @param delayMs API 호출 사이의 지연 시간 (밀리초)
     * @return 업데이트된 주식 수
     */
    public int fetchAndUpdateAllStockProfiles(int batchSize, int delayMs) {
        logger.info("Starting to fetch and update stock profiles for ALL STOCKS");
        
        int totalCount = 0;
        int successCount = 0;
        int pageNumber = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize, Sort.by("id"));
            Page<Stock> stockPage = stockRepository.findAll(pageable);
            
            if (stockPage.isEmpty()) {
                break;
            }
            
            logger.info("Processing batch {} of stocks (page {} of {}, total stocks: {})", 
                batchSize, pageNumber + 1, stockPage.getTotalPages(), stockPage.getTotalElements());
                
            List<Stock> updatedStocks = new ArrayList<>();
            
            for (Stock stock : stockPage.getContent()) {
                totalCount++;
                
                // 각 주식에 대한 상세 정보 로깅 - 명확하게 개선하고 ID 정보 추가
                logger.info("===> Processing ALL STOCKS - #{}: ID={}, {} ({}), Country: {}", 
                        totalCount, stock.getId(), stock.getTicker(), stock.getName(), 
                        stock.getCountry() != null ? stock.getCountry() : "NULL");
                
                try {
                    // API 호출 사이에 지연 추가 (API 제한 고려)
                    if (totalCount > 1) {
                        Thread.sleep(delayMs);
                    }
                    
                    boolean updated = fetchAndUpdateStockProfile(stock);
                    if (updated) {
                        updatedStocks.add(stock);
                        successCount++;
                        logger.info("Stock {} ({}) was successfully updated", stock.getTicker(), stock.getId());
                        
                        if (updatedStocks.size() >= 50) {  // 50개마다 저장
                            stockRepository.saveAll(updatedStocks);
                            logger.info("Saved batch of {} stock profiles, total updated so far: {}", 
                                updatedStocks.size(), successCount);
                            updatedStocks.clear();
                        }
                    } else {
                        logger.info("Stock {} ({}) was not updated (no data or error)", stock.getTicker(), stock.getId());
                    }
                } catch (InterruptedException e) {
                    logger.error("Thread interrupted while waiting between API calls", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing stock {}: {}", stock.getTicker(), e.getMessage());
                }
            }
            
            // 남은 주식 저장
            if (!updatedStocks.isEmpty()) {
                stockRepository.saveAll(updatedStocks);
                logger.info("Saved remaining {} stock profiles in current batch", updatedStocks.size());
            }
            
            pageNumber++;
            
            // 모든 페이지 처리 완료
            if (pageNumber >= stockPage.getTotalPages()) {
                break;
            }
            
            logger.info("Completed page {} of {}. Progress: {}/{} stocks processed ({}%)",
                    pageNumber, stockPage.getTotalPages(), totalCount, stockPage.getTotalElements(),
                    String.format("%.2f", (100.0 * totalCount / stockPage.getTotalElements())));
        }
        
        logger.info("Completed stock profile updates. Processed {} stocks, successfully updated {}", 
            totalCount, successCount);
        
        return successCount;
    }
    
    /**
     * Country 값이 null인 주식 심볼에 대해서만 상세 정보를 가져와 업데이트합니다.
     * @param batchSize 한 번에 처리할 주식 수
     * @param delayMs API 호출 사이의 지연 시간 (밀리초)
     * @return 업데이트된 주식 수
     */
    public int fetchAndUpdateNullCountryStockProfiles(int batchSize, int delayMs) {
        long nullCountryCount = stockRepository.countByCountryIsNull();
        logger.info("Starting to fetch and update profiles for {} stocks with null country", nullCountryCount);
        
        int totalCount = 0;
        int successCount = 0;
        int pageNumber = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize, Sort.by("id"));
            Page<Stock> stockPage = stockRepository.findByCountryIsNull(pageable);
            
            if (stockPage.isEmpty()) {
                break;
            }
            
            logger.info("Processing stocks with null country (page {} of {}, size {})", 
                pageNumber + 1, stockPage.getTotalPages(), stockPage.getContent().size());
                
            List<Stock> updatedStocks = new ArrayList<>();
            
            for (Stock stock : stockPage.getContent()) {
                totalCount++;
                
                try {
                    // API 호출 사이에 지연 추가 (API 제한 고려)
                    if (totalCount > 1) {
                        Thread.sleep(delayMs);
                    }
                    
                    logger.info("Processing stock with null country: ID={}, {} ({})", 
                        stock.getId(), stock.getTicker(), stock.getName());
                    boolean updated = fetchAndUpdateStockProfile(stock);
                    if (updated) {
                        updatedStocks.add(stock);
                        successCount++;
                        
                        if (updatedStocks.size() >= 50) {  // 50개마다 저장
                            stockRepository.saveAll(updatedStocks);
                            logger.info("Saved batch of {} stock profiles, total updated so far: {}", 
                                updatedStocks.size(), successCount);
                            updatedStocks.clear();
                        }
                    }
                } catch (InterruptedException e) {
                    logger.error("Thread interrupted while waiting between API calls", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing stock {}: {}", stock.getTicker(), e.getMessage());
                }
            }
            
            // 남은 주식 저장
            if (!updatedStocks.isEmpty()) {
                stockRepository.saveAll(updatedStocks);
                logger.info("Saved remaining {} stock profiles in current batch", updatedStocks.size());
            }
            
            pageNumber++;
            
            // 모든 페이지 처리 완료
            if (pageNumber >= stockPage.getTotalPages()) {
                break;
            }
        }
        
        logger.info("Completed stock profile updates for null country stocks. Processed {} stocks, successfully updated {}", 
            totalCount, successCount);
        
        return successCount;
    }
    
    /**
     * 단일 주식 심볼에 대한 상세 정보를 가져와 업데이트합니다.
     * @param stock 업데이트할 주식 엔티티
     * @return 업데이트 성공 여부
     */
    private boolean fetchAndUpdateStockProfile(Stock stock) {
        return fetchAndUpdateStockProfile(stock, 0);  // 재시도 횟수 0으로 시작
    }
    
    /**
     * 단일 주식 심볼에 대한 상세 정보를 가져와 업데이트합니다. API 제한에 걸리면 대기 후 재시도합니다.
     * @param stock 업데이트할 주식 엔티티
     * @param retryCount 현재까지의 재시도 횟수
     * @return 업데이트 성공 여부
     */
    private boolean fetchAndUpdateStockProfile(Stock stock, int retryCount) {
        final int MAX_RETRIES = 3;  // 최대 재시도 횟수
        final long RATE_LIMIT_WAIT_MS = 1200;  // API 제한 도달 시 대기 시간 (밀리초)
        
        logger.info(">>> Fetching profile for stock: ID={}, {} ({}), Country: {}", 
            stock.getId(), stock.getTicker(), stock.getName(), stock.getCountry() != null ? stock.getCountry() : "NULL");
        
        String url = "https://finnhub.io/api/v1/stock/profile2?symbol=" + stock.getTicker();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            // ParameterizedTypeReference 사용하여 제네릭 타입 보존
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> profileData = response.getBody();
            
            if (profileData != null && !profileData.isEmpty()) {
                // 필드 업데이트
                updateStockFromProfile(stock, profileData);
                logger.info("<<< Successfully updated profile for stock: ID={}, {} ({})", 
                    stock.getId(), stock.getTicker(), stock.getName());
                return true;
            } else {
                logger.warn("No profile data returned for stock: ID={}, {} ({})", 
                    stock.getId(), stock.getTicker(), stock.getName());
                
                // No profile data인 경우 '-' 값으로 필드 채우기
                fillEmptyProfileWithDash(stock);
                
                // 필드 업데이트 후 저장 표시를 위해 true 반환
                return true;
            }
        } catch (HttpClientErrorException e) {
            // API 호출 제한에 도달했을 경우 (HTTP 429)
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                if (retryCount < MAX_RETRIES) {
                    logger.warn("API rate limit reached for stock: ID={}, {}. Waiting for {} ms before retry. Retry count: {}/{}",
                            stock.getId(), stock.getTicker(), RATE_LIMIT_WAIT_MS, retryCount + 1, MAX_RETRIES);
                    
                    try {
                        Thread.sleep(RATE_LIMIT_WAIT_MS);
                        // 재귀적으로 다시 시도
                        return fetchAndUpdateStockProfile(stock, retryCount + 1);
                    } catch (InterruptedException ie) {
                        logger.error("Sleep interrupted while waiting for API rate limit: {}", ie.getMessage());
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    logger.error("Max retries reached for stock ID={}, {}: {}", stock.getId(), stock.getTicker(), e.getMessage());
                    return false;
                }
            } else {
                // 다른 HTTP 에러
                logger.error("HTTP error for stock ID={}, {}: {} - {}", stock.getId(), stock.getTicker(), e.getStatusCode(), e.getMessage());
                return false;
            }
        } catch (RestClientException e) {
            // 기타 REST 클라이언트 에러
            logger.error("API error for stock ID={}, {}: {}", stock.getId(), stock.getTicker(), e.getMessage());
            return false;
        }
    }
    
    /**
     * API 응답 데이터로 주식 엔티티를 업데이트합니다.
     * @param stock 업데이트할 주식 엔티티
     * @param profileData API 응답 데이터
     */
    private void updateStockFromProfile(Stock stock, Map<String, Object> profileData) {
        // 일부 필드는 이미 있을 수 있으므로 기존 값 유지
        if (profileData.containsKey("name") && stock.getName() == null) {
            stock.setName((String) profileData.get("name"));
        }
        
        if (profileData.containsKey("currency")) {
            stock.setCurrency((String) profileData.get("currency"));
        }
        
        if (profileData.containsKey("estimateCurrency")) {
            stock.setEstimateCurrency((String) profileData.get("estimateCurrency"));
        }
        
        if (profileData.containsKey("exchange")) {
            stock.setExchange((String) profileData.get("exchange"));
        }
        
        if (profileData.containsKey("finnhubIndustry")) {
            stock.setFinnhubIndustry((String) profileData.get("finnhubIndustry"));
        }
        
        if (profileData.containsKey("marketCapitalization")) {
            Object mcap = profileData.get("marketCapitalization");
            if (mcap instanceof Number) {
                stock.setMarketCapitalization(new BigDecimal(mcap.toString()));
            }
        }
        
        if (profileData.containsKey("shareOutstanding")) {
            Object shares = profileData.get("shareOutstanding");
            if (shares instanceof Number) {
                stock.setShareOutstanding(((Number) shares).doubleValue());
            }
        }
        
        if (profileData.containsKey("country")) {
            stock.setCountry((String) profileData.get("country"));
        }
        
        if (profileData.containsKey("phone")) {
            stock.setPhone((String) profileData.get("phone"));
        }
        
        if (profileData.containsKey("ipo")) {
            String ipoDate = (String) profileData.get("ipo");
            try {
                LocalDate date = LocalDate.parse(ipoDate, DateTimeFormatter.ISO_DATE);
                stock.setIpo(date);
            } catch (DateTimeParseException e) {
                logger.warn("Could not parse IPO date: {} for stock: {}", ipoDate, stock.getTicker());
            }
        }
        
        if (profileData.containsKey("logo")) {
            stock.setLogo((String) profileData.get("logo"));
        }
        
        if (profileData.containsKey("weburl")) {
            stock.setWeburl((String) profileData.get("weburl"));
        }
    }
    
    /**
     * No Profile Data 상황에서 주식 필드를 '-' 값으로 채웁니다.
     * @param stock 데이터를 채울 주식 엔티티
     */
    private void fillEmptyProfileWithDash(Stock stock) {
        logger.info("Filling empty profile fields with '-' for stock: ID={}, {}", stock.getId(), stock.getTicker());
        
        // 이미 값이 있는 필드는 덮어쓰지 않음
        if (stock.getCurrency() == null) {
            stock.setCurrency("-");
        }
        
        if (stock.getEstimateCurrency() == null) {
            stock.setEstimateCurrency("-");
        }
        
        if (stock.getExchange() == null) {
            stock.setExchange("-");
        }
        
        if (stock.getFinnhubIndustry() == null) {
            stock.setFinnhubIndustry("-");
        }
        
        if (stock.getCountry() == null) {
            stock.setCountry("-");
        }
        
        if (stock.getPhone() == null) {
            stock.setPhone("-");
        }
        
        if (stock.getLogo() == null) {
            stock.setLogo("-");
        }
        
        if (stock.getWeburl() == null) {
            stock.setWeburl("-");
        }
        
        // 숫자 필드는 0으로 설정
        if (stock.getMarketCapitalization() == null) {
            stock.setMarketCapitalization(BigDecimal.ZERO);
        }
        
        if (stock.getShareOutstanding() == null) {
            stock.setShareOutstanding(0.0);
        }
        
        logger.info("Successfully filled empty fields with '-' for stock: {}", stock.getTicker());
    }
    
    /**
     * API에서 가져온 주식 심볼 데이터를 데이터베이스에 저장합니다.
     * @param stockSymbols API에서 가져온 주식 심볼 데이터 배열
     * @return 저장된 주식 데이터 개수
     */
    @Transactional
    public int saveStocksToDatabase(Object[] stockSymbols) {
        logger.info("Saving {} stock symbols to database", stockSymbols.length);
        
        List<Stock> stocksToSave = new ArrayList<>();
        int count = 0;
        
        for (Object obj : stockSymbols) {
            try {
                // Object를 StockSymbolDto로 변환
                StockSymbolDto dto = objectMapper.convertValue(obj, StockSymbolDto.class);
                
                // 이미 존재하는 티커인지 확인
                if (!stockRepository.existsByTicker(dto.getSymbol())) {
                    // StockSymbolDto를 Stock 엔티티로 변환
                    Stock stock = convertToStockEntity(dto);
                    stocksToSave.add(stock);
                    count++;
                    
                    // 일정 개수마다 배치 저장하여 메모리 효율성 향상
                    if (stocksToSave.size() >= 100) {
                        stockRepository.saveAll(stocksToSave);
                        stocksToSave.clear();
                        logger.info("Saved batch of 100 stocks, total saved so far: {}", count);
                    }
                }
            } catch (Exception e) {
                logger.error("Error converting stock symbol data: {}", e.getMessage(), e);
            }
        }
        
        // 남은 데이터 저장
        if (!stocksToSave.isEmpty()) {
            stockRepository.saveAll(stocksToSave);
            logger.info("Saved remaining {} stocks", stocksToSave.size());
        }
        
        logger.info("Successfully saved {} new stock symbols to database", count);
        return count;
    }
    
    /**
     * StockSymbolDto를 Stock 엔티티로 변환합니다.
     * @param dto API에서 가져온 주식 심볼 DTO
     * @return 변환된 Stock 엔티티
     */
    private Stock convertToStockEntity(StockSymbolDto dto) {
        return Stock.builder()
                .ticker(dto.getSymbol())
                .name(dto.getDescription())
                .currency(dto.getCurrency())
                .exchange(dto.getMic()) // 시장 코드를 exchange 필드에 저장
                .build();
    }

    /**
     * 특정 주식의 시세 정보를 Finnhub API에서 가져와 데이터베이스에 저장하고 반환합니다.
     * @param ticker 주식 심볼 (예: AAPL, MSFT)
     * @return 가져온 주식 시세 정보
     */
    @Transactional
    public StockQuote fetchAndSaveStockQuote(String ticker) {
        logger.info("Fetching quote for stock symbol: {}", ticker);
        
        // 주식 심볼 찾기
        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Stock with ticker " + ticker + " not found"));
        
        String url = "https://finnhub.io/api/v1/quote?symbol=" + ticker;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<StockQuoteDto> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                StockQuoteDto.class
            );
            
            StockQuoteDto quoteDto = response.getBody();
            
            if (quoteDto != null && quoteDto.getCurrentPrice() != null) {
                logger.info("Successfully fetched quote for {}: price={}, change={}({}%)", 
                    ticker, quoteDto.getCurrentPrice(), quoteDto.getChange(), quoteDto.getPercentChange());
                
                // DTO를 엔티티로 변환하여 저장
                StockQuote stockQuote = quoteDto.toEntity(stock);
                return stockQuoteRepository.save(stockQuote);
            } else {
                logger.warn("No valid quote data returned from Finnhub API for ticker: {}", ticker);
                throw new RuntimeException("Failed to get valid quote data for " + ticker);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("API rate limit exceeded when fetching quote for {}", ticker);
                throw new RuntimeException("API rate limit exceeded. Please try again later.", e);
            } else {
                logger.error("Error fetching quote for {}: {}", ticker, e.getMessage());
                throw new RuntimeException("Failed to fetch quote for " + ticker, e);
            }
        } catch (Exception e) {
            logger.error("Error fetching quote for {}: {}", ticker, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch quote for " + ticker, e);
        }
    }
    
    /**
     * 주식 심볼(ticker)로 주식 정보 조회
     * @param ticker 주식 심볼
     * @return 주식 정보
     */
    public Optional<Stock> getStockByTicker(String ticker) {
        logger.info("Fetching stock details for ticker: {}", ticker);
        return stockRepository.findByTicker(ticker);
    }
    
    /**
     * 특정 심볼의 주식 상세 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장하고 반환합니다.
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 업데이트된 주식 정보
     */
    @Transactional
    public Stock fetchAndSaveStockProfile(String symbol) {
        logger.info("Fetching profile for specific stock symbol: {}", symbol);
        
        // 주식 심볼 찾기
        Stock stock = stockRepository.findByTicker(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock with ticker " + symbol + " not found"));
        
        // 주식 상세 정보 가져오기
        boolean updated = fetchAndUpdateStockProfile(stock);
        
        if (!updated) {
            throw new RuntimeException("Failed to fetch and update profile for " + symbol);
        }
        
        // 업데이트된 주식 정보 저장 및 반환
        return stockRepository.save(stock);
    }
    
    /**
     * 모든 주식의 시세 정보를 Finnhub API에서 가져와 데이터베이스에 저장하고 반환합니다.
     * @param batchSize 한 번에 처리할 주식 수
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 가져온 주식 시세 정보 목록
     */
    @Transactional
    public List<StockQuote> fetchAndSaveAllStockQuotes(int batchSize, int delayMs) {
        logger.info("Starting to fetch quotes for ALL STOCKS with batchSize={}, delayMs={}", batchSize, delayMs);
        
        List<StockQuote> results = new ArrayList<>();
        int pageNumber = 0;
        int successCount = 0;
        int failedCount = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, batchSize, Sort.by("id"));
            Page<Stock> stockPage = stockRepository.findAll(pageable);
            
            if (stockPage.isEmpty()) {
                break;
            }
            
            logger.info("Processing batch {} of stocks (page {} of {}, total stocks: {})", 
                batchSize, pageNumber + 1, stockPage.getTotalPages(), stockPage.getTotalElements());
            
            List<StockQuote> batchResults = new ArrayList<>();
            
            for (Stock stock : stockPage.getContent()) {
                String ticker = stock.getTicker();
                
                try {
                    // API 호출 사이에 지연 추가 (API 제한 고려)
                    if (batchResults.size() > 0) {
                        Thread.sleep(delayMs);
                    }
                    
                    logger.info("Fetching quote for stock: ID={}, {} ({})", 
                        stock.getId(), ticker, stock.getName());
                    
                    StockQuote quote = fetchAndSaveStockQuote(ticker);
                    batchResults.add(quote);
                    successCount++;
                    
                    logger.info("Successfully fetched quote for {} ({}/{}): {}", 
                        ticker, successCount + failedCount, stockPage.getTotalElements(), quote);
                } catch (Exception e) {
                    failedCount++;
                    logger.error("Error fetching quote for {} (ID={}): {}", 
                        ticker, stock.getId(), e.getMessage());
                }
            }
            
            results.addAll(batchResults);
            pageNumber++;
            
            // 모든 페이지 처리 완료
            if (pageNumber >= stockPage.getTotalPages()) {
                break;
            }
            
            logger.info("Completed page {} of {}. Progress: {}/{} stocks processed ({}%)",
                    pageNumber, stockPage.getTotalPages(), 
                    successCount + failedCount, stockPage.getTotalElements(),
                    String.format("%.2f", (100.0 * (successCount + failedCount) / stockPage.getTotalElements())));
        }
        
        logger.info("Completed stock quote fetching. Processed {} stocks, success: {}, failed: {}", 
            successCount + failedCount, successCount, failedCount);
        
        return results;
    }

    /**
     * 특정 기간 동안의 주식 관련 뉴스를 가져옵니다.
     * @param symbol 주식 심볼 (특정 회사 뉴스만 필요한 경우)
     * @param from 시작 날짜 (yyyy-MM-dd 형식)
     * @param to 종료 날짜 (yyyy-MM-dd 형식)
     * @param count 가져올 뉴스 항목 수 (최대)
     * @return 뉴스 항목 목록
     */
    public List<NewsItem> fetchCompanyNews(String symbol, String from, String to, int count) {
        logger.info("Fetching company news for {} from {} to {}, max count: {}", 
                    symbol != null ? symbol : "all companies", from, to, count);
        
        String url;
        
        if (symbol != null && !symbol.isEmpty()) {
            // 특정 회사의 뉴스를 가져오는 API 엔드포인트
            url = String.format("https://finnhub.io/api/v1/company-news?symbol=%s&from=%s&to=%s", 
                        symbol.toUpperCase(), from, to);
        } else {
            // 일반 시장 뉴스를 가져오는 API 엔드포인트
            url = String.format("https://finnhub.io/api/v1/news?category=general&from=%s&to=%s", 
                        from, to);
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<NewsItem[]> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                NewsItem[].class
            );
            
            NewsItem[] newsItems = response.getBody();
            
            if (newsItems != null && newsItems.length > 0) {
                logger.info("Successfully fetched {} news items", newsItems.length);
                
                // 모든 뉴스 항목을 List로 변환
                List<NewsItem> allNews = new ArrayList<>(Arrays.asList(newsItems));
                
                // datetime 기준 내림차순으로 정렬 (최신 뉴스가 먼저 오도록)
                Collections.sort(allNews, (a, b) -> {
                    if (a.getDatetime() == null || b.getDatetime() == null) {
                        return 0; // null 값이 있으면 순서 유지
                    }
                    return b.getDatetime().compareTo(a.getDatetime()); // 내림차순 정렬
                });
                
                // 요청한 개수만큼 필터링
                List<NewsItem> filteredNews = new ArrayList<>();
                for (int i = 0; i < Math.min(count, allNews.size()); i++) {
                    filteredNews.add(allNews.get(i));
                }
                
                logger.info("Returning {} news items sorted by most recent first", filteredNews.size());
                return filteredNews;
            } else {
                logger.warn("No news data returned from Finnhub API");
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("API rate limit exceeded: {}", e.getMessage());
            } else {
                logger.error("Error fetching news from Finnhub API: {}", e.getMessage());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error fetching news from Finnhub API: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 마켓 뉴스를 가져옵니다.
     * @param from 시작 날짜 (yyyy-MM-dd 형식)
     * @param to 종료 날짜 (yyyy-MM-dd 형식)
     * @param count 가져올 뉴스 항목 수
     * @return 뉴스 항목 목록
     */
    public List<NewsItem> fetchMarketNews(String from, String to, int count) {
        logger.info("마켓 뉴스 요청 - from: {}, to: {}, count: {}", from, to, count);
        
        // 마켓 뉴스 API 엔드포인트 URL 구성 - 카테고리를 general로 설정
        String url = String.format("https://finnhub.io/api/v1/news?category=general&from=%s&to=%s", from, to);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<NewsItem[]> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                NewsItem[].class
            );
            
            NewsItem[] newsItems = response.getBody();
            
            if (newsItems != null && newsItems.length > 0) {
                logger.info("Successfully fetched {} market news items", newsItems.length);
                
                // 모든 뉴스 항목을 List로 변환
                List<NewsItem> allNews = new ArrayList<>(Arrays.asList(newsItems));
                
                // datetime 기준 내림차순으로 정렬 (최신 뉴스가 먼저 오도록)
                Collections.sort(allNews, (a, b) -> {
                    if (a.getDatetime() == null || b.getDatetime() == null) {
                        return 0; // null 값이 있으면 순서 유지
                    }
                    return b.getDatetime().compareTo(a.getDatetime()); // 내림차순 정렬
                });
                
                // 요청한 개수만큼 필터링
                List<NewsItem> filteredNews = new ArrayList<>();
                for (int i = 0; i < Math.min(count, allNews.size()); i++) {
                    filteredNews.add(allNews.get(i));
                }
                
                logger.info("Returning {} market news items sorted by most recent first", filteredNews.size());
                return filteredNews;
            } else {
                logger.warn("No market news data returned from Finnhub API");
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("API rate limit exceeded: {}", e.getMessage());
            } else {
                logger.error("Error fetching market news from Finnhub API: {}", e.getMessage());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error fetching market news from Finnhub API: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 특정 주식의 주요 재무 지표를 Finnhub API에서 가져옵니다.
     * @param symbol 주식 심볼 (예: AAPL, MSFT)
     * @return 주요 재무 지표
     */
    public BasicFinancialsDto fetchBasicFinancials(String symbol) {
        logger.info("Fetching basic financials for stock symbol: {}", symbol);
        
        String url = String.format("https://finnhub.io/api/v1/stock/metric?symbol=%s&metric=all", 
                    symbol.toUpperCase());
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", apiKey);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, 
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> responseData = response.getBody();
            
            if (responseData != null && responseData.containsKey("metric")) {
                logger.info("Successfully fetched basic financials for {}", symbol);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = (Map<String, Object>) responseData.get("metric");
                
                // DTO 객체 생성 및 필요한 데이터만 추출하여 설정
                BasicFinancialsDto dto = new BasicFinancialsDto();
                dto.setSymbol(symbol.toUpperCase());
                
                // 각 필드에 대해 데이터 설정 (null 체크 포함)
                setMetricValueToBigDecimal(metrics, "marketCapitalization", dto::setMarketCapitalization);
                setMetricValueToBigDecimal(metrics, "enterpriseValue", dto::setEnterpriseValue);
                setMetricValueToBigDecimal(metrics, "peTTM", dto::setPeTTM);
                setMetricValueToBigDecimal(metrics, "peExclExtraTTM", dto::setPeExclExtraTTM);
                setMetricValueToBigDecimal(metrics, "pb", dto::setPb);
                setMetricValueToBigDecimal(metrics, "pbQuarterly", dto::setPbQuarterly);
                setMetricValueToBigDecimal(metrics, "psTTM", dto::setPsTTM);
                setMetricValueToBigDecimal(metrics, "dividendYieldIndicatedAnnual", dto::setDividendYieldIndicatedAnnual);
                setMetricValueToBigDecimal(metrics, "currentDividendYieldTTM", dto::setCurrentDividendYieldTTM);
                setMetricValueToBigDecimal(metrics, "currentEv/freeCashFlowTTM", dto::setCurrentEvFreeCashFlowTTM);
                setMetricValueToBigDecimal(metrics, "pcfShareTTM", dto::setPcfShareTTM);
                setMetricValueToBigDecimal(metrics, "pfcfShareTTM", dto::setPfcfShareTTM);
                setMetricValueToBigDecimal(metrics, "ptbvQuarterly", dto::setPtbvQuarterly);
                
                // 기술적 지표 추가
                
                // 52주 최고/최저가
                setMetricValueToBigDecimal(metrics, "52WeekHigh", dto::setWeekHigh52);
                if (metrics.containsKey("52WeekHighDate") && metrics.get("52WeekHighDate") != null) {
                    dto.setWeekHighDate52(metrics.get("52WeekHighDate").toString());
                }
                setMetricValueToBigDecimal(metrics, "52WeekLow", dto::setWeekLow52);
                if (metrics.containsKey("52WeekLowDate") && metrics.get("52WeekLowDate") != null) {
                    dto.setWeekLowDate52(metrics.get("52WeekLowDate").toString());
                }
                
                // 변동성 지표
                setMetricValueToBigDecimal(metrics, "beta", dto::setBeta);
                setMetricValueToBigDecimal(metrics, "3MonthADReturnStd", dto::setVolatility90Day);  // 90일 변동성으로 사용
                
                // 거래량 지표
                setMetricValueToBigDecimal(metrics, "10DayAverageTradingVolume", dto::setDayAverageTradingVolume10);
                setMetricValueToBigDecimal(metrics, "3MonthAverageTradingVolume", dto::setMonthAverageTradingVolume3);
                
                // 수익률 지표
                setMetricValueToBigDecimal(metrics, "5DayPriceReturnDaily", dto::setDayPriceReturnDaily5);
                setMetricValueToBigDecimal(metrics, "13WeekPriceReturnDaily", dto::setWeekPriceReturnDaily13);
                setMetricValueToBigDecimal(metrics, "26WeekPriceReturnDaily", dto::setWeekPriceReturnDaily26);
                setMetricValueToBigDecimal(metrics, "52WeekPriceReturnDaily", dto::setWeekPriceReturnDaily52);
                setMetricValueToBigDecimal(metrics, "yearToDatePriceReturnDaily", dto::setYearToDatePriceReturnDaily);
                
                // 상대 강도 지표
                setMetricValueToBigDecimal(metrics, "priceRelativeToS&P50013Week", dto::setPriceRelativeToSP50013Week);
                setMetricValueToBigDecimal(metrics, "priceRelativeToS&P50026Week", dto::setPriceRelativeToSP50026Week);
                setMetricValueToBigDecimal(metrics, "priceRelativeToS&P5004Week", dto::setPriceRelativeToSP5004Week);
                setMetricValueToBigDecimal(metrics, "priceRelativeToS&P50052Week", dto::setPriceRelativeToSP50052Week);
                setMetricValueToBigDecimal(metrics, "priceRelativeToS&P500Ytd", dto::setPriceRelativeToSP500Ytd);
                
                // 주당순이익(EPS) 추가
                setMetricValueToBigDecimal(metrics, "epsTTM", dto::setEpsTTM);
                
                return dto;
            } else {
                logger.warn("No financial data returned from Finnhub API for ticker: {}", symbol);
                throw new RuntimeException("Failed to get financial data for " + symbol);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalArgumentException("Stock with ticker " + symbol + " not found");
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("API rate limit exceeded when fetching financials for {}", symbol);
                throw new RuntimeException("API rate limit exceeded. Please try again later.", e);
            } else {
                logger.error("Error fetching financial data for {}: {}", symbol, e.getMessage());
                throw new RuntimeException("Failed to fetch financial data for " + symbol, e);
            }
        } catch (Exception e) {
            logger.error("Error fetching financial data for {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch financial data for " + symbol, e);
        }
    }

    /**
     * 메트릭 맵에서 값을 추출하여 BigDecimal로 변환 후 DTO에 설정합니다.
     */
    private void setMetricValueToBigDecimal(Map<String, Object> metrics, String key, 
                                            Consumer<BigDecimal> setter) {
        if (metrics.containsKey(key) && metrics.get(key) != null) {
            try {
                Object value = metrics.get(key);
                if (value instanceof Number) {
                    setter.accept(new BigDecimal(value.toString()));
                } else if (value instanceof String) {
                    // 문자열인 경우, 숫자로 변환 시도
                    setter.accept(new BigDecimal((String) value));
                }
            } catch (NumberFormatException e) {
                logger.warn("Failed to convert metric value for key {}: {}", key, e.getMessage());
                // 변환 실패 시 null 설정 (기본값)
                setter.accept(null);
            }
        } else {
            // 해당 키가 없거나 값이 null인 경우
            setter.accept(null);
        }
    }
} 