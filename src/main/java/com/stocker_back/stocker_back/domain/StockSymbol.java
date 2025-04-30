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
} 