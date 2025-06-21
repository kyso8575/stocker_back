package com.stocker_back.stocker_back.scheduler;

import com.stocker_back.stocker_back.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 주식 시세(Quote) 자동 수집 스케줄러 서비스
 * 
 * 수집 전략:
 * - 평일: 매일 시장 마감 후 (4:30 PM ET) S&P 500 종가 데이터 수집
 * - 주말: 매일 오후 (4:30 PM ET) S&P 500 데이터 수집 (금요일 종가 유지)
 * - 수동 트리거 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteSchedulerService {

    private final QuoteService quoteService;
    
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    
    /**
     * 매일 S&P 500 데이터 수집 (4:30 PM ET)
     * - 평일: 시장 마감 후 종가 데이터
     * - 주말: 금요일 종가 유지 데이터 (글로벌 시장 영향 확인)
     */
    @Scheduled(cron = "0 30 16 * * *", zone = "America/New_York")
    public void collectDailyQuotes() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        
        String collectionType = (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) 
                ? "weekend" : "weekday";
        
        log.info("🌙 Starting daily S&P 500 quote collection at {} ({})", 
                now.format(FORMATTER), collectionType);
        
        try {
            int processedCount = quoteService.fetchAndSaveSp500Quotes(20, 1000);
            log.info("✅ Daily S&P 500 quote collection completed: {} symbols processed ({})", 
                    processedCount, collectionType);
        } catch (Exception e) {
            log.error("❌ Daily S&P 500 quote collection failed ({}): {}", collectionType, e.getMessage(), e);
        }
    }
    
    /**
     * 수동 트리거용 메서드 - S&P 500 종가 수집
     * @return 처리된 심볼 수
     */
    public int triggerManualSp500QuoteCollection() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        log.info("🔧 Manual S&P 500 quote collection triggered at {}", now.format(FORMATTER));
        
        try {
            int processedCount = quoteService.fetchAndSaveSp500Quotes(20, 1000);
            log.info("✅ Manual S&P 500 quote collection completed: {} symbols processed", processedCount);
            return processedCount;
        } catch (Exception e) {
            log.error("❌ Manual S&P 500 quote collection failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 현재 미국 동부 시간을 반환합니다.
     * @return 현재 시간 (America/New_York 시간대)
     */
    public String getCurrentEasternTime() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        return now.format(FORMATTER);
    }
    
    /**
     * 다음 스케줄 실행 시간 정보를 반환합니다.
     * @return 다음 실행 시간 정보
     */
    public String getNextScheduleInfo() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        DayOfWeek currentDay = now.getDayOfWeek();
        
        // 오늘 4:30 PM
        ZonedDateTime todayClose = now.withHour(16).withMinute(30).withSecond(0).withNano(0);
        
        if (now.isBefore(todayClose)) {
            String dayType = (currentDay == DayOfWeek.SATURDAY || currentDay == DayOfWeek.SUNDAY) 
                    ? "weekend" : "weekday";
            return String.format("Next S&P 500 quote collection: %s (%s)", 
                    todayClose.format(FORMATTER), dayType);
        } else {
            // 오늘 4:30 PM 이후면 다음 날 4:30 PM
            ZonedDateTime nextRun = now.plusDays(1).withHour(16).withMinute(30).withSecond(0).withNano(0);
            String dayType = (nextRun.getDayOfWeek() == DayOfWeek.SATURDAY || nextRun.getDayOfWeek() == DayOfWeek.SUNDAY) 
                    ? "weekend" : "weekday";
            return String.format("Next S&P 500 quote collection: %s (%s)", 
                    nextRun.format(FORMATTER), dayType);
        }
    }
    
    /**
     * Quote 수집 결과 DTO
     */
    public static class QuoteCollectionResult {
        private final int symbolsProcessed;
        private final int symbolsSkipped;
        private final String collectionType;
        private final ZonedDateTime collectionTime;
        
        public QuoteCollectionResult(int symbolsProcessed, int symbolsSkipped, String collectionType) {
            this.symbolsProcessed = symbolsProcessed;
            this.symbolsSkipped = symbolsSkipped;
            this.collectionType = collectionType;
            this.collectionTime = ZonedDateTime.now(US_EASTERN);
        }
        
        // Getters
        public int getSymbolsProcessed() { return symbolsProcessed; }
        public int getSymbolsSkipped() { return symbolsSkipped; }
        public String getCollectionType() { return collectionType; }
        public ZonedDateTime getCollectionTime() { return collectionTime; }
    }
} 