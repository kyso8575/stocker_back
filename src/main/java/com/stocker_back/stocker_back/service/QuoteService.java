package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.QuoteDTO;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.util.FinnhubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private final FinnhubApiClient finnhubApiClient;
    private final QuoteRepository quoteRepository;
    private final StockSymbolRepository stockSymbolRepository;

    /**
     * 특정 심볼의 시세 데이터를 가져와서 저장합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 저장된 Quote 엔티티
     */
    @Transactional
    public Quote fetchAndSaveQuote(String symbol) {
        log.info("Fetching quote for symbol: {}", symbol);
        
        try {
            // Finnhub API에서 시세 데이터 가져오기
            QuoteDTO quoteDTO = finnhubApiClient.getQuote(symbol);
            
            if (quoteDTO == null || quoteDTO.getCurrentPrice() == null) {
                log.warn("No quote data received for symbol: {}", symbol);
                return null;
            }
            
            // Quote 엔티티로 변환
            Quote quote = mapToQuoteEntity(symbol, quoteDTO);
            
            // 데이터베이스에 저장
            Quote savedQuote = quoteRepository.save(quote);
            
            log.info("Successfully saved quote for symbol: {} - Current Price: {}", 
                    symbol, savedQuote.getCurrentPrice());
            
            return savedQuote;
            
        } catch (Exception e) {
            log.error("Error fetching quote for symbol {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to fetch quote for " + symbol + ": " + e.getMessage(), e);
        }
    }

    /**
     * S&P 500에 포함된 모든 심볼의 시세 데이터를 가져와서 저장합니다.
     * 배치 단위로 처리하여 각 배치마다 개별적으로 저장합니다.
     * @param batchSize 배치 크기
     * @param delayMs 요청 간 지연 시간 (밀리초)
     * @return 처리된 심볼 수
     */
    public int fetchAndSaveSp500Quotes(int batchSize, int delayMs) {
        log.info("Fetching quotes for S&P 500 symbols with batchSize={}, delayMs={}", batchSize, delayMs);
        
        List<StockSymbol> sp500Symbols = stockSymbolRepository.findByIsSp500True();
        log.info("Found {} S&P 500 symbols", sp500Symbols.size());
        
        int totalProcessed = 0;
        int totalSuccess = 0;
        int batchNumber = 0;
        
        // 배치 단위로 처리
        for (int i = 0; i < sp500Symbols.size(); i += batchSize) {
            batchNumber++;
            int endIndex = Math.min(i + batchSize, sp500Symbols.size());
            List<StockSymbol> batch = sp500Symbols.subList(i, endIndex);
            
            log.info("Processing batch {}/{}: symbols {} to {} ({} symbols)", 
                    batchNumber, (sp500Symbols.size() + batchSize - 1) / batchSize,
                    batch.get(0).getSymbol(), batch.get(batch.size() - 1).getSymbol(), batch.size());
            
            // 배치 단위로 트랜잭션 처리
            int batchSuccess = processBatch(batch);
            totalSuccess += batchSuccess;
            totalProcessed += batch.size();
            
            log.info("Batch {} completed: {}/{} successful (Total: {}/{})", 
                    batchNumber, batchSuccess, batch.size(), totalSuccess, totalProcessed);
            
            // 마지막 배치가 아니면 지연
            if (endIndex < sp500Symbols.size() && delayMs > 0) {
                try {
                    log.info("Taking a break for {}ms before next batch...", delayMs);
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted during batch processing delay");
                    break;
                }
            }
        }
        
        log.info("Quote collection completed. Total: {}/{}, Success Rate: {:.1f}%", 
                totalSuccess, totalProcessed, (double) totalSuccess / totalProcessed * 100);
        return totalSuccess;
    }

    /**
     * 배치 단위로 quote 데이터를 처리합니다.
     * @param batch 처리할 심볼 배치
     * @return 성공한 심볼 수
     */
    @Transactional
    private int processBatch(List<StockSymbol> batch) {
        int batchSuccess = 0;
        
        for (StockSymbol stockSymbol : batch) {
            try {
                Quote savedQuote = fetchAndSaveQuote(stockSymbol.getSymbol());
                if (savedQuote != null) {
                    batchSuccess++;
                }
            } catch (Exception e) {
                log.error("Error processing symbol {} in batch: {}", stockSymbol.getSymbol(), e.getMessage());
            }
        }
        
        return batchSuccess;
    }

    /**
     * QuoteDTO를 Quote 엔티티로 변환합니다.
     */
    private Quote mapToQuoteEntity(String symbol, QuoteDTO quoteDTO) {
        return Quote.builder()
                .symbol(symbol.toUpperCase())
                .currentPrice(quoteDTO.getCurrentPrice())
                .openPrice(quoteDTO.getOpenPrice())
                .highPrice(quoteDTO.getHighPrice())
                .lowPrice(quoteDTO.getLowPrice())
                .previousClosePrice(quoteDTO.getPreviousClosePrice())
                .volume(BigDecimal.ZERO.longValue()) // Finnhub quote API는 volume을 제공하지 않음
                .timestamp(quoteDTO.getTimestamp())
                .build();
    }

    /**
     * 특정 심볼의 최신 시세를 조회합니다.
     * @param symbol 주식 심볼
     * @return 최신 시세 데이터
     */
    public Optional<Quote> getLatestQuote(String symbol) {
        return quoteRepository.findLatestQuoteBySymbol(symbol.toUpperCase());
    }

    /**
     * S&P 500의 오늘 시세 데이터를 조회합니다.
     * @return S&P 500 시세 데이터 목록
     */
    public List<Quote> getSp500QuotesToday() {
        return quoteRepository.findSp500QuotesByDate(java.time.LocalDateTime.now());
    }
} 