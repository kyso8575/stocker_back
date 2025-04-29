package com.stocker.stocker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasicFinancialsDto {
    private String symbol;
    
    // 시가 총액 (회사의 시장 규모)
    private BigDecimal marketCapitalization;
    
    // 기업 가치 (인수 가치 추정 및 EV 배수 계산에 사용)
    private BigDecimal enterpriseValue;
    
    // 주가수익비율 (PER) - 최근 12개월 기준
    private BigDecimal peTTM;
    
    // 주가수익비율 (PER) - 비경상 항목 제외, TTM 기준 (선호될 수 있음)
    private BigDecimal peExclExtraTTM;
    
    // 주가순자산비율 (PBR) - 현재 또는 TTM 기준
    private BigDecimal pb;
    
    // 주가순자산비율 (PBR) - 가장 최근 분기 기준 (선호될 수 있음)
    private BigDecimal pbQuarterly;
    
    // 주가매출비율 (PSR) - 최근 12개월 기준
    private BigDecimal psTTM;
    
    // 예상 연간 배당수익률 (미래 예측치)
    private BigDecimal dividendYieldIndicatedAnnual;
    
    // 현재 배당수익률 - 최근 12개월 기준
    private BigDecimal currentDividendYieldTTM;
    
    // 기업가치/잉여현금흐름 (EV/FCF) - 최근 12개월 기준
    @JsonProperty("currentEv/freeCashFlowTTM")
    private BigDecimal currentEvFreeCashFlowTTM;
    
    // 주가/주당현금흐름 (Price/Cash Flow) - 최근 12개월 기준
    private BigDecimal pcfShareTTM;
    
    // 주가/주당잉여현금흐름 (Price/Free Cash Flow) - 최근 12개월 기준
    private BigDecimal pfcfShareTTM;
    
    // 주가/주당유형자산 (Price/Tangible Book Value) - 가장 최근 분기 기준
    private BigDecimal ptbvQuarterly;
    
    // 최근 12개월(TTM) 주당순이익 - 가장 기본적인 EPS 지표
    private BigDecimal epsTTM;
    
    // 기술적 지표 필드 (null이 아닌 값들만 유지)
    
    // 52주 최고/최저가
    private BigDecimal weekHigh52;
    private String weekHighDate52;
    private BigDecimal weekLow52;
    private String weekLowDate52;
    
    // 변동성 지표
    private BigDecimal beta;
    private BigDecimal volatility90Day;
    
    // 거래량 지표
    private BigDecimal dayAverageTradingVolume10;
    private BigDecimal monthAverageTradingVolume3;
    
    // 수익률 지표
    private BigDecimal dayPriceReturnDaily5;
    private BigDecimal weekPriceReturnDaily13;
    private BigDecimal weekPriceReturnDaily26;
    private BigDecimal weekPriceReturnDaily52;
    private BigDecimal yearToDatePriceReturnDaily;
    
    // 상대 강도 지표
    private BigDecimal priceRelativeToSP50013Week;
    private BigDecimal priceRelativeToSP50026Week;
    private BigDecimal priceRelativeToSP5004Week;
    private BigDecimal priceRelativeToSP50052Week;
    private BigDecimal priceRelativeToSP500Ytd;
}