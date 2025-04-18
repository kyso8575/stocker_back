package com.stocker.stocker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
    
    @Id
    @SequenceGenerator(name = "stock_seq", sequenceName = "stock_id_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_seq")
    private Long id;
    
    @Column(nullable = false, unique = true, length = 20)
    private String ticker;  // 주식 심볼/티커 (예: AAPL, MSFT)
    
    @Column(nullable = false, length = 100)
    private String name;  // 회사명
    
    @Column(length = 10)
    private String currency;  // 통화 코드 (USD 등)
    
    @Column(length = 10)
    private String estimateCurrency;  // 예상 통화 코드
    
    @Column(length = 100)
    private String exchange;  // 거래소 (NASDAQ NMS - GLOBAL MARKET 등)
    
    @Column(length = 50)
    private String finnhubIndustry;  // 산업군
    
    @Column(precision = 20, scale = 2)
    private BigDecimal marketCapitalization;  // 시가총액
    
    private Double shareOutstanding;  // 발행주식수
    
    @Column(length = 20)
    private String country;  // 국가 코드
    
    @Column(length = 20)
    private String phone;  // 전화번호
    
    @Column(columnDefinition = "DATE")
    private LocalDate ipo;  // 상장일
    
    @Column(columnDefinition = "TEXT")
    private String logo;  // 로고 URL
    
    @Column(columnDefinition = "TEXT")
    private String weburl;  // 웹사이트 URL
} 