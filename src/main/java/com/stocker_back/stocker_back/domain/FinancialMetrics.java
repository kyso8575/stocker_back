package com.stocker_back.stocker_back.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "basic_financials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    // Market Capitalization
    @Column
    private Double marketCapitalization;
    
    // Trading Volume Metrics
    @Column
    private Double tenDayAverageTradingVolume;
    
    @Column
    private Double threeMonthAverageTradingVolume;
    
    // Price Return Metrics
    @Column
    private Double thirteenWeekPriceReturnDaily;
    
    @Column
    private Double twentySixWeekPriceReturnDaily;
    
    @Column
    private Double fiftyTwoWeekPriceReturnDaily;
    
    @Column
    private Double fiveDayPriceReturnDaily;
    
    @Column
    private Double yearToDatePriceReturnDaily;
    
    @Column
    private Double monthToDatePriceReturnDaily;
    
    // Highs and Lows
    @Column
    private Double fiftyTwoWeekHigh;
    
    @Column
    private String fiftyTwoWeekHighDate;
    
    @Column
    private Double fiftyTwoWeekLow;
    
    @Column
    private String fiftyTwoWeekLowDate;
    
    // Beta and Volatility
    @Column
    private Double beta;
    
    @Column
    private Double threeMonthADReturnStd;
    
    // Valuation Ratios
    @Column
    private Double priceToEarningsRatio; // PE
    
    @Column
    private Double priceToBookRatio; // PB
    
    @Column
    private Double priceToSalesRatio; // PS
    
    @Column
    private Double priceToCashFlowRatio; // PCF
    
    // Financial Performance Metrics
    @Column
    private Double returnOnEquity; // ROE
    
    @Column
    private Double returnOnAssets; // ROA
    
    @Column
    private Double returnOnInvestment; // ROI
    
    @Column
    private Double grossMarginTTM;
    
    @Column
    private Double operatingMarginTTM;
    
    @Column
    private Double netProfitMarginTTM;
    
    // Debt Metrics
    @Column
    private Double totalDebtToEquityQuarterly;
    
    @Column
    private Double longTermDebtToEquityQuarterly;
    
    // Dividend Metrics
    @Column
    private Double dividendPerShareAnnual;
    
    @Column
    private Double dividendYieldIndicatedAnnual;
    
    @Column
    private Double dividendGrowthRate5Y;
    
    // Growth Metrics
    @Column
    private Double revenueGrowth3Y;
    
    @Column
    private Double revenueGrowth5Y;
    
    @Column
    private Double epsGrowth3Y;
    
    @Column
    private Double epsGrowth5Y;
    
    // Balance Sheet Metrics
    @Column
    private Double bookValuePerShareAnnual;
    
    @Column
    private Double cashPerSharePerShareAnnual;
    
    @Column
    private Double currentRatioAnnual;
    
    @Column
    private Double quickRatioAnnual;
    
    // Timestamp when this record was created
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
} 