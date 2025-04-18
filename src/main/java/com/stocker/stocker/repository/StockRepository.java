package com.stocker.stocker.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stocker.stocker.domain.Stock;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    
    /**
     * 티커 심볼로 주식 정보 조회
     * @param ticker 주식 티커 심볼
     * @return 해당 티커에 대한 주식 정보
     */
    Optional<Stock> findByTicker(String ticker);
    
    /**
     * 회사명에 특정 키워드가 포함된 주식 목록 조회
     * @param keyword 검색 키워드
     * @return 검색 결과 주식 목록
     */
    List<Stock> findByNameContainingIgnoreCase(String keyword);
    
    /**
     * 특정 국가의 주식 목록 조회
     * @param country 국가 코드
     * @return 해당 국가의 주식 목록
     */
    List<Stock> findByCountry(String country);
    
    /**
     * 특정 거래소의 주식 목록 조회
     * @param exchange 거래소명
     * @return 해당 거래소의 주식 목록
     */
    List<Stock> findByExchange(String exchange);
    
    /**
     * 특정 산업군의 주식 목록 조회
     * @param industry 산업군
     * @return 해당 산업군의 주식 목록
     */
    List<Stock> findByFinnhubIndustry(String industry);
    
    /**
     * 티커가 존재하는지 확인
     * @param ticker 주식 티커 심볼
     * @return 존재 여부
     */
    boolean existsByTicker(String ticker);
    
    /**
     * country 값이 null인 주식 목록 페이징 조회
     * @param pageable 페이지 정보
     * @return country가 null인 주식 목록
     */
    Page<Stock> findByCountryIsNull(Pageable pageable);
    
    /**
     * country 값이 null인 주식 개수 조회
     * @return country가 null인 주식 개수
     */
    long countByCountryIsNull();
} 