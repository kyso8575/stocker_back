package com.stocker_back.stocker_back.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "관심 종목 응답 DTO")
public class WatchlistResponseDto {
    
    @Schema(description = "관심 종목 ID")
    private Long id;
    
    @Schema(description = "주식 심볼", example = "AAPL")
    private String symbol;
    
    @Schema(description = "주식 설명")
    private String description;
    
    @Schema(description = "표시용 심볼", example = "AAPL")
    private String displaySymbol;
    
    @Schema(description = "회사명", example = "Apple Inc.")
    private String name;
    
    @Schema(description = "통화", example = "USD")
    private String currency;
    
    @Schema(description = "거래소", example = "NASDAQ")
    private String exchange;
    
    @Schema(description = "관심 종목 추가 시간")
    private LocalDateTime addedAt;
} 