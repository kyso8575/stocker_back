package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {
    
    // 심볼로 찾기
    boolean existsBySymbol(String symbol);
    
    // Exchange로 모든 심볼 찾기 
    java.util.List<StockSymbol> findByExchange(String exchange);
} 