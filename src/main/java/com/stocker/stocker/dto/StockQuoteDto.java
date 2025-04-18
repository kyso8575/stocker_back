package com.stocker.stocker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stocker.stocker.domain.Stock;
import com.stocker.stocker.domain.StockQuote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Finnhub API의 Quote 응답을 매핑하기 위한 DTO
 * https://finnhub.io/docs/api/quote API 응답 형식에 맞춤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockQuoteDto {

    @JsonProperty("c")
    private BigDecimal currentPrice;  // 현재 가격
    
    @JsonProperty("d")
    private BigDecimal change;  // 변동폭
    
    @JsonProperty("dp")
    private BigDecimal percentChange;  // 변동률(%)
    
    @JsonProperty("h")
    private BigDecimal high;  // 당일 고가
    
    @JsonProperty("l")
    private BigDecimal low;  // 당일 저가
    
    @JsonProperty("o")
    private BigDecimal open;  // 당일 시가
    
    @JsonProperty("pc")
    private BigDecimal previousClose;  // 전일 종가
    
    @JsonProperty("t")
    private Long timestamp;  // Unix 타임스탬프
    
    /**
     * DTO를 엔티티로 변환
     * @param stock 주식 정보
     * @return StockQuote 엔티티
     */
    public StockQuote toEntity(Stock stock) {
        return StockQuote.builder()
                .stock(stock)
                .symbol(stock.getTicker())
                .currentPrice(this.currentPrice)
                .change(this.change)
                .percentChange(this.percentChange)
                .high(this.high)
                .low(this.low)
                .open(this.open)
                .previousClose(this.previousClose)
                .timestamp(this.timestamp)
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 타임스탬프를 LocalDateTime으로 변환
     * @return 변환된 LocalDateTime
     */
    public LocalDateTime getTimestampAsLocalDateTime() {
        if (this.timestamp == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(this.timestamp), ZoneId.systemDefault());
    }
} 