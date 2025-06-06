package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCleanupService {
    
    private final TradeRepository tradeRepository;
    
    /**
     * 매일 새벽 2시에 7일 이전의 거래 데이터 삭제
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldTrades() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            
            log.info("Starting cleanup of trade data older than: {}", cutoffTime);
            
            long beforeCount = tradeRepository.count();
            tradeRepository.deleteOldTrades(cutoffTime);
            long afterCount = tradeRepository.count();
            
            long deletedCount = beforeCount - afterCount;
            log.info("Trade cleanup completed. Deleted {} records, {} remaining", 
                    deletedCount, afterCount);
            
        } catch (Exception e) {
            log.error("Failed to cleanup old trade data", e);
        }
    }
    
    /**
     * 매시간 거래 데이터 통계를 로깅
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void logTradeStatistics() {
        try {
            long totalTrades = tradeRepository.count();
            log.info("Current total trade count: {}", totalTrades);
            
            // 최근 1시간 동안의 거래 수
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentTrades = tradeRepository.findTradesBetween(oneHourAgo, LocalDateTime.now()).size();
            log.info("Trades received in last hour: {}", recentTrades);
            
        } catch (Exception e) {
            log.error("Failed to log trade statistics", e);
        }
    }
} 