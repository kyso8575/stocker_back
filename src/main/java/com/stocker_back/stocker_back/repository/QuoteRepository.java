package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {
    
    // 특정 심볼의 최신 시세 조회
    @Query("SELECT q FROM Quote q WHERE q.symbol = :symbol ORDER BY q.timestamp DESC")
    List<Quote> findLatestQuotesBySymbol(@Param("symbol") String symbol);
    
    // 특정 심볼의 최신 시세 하나만 조회
    @Query("SELECT q FROM Quote q WHERE q.symbol = :symbol ORDER BY q.timestamp DESC LIMIT 1")
    Optional<Quote> findLatestQuoteBySymbol(@Param("symbol") String symbol);
    
    // 특정 시간 범위의 시세 데이터 조회
    @Query("SELECT q FROM Quote q WHERE q.createdAt BETWEEN :startTime AND :endTime ORDER BY q.timestamp DESC")
    List<Quote> findQuotesBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    // 특정 심볼의 특정 시간 이후 시세 데이터 조회
    @Query("SELECT q FROM Quote q WHERE q.symbol = :symbol AND q.createdAt >= :fromTime ORDER BY q.timestamp DESC")
    List<Quote> findQuotesBySymbolAfter(@Param("symbol") String symbol, @Param("fromTime") LocalDateTime fromTime);
    
    // 오늘 날짜의 S&P 500 시세 데이터 조회
    @Query("SELECT q FROM Quote q " +
           "JOIN StockSymbol ss ON q.symbol = ss.symbol " +
           "WHERE ss.isSp500 = true AND CAST(q.createdAt AS LocalDate) = CAST(:date AS LocalDate) " +
           "ORDER BY q.symbol ASC")
    List<Quote> findSp500QuotesByDate(@Param("date") LocalDateTime date);
    
    // 오래된 시세 데이터 삭제 (성능 최적화용)
    @Modifying
    @Transactional
    @Query("DELETE FROM Quote q WHERE q.createdAt < :cutoffTime")
    void deleteOldQuotes(@Param("cutoffTime") LocalDateTime cutoffTime);
} 