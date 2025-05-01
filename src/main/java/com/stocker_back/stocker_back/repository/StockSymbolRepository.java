package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface StockSymbolRepository extends JpaRepository<StockSymbol, Long> {
    
    // 심볼로 찾기
    boolean existsBySymbol(String symbol);
    
    // 심볼로 StockSymbol 엔티티 찾기
    Optional<StockSymbol> findBySymbol(String symbol);
    
    // Exchange로 모든 심볼 찾기 
    java.util.List<StockSymbol> findByExchange(String exchange);
    
    // 모든 심볼 문자열만 가져오는 최적화된 쿼리
    @Query("SELECT s.symbol FROM StockSymbol s")
    Set<String> findAllSymbols();
    
    // 빈 프로필이 아닌(유효한 데이터가 있는) 심볼 수
    long countByProfileEmptyFalse();
    
    // 빈 프로필인 심볼 수
    long countByProfileEmptyTrue();
} 