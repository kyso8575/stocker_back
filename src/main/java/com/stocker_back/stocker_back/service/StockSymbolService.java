package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.StockSymbolDTO;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.util.FinnhubApiClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * 주식 심볼 관리를 담당하는 서비스
 */
@Service
@Slf4j
public class StockSymbolService {

    private final StockSymbolRepository stockSymbolRepository;
    private final FinnhubApiClient finnhubApiClient;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:200}")
    private int batchSize;
    
    @Value("${app.parallel.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${app.parallel.max-threads:8}")
    private int maxThreads;
    
    public StockSymbolService(
        StockSymbolRepository stockSymbolRepository,
        FinnhubApiClient finnhubApiClient) {
        this.stockSymbolRepository = stockSymbolRepository;
        this.finnhubApiClient = finnhubApiClient;
    }
    
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
        // Finnhub API 호출
        List<StockSymbolDTO> symbolDTOs = finnhubApiClient.get(
                "/stock/symbol",
                new ParameterizedTypeReference<List<StockSymbolDTO>>() {},
                "exchange", exchange
        );
        
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
        List<StockSymbolDTO> allSymbols = finnhubApiClient.get(
                "/stock/symbol",
                new ParameterizedTypeReference<List<StockSymbolDTO>>() {},
                "exchange", exchange
        );
        
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
     * S&P 500에 포함된 모든 심볼을 반환합니다.
     */
    public Set<String> findAllSp500Symbols() {
        return stockSymbolRepository.findAllSp500Symbols();
    }
    
    /**
     * 특정 심볼이 이미 존재하는지 확인
     * @param symbol 확인할 심볼
     * @return 존재 여부
     */
    public boolean symbolExists(String symbol) {
        return stockSymbolRepository.existsBySymbol(symbol.toUpperCase());
    }
    
    /**
     * 특정 심볼 조회
     * @param symbol 조회할 심볼
     * @return StockSymbol Optional
     */
    public java.util.Optional<StockSymbol> findBySymbol(String symbol) {
        return stockSymbolRepository.findBySymbol(symbol.toUpperCase());
    }
} 