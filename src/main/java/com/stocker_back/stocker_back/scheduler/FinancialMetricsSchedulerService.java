package com.stocker_back.stocker_back.scheduler;

import com.stocker_back.stocker_back.service.FinancialMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 재무 지표 자동 수집 스케줄러 서비스
 * 미국 시장 개장 30분 전 (9:00 AM ET)에 S&P 500 재무 지표를 수집합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinancialMetricsSchedulerService {

    private final FinancialMetricsService financialMetricsService;
    
    // 미국 동부 시간대
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    
    /**
     * 미국 시장 개장 30분 전 (9:00 AM ET) 자동 수집
     * 월~금요일에만 실행 (미국 거래일)
     * 
     * Cron 표현식: "0 0 9 * * MON-FRI"
     * - 초: 0
     * - 분: 0  
     * - 시: 9 (9 AM)
     * - 일: * (매일)
     * - 월: * (매월)
     * - 요일: MON-FRI (월~금)
     * 
     * 시간대: America/New_York (EST/EDT 자동 적용)
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "America/New_York")
    public void scheduleDailyFinancialMetricsCollection() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        log.info("🕘 Starting scheduled S&P 500 financial metrics collection at {}", 
                now.format(FORMATTER));
        
        try {
            // S&P 500 재무 지표 수집 (배치 크기 20, API 클라이언트에서 rate limit 적용)
            int processedCount = financialMetricsService.fetchAndSaveSp500BasicFinancials(20, 0);
            
            ZonedDateTime completed = ZonedDateTime.now(US_EASTERN);
            log.info("✅ Scheduled financial metrics collection completed at {}", 
                    completed.format(FORMATTER));
            log.info("📊 Summary: {} S&P 500 symbols processed", processedCount);
            
        } catch (Exception e) {
            ZonedDateTime failed = ZonedDateTime.now(US_EASTERN);
            log.error("❌ Scheduled financial metrics collection failed at {}: {}", 
                    failed.format(FORMATTER), e.getMessage(), e);
        }
    }
    
    /**
     * 테스트용 스케줄러 (매 10분마다 실행) - 운영 환경에서는 비활성화
     * 개발/테스트 시에만 사용
     */
    // @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    public void testScheduler() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        log.info("🧪 Test scheduler executed at {}", now.format(FORMATTER));
        log.info("📍 Next market open collection will be at 9:00 AM ET on weekdays");
    }
    
    /**
     * 수동 트리거용 메서드 (외부에서 호출 가능)
     * @return 처리된 심볼 수
     */
    public int triggerManualCollection() {
        ZonedDateTime now = ZonedDateTime.now(US_EASTERN);
        log.info("🔧 Manual financial metrics collection triggered at {}", 
                now.format(FORMATTER));
        
        try {
            int processedCount = financialMetricsService.fetchAndSaveSp500BasicFinancials(20, 0);
            
            log.info("✅ Manual financial metrics collection completed");
            log.info("📊 Summary: {} S&P 500 symbols processed", processedCount);
            
            return processedCount;
        } catch (Exception e) {
            log.error("❌ Manual financial metrics collection failed: {}", e.getMessage(), e);
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
        
        // 다음 평일 9:00 AM 계산
        ZonedDateTime nextRun = now.withHour(9).withMinute(0).withSecond(0).withNano(0);
        
        // 오늘이 이미 9시가 지났거나 주말이면 다음 평일로
        if (now.getHour() >= 9 || now.getDayOfWeek().getValue() >= 6) {
            do {
                nextRun = nextRun.plusDays(1);
            } while (nextRun.getDayOfWeek().getValue() >= 6); // 주말 건너뛰기
        }
        
        return String.format("Next scheduled collection: %s (US Market opens 30 minutes later)", 
                nextRun.format(FORMATTER));
    }
} 