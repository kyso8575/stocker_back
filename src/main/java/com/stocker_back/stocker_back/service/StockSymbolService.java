package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.config.FinnhubApiConfig;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.StockSymbolDTO;
import com.stocker_back.stocker_back.dto.CompanyProfileDTO;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockSymbolService {

    private final RestTemplate restTemplate;
    private final StockSymbolRepository stockSymbolRepository;
    private final FinnhubApiConfig finnhubApiConfig;
    
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
        symbol.setMarketCapitalization(profileDTO.getMarketCapitalization());
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
} 