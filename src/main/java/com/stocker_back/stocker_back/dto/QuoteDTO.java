package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteDTO {
    
    // current price
    @JsonProperty("c")
    private Double currentPrice;
    
    // change
    @JsonProperty("d")
    private Double change;
    
    // percent change
    @JsonProperty("dp")
    private Double percentChange;
    
    // high price of the day
    @JsonProperty("h")
    private Double highPrice;
    
    // low price of the day
    @JsonProperty("l")
    private Double lowPrice;
    
    // open price of the day
    @JsonProperty("o")
    private Double openPrice;
    
    // previous close price
    @JsonProperty("pc")
    private Double previousClosePrice;
    
    // timestamp
    @JsonProperty("t")
    private Long timestamp;
} 