package com.stocker_back.stocker_back.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "관심 종목 추가 요청 DTO")
public class WatchlistRequestDto {
    
    @Schema(
        description = "주식 심볼",
        example = "AAPL",
        required = true
    )
    @NotBlank(message = "Stock symbol is required")
    private String symbol;
} 