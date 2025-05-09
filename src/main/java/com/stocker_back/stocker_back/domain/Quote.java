package com.stocker_back.stocker_back.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_quotes")
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
    
    // current price
    @Column(nullable = false)
    private Double currentPrice;
    
    // change
    @Column
    private Double change;
    
    // percent change
    @Column
    private Double percentChange;
    
    // high price of the day
    @Column
    private Double highPrice;
    
    // low price of the day
    @Column
    private Double lowPrice;
    
    // open price of the day
    @Column
    private Double openPrice;
    
    // previous close price
    @Column
    private Double previousClosePrice;
    
    // timestamp from Finnhub API (seconds since epoch)
    @Column
    private Long timestamp;
    
    // Local timestamp when this record was created
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
} 