package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    
    // 특정 심볼의 최신 거래 데이터 조회
    @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol ORDER BY t.timestamp DESC")
    List<Trade> findLatestTradesBySymbol(@Param("symbol") String symbol);
    
    // 특정 심볼의 특정 시간 이후 거래 데이터 조회
    @Query("SELECT t FROM Trade t WHERE t.symbol = :symbol AND t.receivedAt >= :fromTime ORDER BY t.timestamp DESC")
    List<Trade> findTradesBySymbolAfter(@Param("symbol") String symbol, @Param("fromTime") LocalDateTime fromTime);
    
    // 특정 시간 범위의 모든 거래 데이터 조회
    @Query("SELECT t FROM Trade t WHERE t.receivedAt BETWEEN :startTime AND :endTime ORDER BY t.timestamp DESC")
    List<Trade> findTradesBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    // 심볼별 거래 건수 조회
    @Query("SELECT t.symbol, COUNT(t) FROM Trade t GROUP BY t.symbol ORDER BY COUNT(t) DESC")
    List<Object[]> countTradesBySymbol();
    
    // 특정 심볼의 최신 거래 가격 조회
    @Query("SELECT t.price FROM Trade t WHERE t.symbol = :symbol ORDER BY t.timestamp DESC LIMIT 1")
    java.math.BigDecimal findLatestPriceBySymbol(@Param("symbol") String symbol);
    
    // 오래된 거래 데이터 삭제 (성능 최적화용)
    @Modifying
    @Query("DELETE FROM Trade t WHERE t.receivedAt < :cutoffTime")
    void deleteOldTrades(@Param("cutoffTime") LocalDateTime cutoffTime);
} 