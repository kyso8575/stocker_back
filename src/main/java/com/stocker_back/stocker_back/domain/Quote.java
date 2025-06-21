package com.stocker_back.stocker_back.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotes", 
       indexes = {
           @Index(name = "idx_quote_symbol", columnList = "symbol"),
           @Index(name = "idx_quote_timestamp", columnList = "timestamp"),
           @Index(name = "idx_quote_symbol_timestamp", columnList = "symbol, timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal currentPrice;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal openPrice;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal highPrice;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal lowPrice;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal previousClosePrice;
    
    @Column(nullable = false)
    private Long volume;
    
    @Column(nullable = false)
    private Long timestamp; // Unix timestamp from Finnhub
    
    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
} 