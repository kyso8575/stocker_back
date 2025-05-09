package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.config.FinnhubApiConfig;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.dto.StockSymbolDTO;
import com.stocker_back.stocker_back.dto.CompanyProfileDTO;
import com.stocker_back.stocker_back.dto.QuoteDTO;
import com.stocker_back.stocker_back.dto.FinancialMetricsDTO;
import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockSymbolService {

    private final RestTemplate restTemplate;
    private final StockSymbolRepository stockSymbolRepository;
    private final QuoteRepository quoteRepository;
    private final FinancialMetricsRepository financialMetricsRepository;
    private final FinnhubApiConfig finnhubApiConfig;
    
    public StockSymbolService(
        @Qualifier("customRestTemplate") RestTemplate restTemplate,
        StockSymbolRepository stockSymbolRepository,
        QuoteRepository quoteRepository,
        FinancialMetricsRepository financialMetricsRepository,
        FinnhubApiConfig finnhubApiConfig) {
        this.restTemplate = restTemplate;
        this.stockSymbolRepository = stockSymbolRepository;
        this.quoteRepository = quoteRepository;
        this.financialMetricsRepository = financialMetricsRepository;
        this.finnhubApiConfig = finnhubApiConfig;
    }
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:200}")
    private int batchSize; // 배치 크기를 50에서 200으로 증가
    
    @Value("${app.parallel.chunk-size:1000}")
    private int chunkSize; // 병렬 처리를 위한 청크 크기를 1000으로 증가
    
    @Value("${app.parallel.max-threads:8}")
    private int maxThreads; // 최대 병렬 스레드 수를 8로 증가
    
    /**
     * 특정 거래소의 주식 심볼 데이터를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param exchange 거래소 코드(예: US, KO 등)
     * @param symbol 특정 심볼(예: AAPL). null이면 모든 심볼을 가져옴
     * @return 저장된 심볼 개수
     */
    @Transactional
    public int fetchAndSaveStockSymbols(String exchange, String symbol) {
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Fetching single stock symbol: {} for exchange: {}", symbol, exchange);
            return fetchAndSaveSingleSymbol(exchange, symbol);
        } else {
            log.info("Fetching all stock symbols for exchange: {}", exchange);
            return fetchAndSaveAllSymbols(exchange);
        }
    }
    
    /**
     * 특정 거래소의 모든 주식 심볼 데이터를 가져와 저장
     * @param exchange 거래소 코드
     * @return 저장된 심볼 개수
     */
    private int fetchAndSaveAllSymbols(String exchange) {
        // Finnhub API URL 생성 (최신 방법 사용)
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1/stock/symbol")
                .queryParam("exchange", exchange)
                .queryParam("token", finnhubApiConfig.getApiKey())
                .toUriString();
        
        // API 호출
        ResponseEntity<List<StockSymbolDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<StockSymbolDTO>>() {}
        );
        
        List<StockSymbolDTO> symbolDTOs = response.getBody();
        
        if (symbolDTOs == null || symbolDTOs.isEmpty()) {
            log.warn("No stock symbols found for exchange: {}", exchange);
            return 0;
        }
        
        log.info("Fetched {} stock symbols from Finnhub API", symbolDTOs.size());
        
        // 병렬 처리를 위해 심볼 데이터를 청크로 분할하여 처리
        return saveSymbolsInParallel(symbolDTOs, exchange);
    }
    
    /**
     * 심볼 데이터를 병렬로 저장 처리
     * @param symbolDTOs 심볼 DTO 리스트
     * @param exchange 거래소 코드
     * @return 저장된 심볼 개수
     */
    private int saveSymbolsInParallel(List<StockSymbolDTO> symbolDTOs, String exchange) {
        int totalSize = symbolDTOs.size();
        List<List<StockSymbolDTO>> chunks = new ArrayList<>();
        
        // 데이터를 청크로 분할
        for (int i = 0; i < totalSize; i += chunkSize) {
            int end = Math.min(i + chunkSize, totalSize);
            chunks.add(symbolDTOs.subList(i, end));
        }
        
        log.info("Split {} symbols into {} chunks for parallel processing", totalSize, chunks.size());
        
        // 기존 심볼 캐싱 - 성능 개선의 핵심
        Set<String> existingSymbols = stockSymbolRepository.findAllSymbols();
        log.info("Loaded {} existing symbols for duplicate checking", existingSymbols.size());
        
        // 병렬 처리를 위한 비동기 작업 목록
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        // 최대 스레드 수 제한
        int threadsToUse = Math.min(chunks.size(), maxThreads);
        log.info("Using {} threads for parallel processing", threadsToUse);
        
        // 각 청크를 비동기로 처리
        for (int i = 0; i < chunks.size(); i++) {
            List<StockSymbolDTO> chunk = chunks.get(i);
            CompletableFuture<Integer> future = processChunkAsync(chunk, exchange, i, existingSymbols);
            futures.add(future);
        }
        
        // 모든 비동기 작업이 완료될 때까지 대기하고 결과 취합
        int totalSaved = 0;
        try {
            for (CompletableFuture<Integer> future : futures) {
                totalSaved += future.get(); // get()은 블로킹 호출
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error while processing symbols in parallel", e);
            Thread.currentThread().interrupt();
        }
        
        log.info("Completed parallel processing, total saved: {}", totalSaved);
        return totalSaved;
    }
    
    /**
     * 심볼 데이터의 청크를 비동기적으로 처리
     * @param chunk 처리할 심볼 DTO의 청크
     * @param exchange 거래소 코드
     * @param chunkIndex 청크 인덱스 (로깅용)
     * @param existingSymbols 기존에 존재하는 심볼 집합
     * @return 저장된 심볼 개수를 포함하는 CompletableFuture
     */
    @Async
    public CompletableFuture<Integer> processChunkAsync(
            List<StockSymbolDTO> chunk, 
            String exchange, 
            int chunkIndex, 
            Set<String> existingSymbols) {
        log.info("Processing chunk {} with {} symbols", chunkIndex, chunk.size());
        int savedCount = saveBatch(chunk, exchange, chunkIndex, existingSymbols);
        log.info("Completed processing chunk {}, saved {} symbols", chunkIndex, savedCount);
        return CompletableFuture.completedFuture(savedCount);
    }
    
    /**
     * 특정 심볼 하나만 가져와 저장
     * @param exchange 거래소 코드
     * @param symbol 저장할 심볼
     * @return 저장된 심볼 개수 (0 또는 1)
     */
    private int fetchAndSaveSingleSymbol(String exchange, String symbol) {
        // 먼저 전체 심볼 목록 가져오기
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1/stock/symbol")
                .queryParam("exchange", exchange)
                .queryParam("token", finnhubApiConfig.getApiKey())
                .toUriString();
        
        ResponseEntity<List<StockSymbolDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<StockSymbolDTO>>() {}
        );
        
        List<StockSymbolDTO> allSymbols = response.getBody();
        
        if (allSymbols == null || allSymbols.isEmpty()) {
            log.warn("No stock symbols found for exchange: {}", exchange);
            return 0;
        }
        
        // 특정 심볼만 필터링
        List<StockSymbolDTO> filteredSymbols = allSymbols.stream()
                .filter(dto -> symbol.equalsIgnoreCase(dto.getSymbol()))
                .collect(Collectors.toList());
        
        if (filteredSymbols.isEmpty()) {
            log.warn("Symbol {} not found in exchange {}", symbol, exchange);
            return 0;
        }
        
        log.info("Found symbol {} in exchange {}", symbol, exchange);
        
        // 기존 심볼 캐싱
        Set<String> existingSymbols = stockSymbolRepository.findAllSymbols();
        
        // 단일 심볼 저장
        return saveBatch(filteredSymbols, exchange, 0, existingSymbols);
    }
    
    /**
     * 심볼 데이터를 배치 단위로 저장
     * @param symbolDTOs 심볼 DTO 리스트
     * @param exchange 거래소 코드
     * @param chunkIndex 청크 인덱스 (로깅용)
     * @param existingSymbols 기존에 존재하는 심볼 집합
     * @return 저장된 심볼 개수
     */
    private int saveBatch(
            List<StockSymbolDTO> symbolDTOs, 
            String exchange, 
            int chunkIndex, 
            Set<String> existingSymbols) {
        List<StockSymbol> symbolsToSave = new ArrayList<>();
        int totalSize = symbolDTOs.size();
        int processedCount = 0;
        int savedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        
        for (StockSymbolDTO dto : symbolDTOs) {
            // 중복 체크 개선: 메모리 내에서 확인 (데이터베이스 쿼리 없음)
            if (existingSymbols.contains(dto.getSymbol())) {
                log.debug("Chunk {}: Symbol {} already exists, skipping", chunkIndex, dto.getSymbol());
                processedCount++;
                continue;
            }
            
            StockSymbol symbol = StockSymbol.builder()
                    .symbol(dto.getSymbol())
                    .description(dto.getDescription() != null ? dto.getDescription() : "")
                    .displaySymbol(dto.getDisplaySymbol())
                    .type(dto.getType())
                    .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
                    .exchange(exchange)
                    .figi(dto.getFigi())
                    .mic(dto.getMic())
                    .lastUpdated(now)
                    .build();
            
            symbolsToSave.add(symbol);
            // 처리가 완료된 심볼을 existingSymbols에 추가하여 중복 저장 방지
            existingSymbols.add(dto.getSymbol());
            processedCount++;
            savedCount++;
            
            // 배치 크기에 도달하거나 마지막 항목인 경우 저장
            if (symbolsToSave.size() >= batchSize || processedCount == totalSize) {
                // 별도의 트랜잭션으로 배치 저장
                saveBatchWithNewTransaction(symbolsToSave);
                
                // 진행 상황 기록
                int progressPercent = (processedCount * 100) / totalSize;
                log.info("Chunk {}: Saved batch of symbols: {} of {} ({}%), unique symbols saved: {}", 
                        chunkIndex, processedCount, totalSize, progressPercent, savedCount);
                
                symbolsToSave.clear();
            }
        }
        
        return savedCount;
    }
    
    /**
     * 심볼 데이터를 새로운 트랜잭션에서 배치 저장
     * 각 배치마다 별도의 트랜잭션을 사용하여 메모리 사용량을 줄이고 성능 향상
     * @param symbolsToSave 저장할 심볼 리스트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBatchWithNewTransaction(List<StockSymbol> symbolsToSave) {
        stockSymbolRepository.saveAll(symbolsToSave);
    }

    /**
     * 모든 주식 심볼에 대한 회사 프로필 정보를 가져와 저장합니다.
     * @param batchSize 한 번에 처리할 주식 수
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 업데이트된 회사 프로필 수
     */
    public int fetchAndSaveAllCompanyProfiles(int batchSize, int delayMs) {
        log.info("Fetching all company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        // 배치 크기가 지정되지 않았거나 너무 작은 경우 기본값 설정
        if (batchSize <= 0) {
            batchSize = 100;
        }
        
        // 모든 심볼 조회
        List<StockSymbol> allSymbols = stockSymbolRepository.findAll();
        log.info("Found {} stock symbols to process for company profiles", allSymbols.size());
        
        int updatedCount = 0;
        int emptyCount = 0;
        int totalProcessed = 0;
        
        // 배치 처리를 위한 리스트
        List<StockSymbol> batch = new ArrayList<>(batchSize);
        
        for (int i = 0; i < allSymbols.size(); i++) {
            StockSymbol symbol = allSymbols.get(i);
            totalProcessed++;
            
            try {
                log.info("Processing symbol {}/{}: {}", totalProcessed, allSymbols.size(), symbol.getSymbol());
                
                // 회사 프로필 정보 가져오기
                CompanyProfileDTO profileDTO = fetchCompanyProfile(symbol.getSymbol());
                symbol.setProfileUpdated(LocalDateTime.now());
                
                if (profileDTO != null) {
                    // 프로필 정보 업데이트
                    updateCompanyProfile(symbol, profileDTO);
                    symbol.setProfileEmpty(false);
                    batch.add(symbol);  // 배치에 추가
                    updatedCount++;
                    
                    log.debug("Updated company profile for symbol: {} ({}/{})", 
                             symbol.getSymbol(), totalProcessed, allSymbols.size());
                } else {
                    // 빈 응답인 경우 표시
                    symbol.setProfileEmpty(true);
                    batch.add(symbol);  // 배치에 추가
                    emptyCount++;
                    
                    log.debug("Marked empty profile for symbol: {} ({}/{})", 
                             symbol.getSymbol(), totalProcessed, allSymbols.size());
                }
                
                // 배치 크기에 도달하면 저장
                if (batch.size() >= batchSize) {
                    // 별도의 트랜잭션으로 배치 저장 - 이 메소드는 새로운 트랜잭션을 시작함
                    saveCompanyProfileBatch(batch);
                    log.info("Saved batch of {} company profiles (batch: {}, total processed: {}/{})", 
                            batch.size(), 
                            (totalProcessed / batchSize), 
                            totalProcessed, 
                            allSymbols.size());
                    batch.clear();
                }
                
                // 진행 상황 로깅
                if (totalProcessed % 10 == 0 || totalProcessed == allSymbols.size()) {
                    log.info("Progress: {}/{} symbols processed ({}%) - valid: {}, empty: {}", 
                             totalProcessed, allSymbols.size(), 
                             (totalProcessed * 100 / allSymbols.size()), 
                             updatedCount, emptyCount);
                }
                
                // API 레이트 제한 방지를 위한 지연
                if (i < allSymbols.size() - 1 && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                log.error("Thread interrupted during company profile fetch", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching company profile for symbol {}: {}", symbol.getSymbol(), e.getMessage());
                
                // 에러 발생 시에도 빈 프로필로 표시하고 배치에 추가
                try {
                    symbol.setProfileEmpty(true);
                    symbol.setProfileUpdated(LocalDateTime.now());
                    batch.add(symbol);  // 배치에 추가
                    emptyCount++;
                    
                    // 배치 크기에 도달하면 저장
                    if (batch.size() >= batchSize) {
                        // 별도의 트랜잭션으로 배치 저장
                        saveCompanyProfileBatch(batch);
                        log.info("Saved batch of {} company profiles (batch: {}, total processed: {}/{})", 
                                batch.size(), 
                                (totalProcessed / batchSize), 
                                totalProcessed, 
                                allSymbols.size());
                        batch.clear();
                    }
                    
                    log.debug("Marked error for symbol: {} ({}/{})", 
                             symbol.getSymbol(), totalProcessed, allSymbols.size());
                } catch (Exception ex) {
                    log.error("Error updating profile status for symbol {}: {}", 
                            symbol.getSymbol(), ex.getMessage());
                }
            }
        }
        
        // 남은 배치 저장
        if (!batch.isEmpty()) {
            // 별도의 트랜잭션으로 배치 저장
            saveCompanyProfileBatch(batch);
            log.info("Saved final batch of {} company profiles", batch.size());
        }
        
        log.info("Completed company profile update: {} valid profiles, {} empty profiles", 
                updatedCount, emptyCount);
        
        return updatedCount + emptyCount;
    }
    
    /**
     * 회사 프로필 배치를 별도의 트랜잭션으로 저장합니다.
     * 이 메소드는 새로운 트랜잭션을 시작하므로 호출자의 트랜잭션과 독립적으로 커밋됩니다.
     * @param batch 저장할 StockSymbol 배치
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCompanyProfileBatch(List<StockSymbol> batch) {
        stockSymbolRepository.saveAll(batch);
    }
    
    /**
     * 특정 주식 심볼에 대한 회사 프로필 정보를 Finnhub API에서 가져옵니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 회사 프로필 DTO 또는 없는 경우 null
     */
    private CompanyProfileDTO fetchCompanyProfile(String symbol) {
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1/stock/profile2")
                .queryParam("symbol", symbol)
                .queryParam("token", finnhubApiConfig.getApiKey())
                .toUriString();
        
        try {
            ResponseEntity<CompanyProfileDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    CompanyProfileDTO.class
            );
            
            CompanyProfileDTO profile = response.getBody();
            
            // 응답이 비어있거나 필수 필드가 누락된 경우
            if (profile == null || profile.getSymbol() == null || profile.getSymbol().isEmpty()) {
                log.warn("Empty or invalid company profile response for symbol: {}", symbol);
                return null;
            }
            
            return profile;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded for Finnhub API when fetching company profile for {}", symbol);
            } else {
                log.error("HTTP error fetching company profile for {}: {} - {}", 
                      symbol, e.getStatusCode(), e.getResponseBodyAsString());
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching company profile for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
    
    /**
     * 주식 심볼 엔티티에 회사 프로필 정보를 업데이트합니다.
     * @param symbol 업데이트할 주식 심볼 엔티티
     * @param profileDTO 회사 프로필 DTO
     */
    private void updateCompanyProfile(StockSymbol symbol, CompanyProfileDTO profileDTO) {
        symbol.setCountry(profileDTO.getCountry());
        symbol.setEstimateCurrency(profileDTO.getEstimateCurrency());
        symbol.setFinnhubIndustry(profileDTO.getFinnhubIndustry());
        symbol.setIpo(profileDTO.getIpo());
        symbol.setLogo(profileDTO.getLogo());
        symbol.setName(profileDTO.getName());
        symbol.setPhone(profileDTO.getPhone());
        symbol.setShareOutstanding(profileDTO.getShareOutstanding());
        symbol.setWeburl(profileDTO.getWeburl());
        symbol.setProfileUpdated(LocalDateTime.now());
    }
    
    /**
     * 단일 주식 심볼에 대한 회사 프로필 정보를 가져와 저장합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 업데이트된 StockSymbol 엔티티 또는 null
     */
    @Transactional
    public StockSymbol fetchAndSaveSingleCompanyProfile(String symbol) {
        log.info("Fetching company profile for symbol: {}", symbol);
        
        // 심볼 대문자로 변환
        symbol = symbol.toUpperCase();
        
        // 데이터베이스에서 심볼 확인
        Optional<StockSymbol> symbolEntity = stockSymbolRepository.findBySymbol(symbol);
        
        if (symbolEntity.isEmpty()) {
            log.warn("Symbol {} not found in database", symbol);
            return null;
        }
        
        StockSymbol stockSymbol = symbolEntity.get();
        stockSymbol.setProfileUpdated(LocalDateTime.now());
        
        try {
            // 회사 프로필 정보 가져오기
            CompanyProfileDTO profileDTO = fetchCompanyProfile(symbol);
            
            if (profileDTO != null) {
                // 프로필 정보 업데이트
                updateCompanyProfile(stockSymbol, profileDTO);
                stockSymbol.setProfileEmpty(false);
                stockSymbolRepository.save(stockSymbol);
                log.info("Successfully updated company profile for symbol: {}", symbol);
                return stockSymbol;
            } else {
                // 빈 응답인 경우 표시
                stockSymbol.setProfileEmpty(true);
                stockSymbolRepository.save(stockSymbol);
                log.info("Marked empty profile for symbol: {}", symbol);
                return stockSymbol;
            }
        } catch (Exception e) {
            // 에러 발생 시에도 빈 프로필로 표시
            try {
                stockSymbol.setProfileEmpty(true);
                stockSymbolRepository.save(stockSymbol);
                log.info("Marked error for company profile fetch for symbol: {}", symbol);
            } catch (Exception ex) {
                log.error("Error updating profile status: {}", ex.getMessage(), ex);
            }
            
            log.error("Error fetching and saving company profile for symbol {}: {}", 
                    symbol, e.getMessage(), e);
            return stockSymbol;
        }
    }

    /**
     * Fetch quote data for all stock symbols from Finnhub API and save them to the database
     * @param batchSize Number of symbols to process in a batch (default: 20)
     * @param delayMs Delay between API calls in milliseconds to avoid rate limits (default: 500)
     * @return The number of successfully fetched and saved quotes
     */
    public int fetchAndSaveAllQuotes(int batchSize, int delayMs) {
        log.info("Fetching quotes for all symbols with batchSize={}, delayMs={}", batchSize, delayMs);
        
        if (batchSize <= 0) {
            batchSize = 20;
        }
        
        // Get all symbols from the database
        List<StockSymbol> allSymbols = stockSymbolRepository.findAll();
        log.info("Found {} stock symbols to process for quotes", allSymbols.size());
        
        int successCount = 0;
        int errorCount = 0;
        int totalProcessed = 0;
        
        // 배치 처리를 위한 리스트
        List<Quote> batch = new ArrayList<>(batchSize);
        
        for (int i = 0; i < allSymbols.size(); i++) {
            StockSymbol symbol = allSymbols.get(i);
            totalProcessed++;
            
            try {
                log.info("Processing quote for symbol {}/{}: {}", 
                        totalProcessed, allSymbols.size(), symbol.getSymbol());
                
                // Fetch quote for this symbol
                QuoteDTO quoteDTO = fetchQuoteFromFinnhub(symbol.getSymbol());
                
                if (quoteDTO != null && quoteDTO.getCurrentPrice() != null) {
                    // Create a Quote entity
                    Quote quote = Quote.builder()
                            .symbol(symbol.getSymbol())
                            .currentPrice(quoteDTO.getCurrentPrice())
                            .change(quoteDTO.getChange())
                            .percentChange(quoteDTO.getPercentChange())
                            .highPrice(quoteDTO.getHighPrice())
                            .lowPrice(quoteDTO.getLowPrice())
                            .openPrice(quoteDTO.getOpenPrice())
                            .previousClosePrice(quoteDTO.getPreviousClosePrice())
                            .timestamp(quoteDTO.getTimestamp())
                            .build();
                    
                    // Add to batch
                    batch.add(quote);
                    successCount++;
                    
                    log.debug("Added quote for symbol {} to batch ({}/{})", 
                             symbol.getSymbol(), totalProcessed, allSymbols.size());
                } else {
                    errorCount++;
                    log.debug("Failed to fetch quote data for symbol: {} ({}/{})", 
                             symbol.getSymbol(), totalProcessed, allSymbols.size());
                }
                
                // 배치 크기에 도달하면 저장
                if (batch.size() >= batchSize) {
                    // 별도의 트랜잭션으로 배치 저장
                    saveQuoteBatch(batch);
                    log.info("Saved batch of {} quotes (batch: {}, total processed: {}/{})", 
                            batch.size(), 
                            (totalProcessed / batchSize), 
                            totalProcessed, 
                            allSymbols.size());
                    batch.clear();
                }
                
                // Log progress
                if (totalProcessed % 10 == 0 || totalProcessed == allSymbols.size()) {
                    log.info("Progress: {}/{} symbols processed ({}%) - success: {}, errors: {}", 
                             totalProcessed, allSymbols.size(), 
                             (totalProcessed * 100 / allSymbols.size()), 
                             successCount, errorCount);
                }
                
                // Add delay to avoid rate limiting
                if (i < allSymbols.size() - 1 && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                log.error("Thread interrupted during quote fetch", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error fetching quote for symbol {}: {}", symbol.getSymbol(), e.getMessage());
                errorCount++;
            }
        }
        
        // 남은 배치 저장
        if (!batch.isEmpty()) {
            // 별도의 트랜잭션으로 배치 저장
            saveQuoteBatch(batch);
            log.info("Saved final batch of {} quotes", batch.size());
        }
        
        log.info("Completed quote fetch: {} successful, {} errors", successCount, errorCount);
        return successCount;
    }
    
    /**
     * 견적 데이터 배치를 저장합니다.
     * @param quoteBatch 저장할 Quote 배치
     * @return 저장된 Quote 목록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Quote> saveQuoteBatch(List<Quote> quoteBatch) {
        // 저장 전 로그
        log.info("Saving batch of {} quotes to database", quoteBatch.size());
        
        // 심플하게 배치 저장 수행 - 회사 프로필 배치 저장과 동일한 방식
        List<Quote> savedQuotes = quoteRepository.saveAll(quoteBatch);
        
        // 저장 후 로그
        log.info("Successfully saved {} quotes to database", savedQuotes.size());
        
        return savedQuotes;
    }

    /**
     * Fetch quote data for a specific stock symbol from Finnhub API and save it to the database
     * @param symbol Stock symbol (e.g., AAPL)
     * @return The saved Quote entity
     */
    @Transactional
    public Quote fetchAndSaveQuote(String symbol) {
        log.info("Fetching quote data for symbol: {}", symbol);
        
        QuoteDTO quoteDTO = fetchQuoteFromFinnhub(symbol);
        
        if (quoteDTO == null || quoteDTO.getCurrentPrice() == null) {
            log.warn("No quote data found for symbol: {}", symbol);
            return null;
        }
        
        // Create a new Quote entity
        Quote quote = Quote.builder()
                .symbol(symbol)
                .currentPrice(quoteDTO.getCurrentPrice())
                .change(quoteDTO.getChange())
                .percentChange(quoteDTO.getPercentChange())
                .highPrice(quoteDTO.getHighPrice())
                .lowPrice(quoteDTO.getLowPrice())
                .openPrice(quoteDTO.getOpenPrice())
                .previousClosePrice(quoteDTO.getPreviousClosePrice())
                .timestamp(quoteDTO.getTimestamp())
                .build();
        
        log.info("Saving quote data for symbol: {}", symbol);
        return quoteRepository.save(quote);
    }
    
    /**
     * Fetch quote data from Finnhub API
     * @param symbol Stock symbol
     * @return QuoteDTO containing the quote data
     */
    private QuoteDTO fetchQuoteFromFinnhub(String symbol) {
        // Build the URL for Finnhub API
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1/quote")
                .queryParam("symbol", symbol)
                .queryParam("token", finnhubApiConfig.getApiKey())
                .toUriString();
        
        try {
            // Call the Finnhub API
            ResponseEntity<QuoteDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    QuoteDTO.class
            );
            
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Error fetching quote data for symbol {}: {}", symbol, e.getMessage());
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded for Finnhub API. Please try again later.");
            }
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching quote data for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch company news from Finnhub API for a specific stock symbol
     * 
     * @param symbol Stock symbol (e.g., AAPL)
     * @param from Start date in format YYYY-MM-DD
     * @param to End date in format YYYY-MM-DD
     * @param count Optional limit on number of news items to return
     * @return List of company news items
     */
    public List<CompanyNewsDTO> fetchCompanyNews(String symbol, String from, String to, Integer count) {
        log.info("Fetching company news for symbol: {}, from: {}, to: {}, count: {}", symbol, from, to, count);
        
        try {
            // Build the Finnhub API URL
            String url = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host("finnhub.io")
                    .path("/api/v1/company-news")
                    .queryParam("symbol", symbol)
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .queryParam("token", finnhubApiConfig.getApiKey())
                    .toUriString();
            
            log.debug("Calling Finnhub API: {}", url);
            
            // Call Finnhub API
            ResponseEntity<List<CompanyNewsDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<CompanyNewsDTO>>() {}
            );
            
            List<CompanyNewsDTO> newsItems = response.getBody();
            
            if (newsItems == null || newsItems.isEmpty()) {
                log.warn("No news found for symbol: {} in date range {} to {}", symbol, from, to);
                return Collections.emptyList();
            }
            
            log.info("Fetched {} news items for {}", newsItems.size(), symbol);
            
            // Apply count limit if specified
            if (count != null && count > 0 && count < newsItems.size()) {
                return newsItems.subList(0, count);
            }
            
            return newsItems;
        } catch (HttpClientErrorException e) {
            log.error("Error fetching company news for symbol {}: {} - {}", 
                     symbol, e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded when calling Finnhub API");
            }
            
            throw e;
        } catch (Exception e) {
            log.error("Error fetching company news for symbol {}: ", symbol, e);
            throw e;
        }
    }

    /**
     * Fetch general market news from Finnhub API
     * 
     * @param from Start date in format YYYY-MM-DD
     * @param to End date in format YYYY-MM-DD
     * @param count Optional limit on number of news items to return
     * @return List of market news items
     */
    public List<CompanyNewsDTO> fetchMarketNews(String from, String to, Integer count) {
        log.info("Fetching market news from: {}, to: {}, count: {}", from, to, count);
        
        try {
            // Build the Finnhub API URL
            String url = UriComponentsBuilder.newInstance()
                    .scheme("https")
                    .host("finnhub.io")
                    .path("/api/v1/news")
                    .queryParam("category", "general")
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .queryParam("token", finnhubApiConfig.getApiKey())
                    .toUriString();
            
            log.debug("Calling Finnhub API: {}", url);
            
            // Call Finnhub API
            ResponseEntity<List<CompanyNewsDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<CompanyNewsDTO>>() {}
            );
            
            List<CompanyNewsDTO> newsItems = response.getBody();
            
            if (newsItems == null || newsItems.isEmpty()) {
                log.warn("No market news found in date range {} to {}", from, to);
                return Collections.emptyList();
            }
            
            log.info("Fetched {} market news items", newsItems.size());
            
            // Apply count limit if specified
            if (count != null && count > 0 && count < newsItems.size()) {
                return newsItems.subList(0, count);
            }
            
            return newsItems;
        } catch (HttpClientErrorException e) {
            log.error("Error fetching market news: {} - {}", 
                     e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded when calling Finnhub API");
            }
            
            throw e;
        } catch (Exception e) {
            log.error("Error fetching market news: ", e);
            throw e;
        }
    }

    /**
     * Fetch basic financial metrics for a specific stock symbol from Finnhub API
     * @param symbol the stock symbol (e.g., AAPL)
     * @return FinancialMetricsResult containing the status and data
     */
    @Transactional
    public FinancialMetricsResult fetchAndSaveBasicFinancials(String symbol) {
        log.info("Fetching basic financial metrics for symbol: {}", symbol);
        
        try {
            // Check if we already have metrics for this symbol today
            boolean alreadyExists = financialMetricsRepository.existsBySymbolAndCreatedAtDate(symbol, LocalDate.now());
            if (alreadyExists) {
                log.info("Financial metrics for symbol {} already exist for today, skipping", symbol);
                return FinancialMetricsResult.skipped(symbol);
            }
            
            // 먼저 회사 프로필 정보 가져오기 (marketCapitalization용)
            CompanyProfileDTO profileDTO = fetchCompanyProfile(symbol);
            
            FinancialMetricsDTO metricsDTO = fetchBasicFinancialsFromFinnhub(symbol);
            
            if (metricsDTO == null || metricsDTO.getMetric() == null) {
                log.warn("No financial metrics found for symbol: {}", symbol);
                return FinancialMetricsResult.noData(symbol);
            }
            
            FinancialMetrics financialMetrics = mapToFinancialMetricsEntity(symbol, metricsDTO);
            
            // marketCapitalization 설정
            if (profileDTO != null && profileDTO.getMarketCapitalization() != null) {
                financialMetrics.setMarketCapitalization(profileDTO.getMarketCapitalization());
                log.debug("Added market capitalization: {} for symbol: {}", 
                         profileDTO.getMarketCapitalization(), symbol);
            }
            
            FinancialMetrics savedMetrics = financialMetricsRepository.save(financialMetrics);
            return FinancialMetricsResult.success(savedMetrics);
            
        } catch (Exception e) {
            log.error("Error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
            return FinancialMetricsResult.error(symbol, e.getMessage());
        }
    }
    
    /**
     * Fetch basic financial metrics for all stock symbols in the database
     * @param batchSize the number of symbols to process in each batch
     * @param delayMs the delay between API calls in milliseconds
     * @return the number of symbols for which financial metrics were successfully fetched and saved
     */
    public int fetchAndSaveAllBasicFinancials(int batchSize, int delayMs) {
        log.info("Starting to fetch basic financial metrics for all symbols with batchSize={}, delayMs={}", 
                batchSize, delayMs);
        
        List<StockSymbol> allSymbols = stockSymbolRepository.findByProfileEmptyFalse();
        log.info("Found {} symbols with valid company profiles", allSymbols.size());
        
        int totalProcessed = 0;
        int totalSkipped = 0;
        int totalBatches = (int) Math.ceil((double) allSymbols.size() / batchSize);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, allSymbols.size());
            List<StockSymbol> batch = allSymbols.subList(startIndex, endIndex);
            
            log.info("Processing batch {}/{} with {} symbols", 
                    batchIndex + 1, totalBatches, batch.size());
            
            // 배치별로 처리하고 결과를 누적
            BatchResult result = processBatchWithNewTransaction(batch, delayMs);
            totalProcessed += result.getProcessedCount();
            totalSkipped += result.getSkippedCount();
            
            log.info("Completed batch {}/{}, batch successful: {}, batch skipped: {}, total successful: {}, total skipped: {}", 
                    batchIndex + 1, totalBatches, result.getProcessedCount(), result.getSkippedCount(), 
                    totalProcessed, totalSkipped);
        }
        
        log.info("Completed fetching financial metrics for all symbols. Total successful: {}/{}, Total skipped: {}", 
                totalProcessed, allSymbols.size(), totalSkipped);
        
        return totalProcessed;
    }
    
    /**
     * 배치 처리 결과를 담는 내부 클래스
     */
    private static class BatchResult {
        private final int processedCount;
        private final int skippedCount;
        
        public BatchResult(int processedCount, int skippedCount) {
            this.processedCount = processedCount;
            this.skippedCount = skippedCount;
        }
        
        public int getProcessedCount() {
            return processedCount;
        }
        
        public int getSkippedCount() {
            return skippedCount;
        }
    }
    
    /**
     * 새로운 트랜잭션에서 배치를 처리하는 메소드
     * 각 배치마다 별도의 트랜잭션을 사용하여 처리 완료 시 데이터베이스에 즉시 커밋
     * 
     * @param batch 처리할 심볼 배치
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 처리 결과 (성공 및 건너뛴 개수)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processBatchWithNewTransaction(List<StockSymbol> batch, int delayMs) {
        int batchProcessed = 0;
        int batchSkipped = 0;
        
        List<FinancialMetrics> metricsToSave = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (StockSymbol symbol : batch) {
            try {
                // Check if we already have metrics for this symbol today
                boolean alreadyExists = financialMetricsRepository.existsBySymbolAndCreatedAtDate(symbol.getSymbol(), today);
                if (alreadyExists) {
                    log.info("Financial metrics for symbol {} already exist for today, skipping", symbol.getSymbol());
                    batchSkipped++;
                    continue;
                }
                
                FinancialMetricsDTO metricsDTO = fetchBasicFinancialsFromFinnhub(symbol.getSymbol());
                
                if (metricsDTO != null && metricsDTO.getMetric() != null) {
                    FinancialMetrics metrics = mapToFinancialMetricsEntity(symbol.getSymbol(), metricsDTO);
                    
                    // 회사 프로필에서 marketCapitalization 정보 가져오기
                    CompanyProfileDTO profileDTO = fetchCompanyProfile(symbol.getSymbol());
                    if (profileDTO != null && profileDTO.getMarketCapitalization() != null) {
                        metrics.setMarketCapitalization(profileDTO.getMarketCapitalization());
                        log.debug("Added market capitalization: {} for symbol: {}", 
                                 profileDTO.getMarketCapitalization(), symbol.getSymbol());
                    }
                    
                    metricsToSave.add(metrics);
                    batchProcessed++;
                    log.debug("Successfully fetched financial metrics for symbol: {}", symbol.getSymbol());
                } else {
                    batchSkipped++;
                    log.warn("Skipped financial metrics for symbol (no data): {}", symbol.getSymbol());
                }
                
                // Delay between API calls to respect rate limits
                if (delayMs > 0 && batch.indexOf(symbol) < batch.size() - 1) {
                    Thread.sleep(delayMs);
                }
                
            } catch (Exception e) {
                log.error("Error processing financial metrics for symbol {}: {}", 
                        symbol.getSymbol(), e.getMessage());
                batchSkipped++;
                // Continue with the next symbol
            }
        }
        
        // 배치의 모든 데이터를 한번에 저장 (성능 향상)
        if (!metricsToSave.isEmpty()) {
            log.info("Saving batch of {} financial metrics to database", metricsToSave.size());
            financialMetricsRepository.saveAll(metricsToSave);
        }
        
        return new BatchResult(batchProcessed, batchSkipped);
    }
    
    /**
     * Fetch financial metrics from Finnhub API
     * @param symbol the stock symbol (e.g., AAPL)
     * @return FinancialMetricsDTO containing the response from Finnhub
     */
    private FinancialMetricsDTO fetchBasicFinancialsFromFinnhub(String symbol) {
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1/stock/metric")
                .queryParam("symbol", symbol)
                .queryParam("metric", "all")
                .queryParam("token", finnhubApiConfig.getApiKey())
                .toUriString();
        
        try {
            ResponseEntity<FinancialMetricsDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    FinancialMetricsDTO.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.debug("Successfully fetched financial metrics for symbol: {}", symbol);
                return response.getBody();
            } else {
                log.warn("Received non-OK response when fetching financial metrics for symbol {}: {}", 
                        symbol, response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit exceeded when fetching financial metrics for symbol: {}", symbol);
                throw new RuntimeException("Finnhub API rate limit exceeded. Please try again later.");
            } else {
                log.error("HTTP error fetching financial metrics for symbol {}: {}", symbol, e.getStatusCode());
                return null; // Return null to skip this symbol instead of throwing exception
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Handle timeout and connection errors
            log.warn("Timeout or connection error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
            return null; // Return null to skip this symbol
        } catch (Exception e) {
            log.error("Error fetching financial metrics for symbol {}: {}", symbol, e.getMessage());
            return null; // Return null to skip this symbol instead of throwing exception
        }
    }
    
    /**
     * Map the FinancialMetricsDTO to a FinancialMetrics entity
     * @param symbol the stock symbol
     * @param metricsDTO the DTO from Finnhub API
     * @return FinancialMetrics entity
     */
    private FinancialMetrics mapToFinancialMetricsEntity(String symbol, FinancialMetricsDTO metricsDTO) {
        Map<String, Object> metrics = metricsDTO.getMetric();
        
        FinancialMetrics entity = FinancialMetrics.builder()
                .symbol(symbol)
                .build();
        
        // Trading Volume Metrics
        if (metrics.containsKey("10DayAverageTradingVolume")) {
            entity.setTenDayAverageTradingVolume(getDoubleValue(metrics, "10DayAverageTradingVolume"));
        }
        if (metrics.containsKey("3MonthAverageTradingVolume")) {
            entity.setThreeMonthAverageTradingVolume(getDoubleValue(metrics, "3MonthAverageTradingVolume"));
        }
        
        // Price Return Metrics
        if (metrics.containsKey("13WeekPriceReturnDaily")) {
            entity.setThirteenWeekPriceReturnDaily(getDoubleValue(metrics, "13WeekPriceReturnDaily"));
        }
        if (metrics.containsKey("26WeekPriceReturnDaily")) {
            entity.setTwentySixWeekPriceReturnDaily(getDoubleValue(metrics, "26WeekPriceReturnDaily"));
        }
        if (metrics.containsKey("52WeekPriceReturnDaily")) {
            entity.setFiftyTwoWeekPriceReturnDaily(getDoubleValue(metrics, "52WeekPriceReturnDaily"));
        }
        if (metrics.containsKey("5DayPriceReturnDaily")) {
            entity.setFiveDayPriceReturnDaily(getDoubleValue(metrics, "5DayPriceReturnDaily"));
        }
        if (metrics.containsKey("yearToDatePriceReturnDaily")) {
            entity.setYearToDatePriceReturnDaily(getDoubleValue(metrics, "yearToDatePriceReturnDaily"));
        }
        if (metrics.containsKey("monthToDatePriceReturnDaily")) {
            entity.setMonthToDatePriceReturnDaily(getDoubleValue(metrics, "monthToDatePriceReturnDaily"));
        }
        
        // Highs and Lows
        if (metrics.containsKey("52WeekHigh")) {
            entity.setFiftyTwoWeekHigh(getDoubleValue(metrics, "52WeekHigh"));
        }
        if (metrics.containsKey("52WeekHighDate")) {
            entity.setFiftyTwoWeekHighDate((String) metrics.get("52WeekHighDate"));
        }
        if (metrics.containsKey("52WeekLow")) {
            entity.setFiftyTwoWeekLow(getDoubleValue(metrics, "52WeekLow"));
        }
        if (metrics.containsKey("52WeekLowDate")) {
            entity.setFiftyTwoWeekLowDate((String) metrics.get("52WeekLowDate"));
        }
        
        // Beta and Volatility
        if (metrics.containsKey("beta")) {
            entity.setBeta(getDoubleValue(metrics, "beta"));
        }
        if (metrics.containsKey("3MonthADReturnStd")) {
            entity.setThreeMonthADReturnStd(getDoubleValue(metrics, "3MonthADReturnStd"));
        }
        
        // Valuation Ratios
        if (metrics.containsKey("peTTM")) {
            entity.setPriceToEarningsRatio(getDoubleValue(metrics, "peTTM"));
        }
        if (metrics.containsKey("pbQuarterly")) {
            entity.setPriceToBookRatio(getDoubleValue(metrics, "pbQuarterly"));
        }
        if (metrics.containsKey("psTTM")) {
            entity.setPriceToSalesRatio(getDoubleValue(metrics, "psTTM"));
        }
        if (metrics.containsKey("pcfShareTTM")) {
            entity.setPriceToCashFlowRatio(getDoubleValue(metrics, "pcfShareTTM"));
        }
        
        // Financial Performance Metrics
        if (metrics.containsKey("roeTTM")) {
            entity.setReturnOnEquity(getDoubleValue(metrics, "roeTTM"));
        }
        if (metrics.containsKey("roaTTM")) {
            entity.setReturnOnAssets(getDoubleValue(metrics, "roaTTM"));
        }
        if (metrics.containsKey("roiTTM")) {
            entity.setReturnOnInvestment(getDoubleValue(metrics, "roiTTM"));
        }
        if (metrics.containsKey("grossMarginTTM")) {
            entity.setGrossMarginTTM(getDoubleValue(metrics, "grossMarginTTM"));
        }
        if (metrics.containsKey("operatingMarginTTM")) {
            entity.setOperatingMarginTTM(getDoubleValue(metrics, "operatingMarginTTM"));
        }
        if (metrics.containsKey("netProfitMarginTTM")) {
            entity.setNetProfitMarginTTM(getDoubleValue(metrics, "netProfitMarginTTM"));
        }
        
        // Debt Metrics
        if (metrics.containsKey("totalDebt/totalEquityQuarterly")) {
            entity.setTotalDebtToEquityQuarterly(getDoubleValue(metrics, "totalDebt/totalEquityQuarterly"));
        }
        if (metrics.containsKey("longTermDebt/equityQuarterly")) {
            entity.setLongTermDebtToEquityQuarterly(getDoubleValue(metrics, "longTermDebt/equityQuarterly"));
        }
        
        // Dividend Metrics
        if (metrics.containsKey("dividendPerShareAnnual")) {
            entity.setDividendPerShareAnnual(getDoubleValue(metrics, "dividendPerShareAnnual"));
        }
        if (metrics.containsKey("dividendYieldIndicatedAnnual")) {
            entity.setDividendYieldIndicatedAnnual(getDoubleValue(metrics, "dividendYieldIndicatedAnnual"));
        }
        if (metrics.containsKey("dividendGrowthRate5Y")) {
            entity.setDividendGrowthRate5Y(getDoubleValue(metrics, "dividendGrowthRate5Y"));
        }
        
        // Growth Metrics
        if (metrics.containsKey("revenueGrowth3Y")) {
            entity.setRevenueGrowth3Y(getDoubleValue(metrics, "revenueGrowth3Y"));
        }
        if (metrics.containsKey("revenueGrowth5Y")) {
            entity.setRevenueGrowth5Y(getDoubleValue(metrics, "revenueGrowth5Y"));
        }
        if (metrics.containsKey("epsGrowth3Y")) {
            entity.setEpsGrowth3Y(getDoubleValue(metrics, "epsGrowth3Y"));
        }
        if (metrics.containsKey("epsGrowth5Y")) {
            entity.setEpsGrowth5Y(getDoubleValue(metrics, "epsGrowth5Y"));
        }
        
        // Balance Sheet Metrics
        if (metrics.containsKey("bookValuePerShareAnnual")) {
            entity.setBookValuePerShareAnnual(getDoubleValue(metrics, "bookValuePerShareAnnual"));
        }
        if (metrics.containsKey("cashPerSharePerShareAnnual")) {
            entity.setCashPerSharePerShareAnnual(getDoubleValue(metrics, "cashPerSharePerShareAnnual"));
        }
        if (metrics.containsKey("currentRatioAnnual")) {
            entity.setCurrentRatioAnnual(getDoubleValue(metrics, "currentRatioAnnual"));
        }
        if (metrics.containsKey("quickRatioAnnual")) {
            entity.setQuickRatioAnnual(getDoubleValue(metrics, "quickRatioAnnual"));
        }
        
        return entity;
    }
    
    /**
     * Helper method to safely get a Double value from a Map
     * @param map the map containing the values
     * @param key the key to look up
     * @return the Double value, or null if not found or not a number
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Could not parse value for key {}: {}", key, value);
            return null;
        }
    }
} 