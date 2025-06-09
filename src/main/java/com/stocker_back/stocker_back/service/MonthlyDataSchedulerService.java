package com.stocker_back.stocker_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 월간 데이터 수집 스케줄러 서비스
 * 매월 1일과 15일에 S&P 500 목록 업데이트와 회사 프로필 수집을 자동으로 실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyDataSchedulerService {

    private final Sp500ScraperService sp500ScraperService;
    private final CompanyProfileService companyProfileService;

    private static final ZoneId EASTERN_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    /**
     * 매월 1일과 15일 오전 8:00 AM ET에 S&P 500 관련 데이터 자동 수집
     * - S&P 500 목록 업데이트 (웹 스크래핑)
     * - S&P 500 회사 프로필 수집
     */
    @Scheduled(cron = "0 0 8 1,15 * ?", zone = "America/New_York")
    public void executeMonthlyDataCollection() {
        String currentTime = getCurrentEasternTime();
        log.info("=== MONTHLY DATA COLLECTION STARTED at {} ===", currentTime);
        
        try {
            // 1. S&P 500 목록 업데이트
            log.info("Step 1: Updating S&P 500 symbols list...");
            Set<String> updatedSymbols = sp500ScraperService.updateSp500List();
            log.info("Successfully updated S&P 500 list with {} symbols", updatedSymbols.size());
            
            // 2. S&P 500 회사 프로필 수집 (배치 크기 20, rate limit는 API 클라이언트에서 처리)
            log.info("Step 2: Collecting S&P 500 company profiles...");
            int profilesProcessed = companyProfileService.fetchAndSaveSp500CompanyProfiles(20, 0);
            log.info("Successfully processed {} S&P 500 company profiles", profilesProcessed);
            
            String completionTime = getCurrentEasternTime();
            log.info("=== MONTHLY DATA COLLECTION COMPLETED at {} ===", completionTime);
            log.info("Summary: {} symbols updated, {} profiles processed", updatedSymbols.size(), profilesProcessed);
            
        } catch (Exception e) {
            log.error("Error during monthly data collection: {}", e.getMessage(), e);
            throw new RuntimeException("Monthly data collection failed", e);
        }
    }

    /**
     * 수동으로 월간 데이터 수집 실행
     * @return 처리된 심볼 수와 프로필 수
     */
    public MonthlyDataResult triggerManualCollection() {
        log.info("Manual monthly data collection triggered");
        
        try {
            // S&P 500 목록 업데이트
            Set<String> updatedSymbols = sp500ScraperService.updateSp500List();
            
            // S&P 500 회사 프로필 수집
            int profilesProcessed = companyProfileService.fetchAndSaveSp500CompanyProfiles(20, 0);
            
            log.info("Manual monthly data collection completed: {} symbols, {} profiles", 
                    updatedSymbols.size(), profilesProcessed);
            
            return new MonthlyDataResult(updatedSymbols.size(), profilesProcessed, updatedSymbols);
            
        } catch (Exception e) {
            log.error("Error during manual monthly data collection: {}", e.getMessage(), e);
            throw new RuntimeException("Manual monthly data collection failed", e);
        }
    }

    /**
     * 현재 동부 시간 조회
     * @return 포맷된 동부 시간 문자열
     */
    public String getCurrentEasternTime() {
        ZonedDateTime easternTime = ZonedDateTime.now(EASTERN_ZONE);
        return easternTime.format(FORMATTER);
    }

    /**
     * 다음 스케줄 정보 조회
     * @return 다음 실행 예정 시간 정보
     */
    public String getNextScheduleInfo() {
        ZonedDateTime now = ZonedDateTime.now(EASTERN_ZONE);
        ZonedDateTime nextRun;
        
        // 현재 날짜 기준으로 다음 실행일 계산
        int currentDay = now.getDayOfMonth();
        
        if (currentDay < 1) {
            // 이번 달 1일
            nextRun = now.withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        } else if (currentDay < 15) {
            // 이번 달 15일
            nextRun = now.withDayOfMonth(15).withHour(8).withMinute(0).withSecond(0).withNano(0);
        } else {
            // 다음 달 1일
            nextRun = now.plusMonths(1).withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        }
        
        // 만약 계산된 시간이 현재보다 이전이면 다음 스케줄로 이동
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            if (currentDay >= 15) {
                // 다음 달 1일
                nextRun = now.plusMonths(1).withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
            } else {
                // 이번 달 15일
                nextRun = now.withDayOfMonth(15).withHour(8).withMinute(0).withSecond(0).withNano(0);
            }
        }
        
        return String.format("Next execution: %s", nextRun.format(FORMATTER));
    }

    /**
     * 월간 데이터 수집 결과 DTO
     */
    public static class MonthlyDataResult {
        private final int symbolsUpdated;
        private final int profilesProcessed;
        private final Set<String> updatedSymbols;
        
        public MonthlyDataResult(int symbolsUpdated, int profilesProcessed, Set<String> updatedSymbols) {
            this.symbolsUpdated = symbolsUpdated;
            this.profilesProcessed = profilesProcessed;
            this.updatedSymbols = updatedSymbols;
        }
        
        public int getSymbolsUpdated() { return symbolsUpdated; }
        public int getProfilesProcessed() { return profilesProcessed; }
        public Set<String> getUpdatedSymbols() { return updatedSymbols; }
    }
} 