package com.stocker_back.stocker_back.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_info", 
       uniqueConstraints = @UniqueConstraint(name = "uk_symbol", columnNames = {"symbol"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSymbol {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, length = 200)
    private String description;
    
    @Column(length = 50)
    private String displaySymbol;
    
    @Column(length = 20)
    private String type;
    
    @Column(nullable = false, length = 10)
    private String currency;
    
    @Column(nullable = false, length = 10)
    private String exchange;
    
    // Finnhub API 응답에서 figi 필드가 있다면 추가
    @Column(length = 30)
    private String figi;
    
    // Finnhub API 응답에서 mic 필드가 있다면 추가
    @Column(length = 20)
    private String mic;
    
    // 데이터가 마지막으로 업데이트된 시간을 저장
    @Column(name = "last_updated")
    private java.time.LocalDateTime lastUpdated;
    
    // 회사 프로필 관련 필드 추가
    @Column(length = 50)
    private String country;
    
    @Column(length = 10)
    private String estimateCurrency;
    
    @Column(length = 200)
    private String finnhubIndustry;
    
    @Column(length = 20)
    private String ipo;
    
    @Column(length = 500)
    private String logo;
    
    @Column(length = 200)
    private String name;
    
    @Column(length = 20)
    private String phone;
    
    @Column
    private Double shareOutstanding;
    
    @Column(length = 200)
    private String weburl;
    
    // 마지막 프로필 업데이트 시간
    @Column(name = "profile_updated")
    private java.time.LocalDateTime profileUpdated;
    
    // 빈 프로필 응답 여부
    // null: 아직 시도하지 않음
    // true: API 호출 시도했으나 데이터 없음 또는 오류 발생
    // false: API 호출 성공 및 유효한 데이터 존재
    @Column(name = "profile_empty")
    private Boolean profileEmpty;
} 