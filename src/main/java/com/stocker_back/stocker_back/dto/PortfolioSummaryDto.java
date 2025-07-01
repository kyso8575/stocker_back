package com.stocker_back.stocker_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryDto {
    
    // 계좌 정보
    private BigDecimal totalBalance;           // 총 잔고 (현금)
    private BigDecimal totalInvested;         // 총 투자금액 (매수 총액)
    private BigDecimal totalMarketValue;      // 총 평가금액 (현재 보유 종목 평가금액 + 현금)
    
    // 수익률 정보
    private BigDecimal totalReturn;           // 총 수익률 (%)
    private BigDecimal totalProfit;           // 총 손익 (평가손익 + 실현손익)
    private BigDecimal unrealizedProfit;      // 평가손익 (미실현 손익)
    private BigDecimal realizedProfit;        // 실현손익 (매도로 인한 손익)
    
    // 투자 성과 지표
    private BigDecimal initialBalance;        // 초기 잔고 (1,000,000원)
    private BigDecimal returnRate;            // 수익률 (%)
    private BigDecimal profitRate;            // 손익률 (%)
    
    // 거래 통계
    private Integer totalTrades;              // 총 거래 횟수
    private Integer buyTrades;                // 매수 횟수
    private Integer sellTrades;               // 매도 횟수
    private BigDecimal totalTradingVolume;    // 총 거래량
    
    // 보유 종목 분석
    private List<HoldingSummaryDto> holdings; // 보유 종목별 분석
    private Integer totalHoldings;            // 보유 종목 수
    
    // 거래 내역
    private List<TradeHistoryDto> recentTrades; // 최근 거래 내역
    
    // 시간별 성과
    private LocalDateTime analysisDate;       // 분석 기준일시
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingSummaryDto {
        private String symbol;
        private String logo;
        private BigDecimal quantity;
        private BigDecimal avgPrice;
        private BigDecimal currentPrice;
        private BigDecimal investedAmount;    // 투자금액 (avgPrice * quantity)
        private BigDecimal marketValue;       // 평가금액 (currentPrice * quantity)
        private BigDecimal unrealizedProfit;  // 평가손익
        private BigDecimal profitRate;        // 수익률 (%)
        private BigDecimal weight;            // 포트폴리오 비중 (%)
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeHistoryDto {
        private Long id;
        private String symbol;
        private String tradeType;             // "BUY" or "SELL"
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal totalAmount;
        private BigDecimal realizedProfit;    // 매도 시 실현손익
        private LocalDateTime tradeDate;
    }
} 