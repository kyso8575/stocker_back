package com.stocker_back.stocker_back.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades", 
       indexes = {
           @Index(name = "idx_symbol", columnList = "symbol"),
           @Index(name = "idx_timestamp", columnList = "timestamp"),
           @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Long volume;
    
    @Column(nullable = false)
    private Long timestamp; // Unix timestamp from Finnhub
    
    @Column(nullable = false, name = "received_at")
    private LocalDateTime receivedAt; // When we received this data
    
    // Finnhub specific fields
    @Column(name = "trade_conditions")
    private String tradeConditions; // 거래 조건들 (comma separated)
    
    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
} 