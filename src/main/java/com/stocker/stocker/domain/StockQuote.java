package com.stocker.stocker.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주식의 실시간 시세 정보를 저장하는 엔티티
 */
@Entity
@Table(name = "stock_quotes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockQuote {
    
    @Id
    @SequenceGenerator(name = "quote_seq", sequenceName = "quote_id_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "quote_seq")
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;
    
    @Column(nullable = false)
    private String symbol;  // 주식 심볼 (티커)
    
    @Column(name = "current_price", precision = 15, scale = 4)
    private BigDecimal currentPrice;  // 현재 가격
    
    @Column(precision = 15, scale = 4)
    private BigDecimal change;  // 변동폭
    
    @Column(name = "percent_change", precision = 10, scale = 4)
    private BigDecimal percentChange;  // 변동률(%)
    
    @Column(precision = 15, scale = 4)
    private BigDecimal high;  // 당일 고가
    
    @Column(precision = 15, scale = 4)
    private BigDecimal low;  // 당일 저가
    
    @Column(precision = 15, scale = 4)
    private BigDecimal open;  // 당일 시가
    
    @Column(name = "previous_close", precision = 15, scale = 4)
    private BigDecimal previousClose;  // 전일 종가
    
    @Column
    private Long timestamp;  // Unix 타임스탬프
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 업데이트 시간
    
    // 업데이트 시간 자동 설정
    @jakarta.persistence.PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // 업데이트 시간 자동 설정
    @jakarta.persistence.PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
} 