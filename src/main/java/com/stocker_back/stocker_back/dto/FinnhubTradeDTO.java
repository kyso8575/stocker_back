package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinnhubTradeDTO {
    
    @JsonProperty("type")
    private String type; // "trade"
    
    @JsonProperty("data")
    private List<TradeData> data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeData {
        
        @JsonProperty("s")
        private String symbol; // Stock symbol
        
        @JsonProperty("p")
        private BigDecimal price; // Price
        
        @JsonProperty("v")
        private Long volume; // Volume
        
        @JsonProperty("c")
        private List<String> conditions; // Trade conditions
        
        @JsonProperty("t")
        private Long timestamp; // Timestamp in milliseconds
    }
} 