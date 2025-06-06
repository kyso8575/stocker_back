package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.StockSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.List;

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
    
    // 빈 프로필이 아닌(유효한 데이터가 있는) 심볼 목록 가져오기
    List<StockSymbol> findByProfileEmptyFalse();
    
    /**
     * 모든 심볼의 S&P 500 상태를 false로 초기화
     */
    @Modifying
    @Query("UPDATE StockSymbol s SET s.isSp500 = false")
    void resetSp500Status();
    
    /**
     * 주어진 심볼 목록의 S&P 500 상태를 true로 설정
     * @param symbols S&P 500에 포함된 심볼 목록
     * @return 업데이트된 레코드 수
     */
    @Modifying
    @Query("UPDATE StockSymbol s SET s.isSp500 = true WHERE s.symbol IN :symbols")
    int updateSp500Status(Set<String> symbols);
    
    // S&P 500에 포함된 모든 심볼 조회
    @Query("SELECT s.symbol FROM StockSymbol s WHERE s.isSp500 = true")
    Set<String> findAllSp500Symbols();
} 