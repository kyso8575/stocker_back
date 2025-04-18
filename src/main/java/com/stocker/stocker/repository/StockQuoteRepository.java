package com.stocker.stocker.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stocker.stocker.domain.Stock;
import com.stocker.stocker.domain.StockQuote;

/**
 * 주식 실시간 시세 정보에 접근하기 위한 리포지토리
 */
@Repository
public interface StockQuoteRepository extends JpaRepository<StockQuote, Long> {
    
    /**
     * 특정 심볼에 대한 최신 시세 정보 조회
     * @param symbol 주식 심볼
     * @return 최신 시세 정보
     */
    Optional<StockQuote> findTopBySymbolOrderByUpdatedAtDesc(String symbol);
    
    /**
     * 특정 주식에 대한 최신 시세 정보 조회
     * @param stock 주식 객체
     * @return 최신 시세 정보
     */
    Optional<StockQuote> findTopByStockOrderByUpdatedAtDesc(Stock stock);
    
    /**
     * 주어진 심볼 목록에 대한 최신 시세 정보 조회
     * @param symbols 주식 심볼 목록
     * @return 각 심볼의 최신 시세 정보 목록
     */
    @Query("SELECT q FROM StockQuote q WHERE q.symbol IN :symbols AND q.updatedAt IN " +
           "(SELECT MAX(q2.updatedAt) FROM StockQuote q2 WHERE q2.symbol = q.symbol GROUP BY q2.symbol)")
    List<StockQuote> findLatestQuotesBySymbols(@Param("symbols") List<String> symbols);
    
    /**
     * 특정 심볼의 시세 이력 조회
     * @param symbol 주식 심볼
     * @param pageable 페이징 정보
     * @return 시세 이력 페이지
     */
    Page<StockQuote> findBySymbolOrderByUpdatedAtDesc(String symbol, Pageable pageable);
    
    /**
     * 최근 업데이트된 시세 정보 목록 조회
     * @param pageable 페이징 정보
     * @return 최근 업데이트된 시세 정보 페이지
     */
    Page<StockQuote> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    
    /**
     * 특정 기간 내 업데이트된 시세 정보 목록 조회
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param pageable 페이징 정보
     * @return 해당 기간 내 시세 정보 페이지
     */
    Page<StockQuote> findByUpdatedAtBetweenOrderByUpdatedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * 특정 심볼의 모든 시세 정보 삭제
     * @param symbol 주식 심볼
     */
    void deleteBySymbol(String symbol);
    
    /**
     * 특정 시간 이전의 모든 시세 정보 삭제 (데이터 정리용)
     * @param dateTime 기준 시간
     * @return 삭제된 레코드 수
     */
    long deleteByUpdatedAtBefore(LocalDateTime dateTime);
} 