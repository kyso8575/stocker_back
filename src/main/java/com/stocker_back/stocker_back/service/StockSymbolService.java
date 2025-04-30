package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.config.FinnhubApiConfig;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.StockSymbolDTO;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockSymbolService {

    private final RestTemplate restTemplate;
    private final StockSymbolRepository stockSymbolRepository;
    private final FinnhubApiConfig finnhubApiConfig;
    
    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:50}")
    private int batchSize;
    
    /**
     * 특정 거래소의 주식 심볼 데이터를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param exchange 거래소 코드(예: US, KO 등)
     * @return 저장된 심볼 개수
     */
    @Transactional
    public int fetchAndSaveStockSymbols(String exchange) {
        log.info("Fetching stock symbols for exchange: {}", exchange);
        
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
        
        // DTO를 엔티티로 변환하고 배치 처리
        return saveBatch(symbolDTOs, exchange);
    }
    
    /**
     * 심볼 데이터를 배치 단위로 저장
     * @param symbolDTOs 심볼 DTO 리스트
     * @param exchange 거래소 코드
     * @return 저장된 심볼 개수
     */
    private int saveBatch(List<StockSymbolDTO> symbolDTOs, String exchange) {
        List<StockSymbol> symbolsToSave = new ArrayList<>();
        int totalSize = symbolDTOs.size();
        int processedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        
        for (StockSymbolDTO dto : symbolDTOs) {
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
            processedCount++;
            
            // 배치 크기에 도달하거나 마지막 항목인 경우 저장
            if (symbolsToSave.size() >= batchSize || processedCount == totalSize) {
                stockSymbolRepository.saveAll(symbolsToSave);
                
                // 진행 상황 기록
                int progressPercent = (processedCount * 100) / totalSize;
                log.info("Saved batch of symbols: {} of {} ({}%)", 
                        processedCount, totalSize, progressPercent);
                
                symbolsToSave.clear();
            }
        }
        
        return processedCount;
    }
} 