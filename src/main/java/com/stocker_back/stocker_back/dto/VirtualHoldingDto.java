package com.stocker_back.stocker_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualHoldingDto {
    private String symbol;
    private BigDecimal quantity;
    private BigDecimal avgPrice;
    private BigDecimal currentPrice;
    private BigDecimal evalAmount;   // 평가금액 = currentPrice * quantity
    private BigDecimal evalProfit;   // 평가손익 = (currentPrice - avgPrice) * quantity
    private String logo;
} 