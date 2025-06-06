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
public class FinnhubSubscriptionDTO {
    
    @JsonProperty("type")
    private String type; // "subscribe" or "unsubscribe"
    
    @JsonProperty("symbol")
    private String symbol; // Stock symbol to subscribe/unsubscribe
    
    public static FinnhubSubscriptionDTO subscribe(String symbol) {
        return FinnhubSubscriptionDTO.builder()
                .type("subscribe")
                .symbol(symbol)
                .build();
    }
    
    public static FinnhubSubscriptionDTO unsubscribe(String symbol) {
        return FinnhubSubscriptionDTO.builder()
                .type("unsubscribe")
                .symbol(symbol)
                .build();
    }
} 