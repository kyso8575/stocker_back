package com.stocker_back.stocker_back.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistRequestDto {
    
    @NotBlank(message = "Stock symbol is required")
    private String symbol;
} 