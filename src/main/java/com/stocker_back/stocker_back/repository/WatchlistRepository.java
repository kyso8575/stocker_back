package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.Watchlist;
import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.domain.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    
    /**
     * 특정 사용자의 모든 관심 종목 조회
     */
    @Query("SELECT w FROM Watchlist w " +
           "JOIN FETCH w.stockSymbol s " +
           "WHERE w.user.id = :userId " +
           "ORDER BY w.addedAt DESC")
    List<Watchlist> findByUserIdWithStockSymbol(@Param("userId") Long userId);
    
    /**
     * 특정 사용자와 주식 심볼로 관심 종목 존재 여부 확인
     */
    boolean existsByUserAndStockSymbol(User user, StockSymbol stockSymbol);
    
    /**
     * 특정 사용자와 주식 심볼로 관심 종목 조회
     */
    Optional<Watchlist> findByUserAndStockSymbol(User user, StockSymbol stockSymbol);
    
    /**
     * 특정 사용자의 관심 종목 개수 조회
     */
    long countByUserId(Long userId);
} 