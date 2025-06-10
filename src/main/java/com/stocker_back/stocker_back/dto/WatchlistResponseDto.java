package com.stocker_back.stocker_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResponseDto {
    
    private Long id;
    private String symbol;
    private String description;
    private String displaySymbol;
    private String name;
    private String currency;
    private String exchange;
    private LocalDateTime addedAt;
} 