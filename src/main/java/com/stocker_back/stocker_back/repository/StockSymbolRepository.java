package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {
    
    // 심볼로 찾기
    boolean existsBySymbol(String symbol);
    
    // Exchange로 모든 심볼 찾기 
    java.util.List<StockSymbol> findByExchange(String exchange);
    
    // 모든 심볼 문자열만 가져오는 최적화된 쿼리
    @Query("SELECT s.symbol FROM StockSymbol s")
    Set<String> findAllSymbols();
} 