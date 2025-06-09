package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.CompanyProfileDTO;
import com.stocker_back.stocker_back.dto.FinancialMetricsDTO;
import com.stocker_back.stocker_back.dto.FinancialMetricsResult;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.util.FinnhubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 재무 지표 관련 기능을 담당하는 서비스
 */
@Service
@Slf4j
public class FinancialMetricsService {

    private final FinancialMetricsRepository financialMetricsRepository;
    private final StockSymbolRepository stockSymbolRepository;
    private final FinnhubApiClient finnhubApiClient;
    private final CompanyProfileService companyProfileService;
    
    public FinancialMetricsService(
        FinancialMetricsRepository financialMetricsRepository,
        StockSymbolRepository stockSymbolRepository,
        FinnhubApiClient finnhubApiClient,
        CompanyProfileService companyProfileService) {
        this.financialMetricsRepository = financialMetricsRepository;
        this.stockSymbolRepository = stockSymbolRepository;
        this.finnhubApiClient = finnhubApiClient;
        this.companyProfileService = companyProfileService;
    }
    
    /**
     * 특정 주식 심볼에 대한 기본 재무 지표를 Finnhub API에서 가져와 저장
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 성공 여부와 데이터를 담은 결과 객체
     */
    @Transactional
    public FinancialMetricsResult fetchAndSaveBasicFinancials(String symbol) {
        log.info("Fetching basic financial metrics for symbol: {}", symbol);
        
        try {
            // 오늘 날짜에 이미 지표가 있는지 확인
            boolean alreadyExists = financialMetricsRepository.existsBySymbolAndCreatedAtDate(symbol, LocalDate.now());
            if (alreadyExists) {
                log.info("Financial metrics for symbol {} already exist for today, skipping", symbol);
                return FinancialMetricsResult.skipped(symbol);
            }
            
            // 먼저 회사 프로필 정보 가져오기 (marketCapitalization용)
            CompanyProfileDTO profileDTO = companyProfileService.fetchCompanyProfile(symbol);
            
            // 재무 지표 가져오기
            FinancialMetricsDTO metricsDTO = fetchBasicFinancials(symbol);
            
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
     * 모든 주식 심볼에 대한 기본 재무 지표를 가져와 저장
     * @param batchSize 배치 크기
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 성공적으로 저장된 심볼 수
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
     * S&P 500 종목들에 대한 기본 재무 지표를 가져와 저장
     * @param batchSize 배치 크기
     * @param delayMs API 호출 사이의 지연 시간(밀리초)
     * @return 성공적으로 저장된 심볼 수
     */
    public int fetchAndSaveSp500BasicFinancials(int batchSize, int delayMs) {
        log.info("💰 Starting S&P 500 financial metrics collection (batch: {}, delay: {}ms)", batchSize, delayMs);
        
        // S&P 500 종목들 전체 조회 (프로필 유효성 체크 제거)
        List<StockSymbol> sp500Symbols = stockSymbolRepository.findByIsSp500True();
        log.info("📊 Found {} S&P 500 symbols to process", sp500Symbols.size());
        
        int totalProcessed = 0;
        int totalSkipped = 0;
        int totalBatches = (int) Math.ceil((double) sp500Symbols.size() / batchSize);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, sp500Symbols.size());
            List<StockSymbol> batch = sp500Symbols.subList(startIndex, endIndex);
            
            log.info("🔄 Processing batch {}/{} ({} symbols)...", 
                     batchIndex + 1, totalBatches, batch.size());
            
            // 배치별로 처리하고 결과를 누적
            BatchResult result = processBatchWithNewTransaction(batch, delayMs);
            totalProcessed += result.getProcessedCount();
            totalSkipped += result.getSkippedCount();
            
            log.info("💾 Batch {}/{} completed: ✅{} 📭{} | Total: ✅{} 📭{} ({}/{})", 
                     batchIndex + 1, totalBatches, 
                     result.getProcessedCount(), result.getSkippedCount(),
                     totalProcessed, totalSkipped,
                     totalProcessed + totalSkipped, sp500Symbols.size());
        }
        
        log.info("🎯 S&P 500 financial metrics collection completed:");
        log.info("   ✅ Success: {} metrics", totalProcessed);
        log.info("   📭 Skipped: {} symbols", totalSkipped);
        log.info("   📊 Total processed: {}/{}", totalProcessed + totalSkipped, sp500Symbols.size());
        
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
        
        log.debug("🔍 Starting batch processing for {} symbols", batch.size());
        
        for (int i = 0; i < batch.size(); i++) {
            StockSymbol symbol = batch.get(i);
            
            try {
                // 오늘 날짜에 이미 지표가 있는지 확인
                boolean alreadyExists = financialMetricsRepository.existsBySymbolAndCreatedAtDate(symbol.getSymbol(), today);
                if (alreadyExists) {
                    batchSkipped++;
                    continue;
                }
                
                // 재무 지표 가져오기
                FinancialMetricsDTO metricsDTO = fetchBasicFinancials(symbol.getSymbol());
                
                if (metricsDTO != null && metricsDTO.getMetric() != null) {
                    FinancialMetrics metrics = mapToFinancialMetricsEntity(symbol.getSymbol(), metricsDTO);
                    
                    // 회사 프로필에서 marketCapitalization 정보 가져오기
                    CompanyProfileDTO profileDTO = companyProfileService.fetchCompanyProfile(symbol.getSymbol());
                    if (profileDTO != null && profileDTO.getMarketCapitalization() != null) {
                        metrics.setMarketCapitalization(profileDTO.getMarketCapitalization());
                    }
                    
                    metricsToSave.add(metrics);
                    batchProcessed++;
                } else {
                    batchSkipped++;
                }
                
                // 진행 상황 중간 보고 (배치 내에서 매 10개마다)
                if ((i + 1) % 10 == 0 || (i + 1) == batch.size()) {
                    log.debug("   📊 Batch progress: {}/{} symbols processed", i + 1, batch.size());
                }
                
                // API 레이트 제한 방지를 위한 지연
                if (delayMs > 0 && i < batch.size() - 1) {
                    Thread.sleep(delayMs);
                }
                
            } catch (Exception e) {
                log.warn("❌ Error processing {}: {}", symbol.getSymbol(), e.getMessage());
                batchSkipped++;
                // 다음 심볼 계속 처리
            }
        }
        
        // 배치의 모든 데이터를 한번에 저장 (성능 향상)
        if (!metricsToSave.isEmpty()) {
            log.debug("💾 Saving {} financial metrics to database...", metricsToSave.size());
            financialMetricsRepository.saveAll(metricsToSave);
            log.debug("✅ Successfully saved {} metrics", metricsToSave.size());
        }
        
        return new BatchResult(batchProcessed, batchSkipped);
    }
    
    /**
     * Finnhub API에서 재무 지표 데이터를 가져옴
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 재무 지표 DTO
     */
    private FinancialMetricsDTO fetchBasicFinancials(String symbol) {
        return finnhubApiClient.get(
                "/stock/metric",
                FinancialMetricsDTO.class,
                "symbol", symbol,
                "metric", "all"
        );
    }
    
    /**
     * FinancialMetricsDTO를 FinancialMetrics 엔티티로 변환
     * @param symbol 주식 심볼
     * @param metricsDTO Finnhub API에서 가져온 DTO
     * @return FinancialMetrics 엔티티
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
     * Map에서 안전하게 Double 값을 가져오는 헬퍼 메소드
     * @param map 값을 포함하는 맵
     * @param key 찾을 키
     * @return Double 값 또는 존재하지 않거나 숫자가 아닌 경우 null
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