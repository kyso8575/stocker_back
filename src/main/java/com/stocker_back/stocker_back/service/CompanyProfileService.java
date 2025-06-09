package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.CompanyProfileDTO;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.util.FinnhubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 회사 프로필 정보 관리를 담당하는 서비스
 */
@Service
@Slf4j
public class CompanyProfileService {

    private final FinnhubApiClient finnhubApiClient;
    private final StockSymbolRepository stockSymbolRepository;
    
    public CompanyProfileService(
        FinnhubApiClient finnhubApiClient,
        StockSymbolRepository stockSymbolRepository) {
        this.finnhubApiClient = finnhubApiClient;
        this.stockSymbolRepository = stockSymbolRepository;
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
                symbol.setLastProfileUpdated(LocalDateTime.now());
                
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
                    symbol.setLastProfileUpdated(LocalDateTime.now());
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
    public CompanyProfileDTO fetchCompanyProfile(String symbol) {
        return finnhubApiClient.get(
                "/stock/profile2",
                CompanyProfileDTO.class,
                "symbol", symbol
        );
    }
    
    /**
     * 상세한 결과와 함께 회사 프로필 정보를 가져옵니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return API 결과 (성공/실패/rate limit 등 구분)
     */
    public FinnhubApiClient.ApiResult<CompanyProfileDTO> fetchCompanyProfileWithResult(String symbol) {
        return finnhubApiClient.getWithResult(
                "/stock/profile2",
                CompanyProfileDTO.class,
                "symbol", symbol
        );
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
        symbol.setLastProfileUpdated(LocalDateTime.now());
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
        stockSymbol.setLastProfileUpdated(LocalDateTime.now());
        
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
     * S&P 500 종목들에 대한 회사 프로필 정보를 가져와 저장합니다.
     * @param batchSize 한 번에 처리할 주식 수
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 업데이트된 회사 프로필 수
     */
    public int fetchAndSaveSp500CompanyProfiles(int batchSize, int delayMs) {
        log.info("🚀 Starting S&P 500 company profile collection (batch: {}, delay: {}ms)", batchSize, delayMs);
        
        // 배치 크기가 지정되지 않았거나 너무 작은 경우 기본값 설정
        if (batchSize <= 0) {
            batchSize = 100;
        }
        
        // S&P 500 종목 전체 조회
        List<StockSymbol> sp500Symbols = stockSymbolRepository.findByIsSp500True();
        log.info("📊 Found {} S&P 500 symbols to process", sp500Symbols.size());
        
        int successCount = 0;
        int noDataCount = 0;
        int rateLimitCount = 0;
        int errorCount = 0;
        int totalProcessed = 0;
        
        // 배치 처리를 위한 리스트
        List<StockSymbol> batch = new ArrayList<>(batchSize);
        
        for (int i = 0; i < sp500Symbols.size(); i++) {
            StockSymbol symbol = sp500Symbols.get(i);
            totalProcessed++;
            
            try {
                // 상세한 결과와 함께 회사 프로필 정보 가져오기
                FinnhubApiClient.ApiResult<CompanyProfileDTO> result = fetchCompanyProfileWithResult(symbol.getSymbol());
                symbol.setLastProfileUpdated(LocalDateTime.now());
                
                if (result.isSuccess()) {
                    // 성공: 프로필 정보 업데이트
                    updateCompanyProfile(symbol, result.getData());
                    symbol.setProfileEmpty(false);
                    batch.add(symbol);
                    successCount++;
                    
                    log.debug("✅ {} - Company profile updated", symbol.getSymbol());
                } else if (result.isNoData()) {
                    // 데이터 없음
                    symbol.setProfileEmpty(true);
                    batch.add(symbol);
                    noDataCount++;
                    
                    log.debug("📭 {} - No profile data available", symbol.getSymbol());
                } else if (result.isRateLimitExceeded()) {
                    // Rate limit 초과
                    symbol.setProfileEmpty(true);
                    batch.add(symbol);
                    rateLimitCount++;
                    
                    log.warn("⏳ {} - Rate limit exceeded after all retries", symbol.getSymbol());
                } else {
                    // 기타 에러
                    symbol.setProfileEmpty(true);
                    batch.add(symbol);
                    errorCount++;
                    
                    log.warn("❌ {} - API error: {}", symbol.getSymbol(), result.getMessage());
                }
                
                // 배치 크기에 도달하면 저장
                if (batch.size() >= batchSize) {
                    saveCompanyProfileBatch(batch);
                    log.info("💾 Saved batch: {} profiles | Progress: {}/{} ({}%) - ✅{} 📭{} ⏳{} ❌{}", 
                            batch.size(), totalProcessed, sp500Symbols.size(),
                            String.format("%.1f", (totalProcessed * 100.0) / sp500Symbols.size()),
                            successCount, noDataCount, rateLimitCount, errorCount);
                    batch.clear();
                }
                
                // 진행 상황 로깅 (매 25개마다)
                if (totalProcessed % 25 == 0 || totalProcessed == sp500Symbols.size()) {
                    double progressPct = (totalProcessed * 100.0) / sp500Symbols.size();
                    log.info("📊 Processing: {}/{} ({}%) - ✅{} 📭{} ⏳{} ❌{}", 
                             totalProcessed, sp500Symbols.size(), String.format("%.1f", progressPct),
                             successCount, noDataCount, rateLimitCount, errorCount);
                }
                
                // API 레이트 제한 방지를 위한 지연
                if (i < sp500Symbols.size() - 1 && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException e) {
                log.error("🛑 Thread interrupted during S&P 500 company profile collection", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("🔥 Unexpected error processing symbol {}: {}", symbol.getSymbol(), e.getMessage());
                
                // 예외 발생 시에도 빈 프로필로 표시하고 배치에 추가
                try {
                    symbol.setProfileEmpty(true);
                    symbol.setLastProfileUpdated(LocalDateTime.now());
                    batch.add(symbol);
                    errorCount++;
                    
                    // 배치 크기에 도달하면 저장
                    if (batch.size() >= batchSize) {
                        saveCompanyProfileBatch(batch);
                        log.debug("💾 Saved batch of {} profiles (processed: {}/{})", 
                                batch.size(), totalProcessed, sp500Symbols.size());
                        batch.clear();
                    }
                } catch (Exception ex) {
                    log.error("💥 Critical error updating profile status for symbol {}: {}", 
                            symbol.getSymbol(), ex.getMessage());
                }
            }
        }
        
        // 남은 배치 저장
        if (!batch.isEmpty()) {
            saveCompanyProfileBatch(batch);
            log.debug("💾 Saved final batch of {} profiles", batch.size());
        }
        
        log.info("🎯 S&P 500 company profile collection completed:");
        log.info("   ✅ Success: {} profiles", successCount);
        log.info("   📭 No data: {} profiles", noDataCount);
        log.info("   ⏳ Rate limited: {} profiles", rateLimitCount);
        log.info("   ❌ Errors: {} profiles", errorCount);
        log.info("   📊 Total processed: {}/{}", totalProcessed, sp500Symbols.size());
        
        return totalProcessed;
    }
} 