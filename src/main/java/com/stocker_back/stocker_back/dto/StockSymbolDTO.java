package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockSymbolDTO {
    
    private String symbol;
    private String description;
    
    @JsonProperty("displaySymbol")
    private String displaySymbol;
    
    private String type;
    private String currency;
    
    @JsonProperty("figi")
    private String figi;
    
    @JsonProperty("mic")
    private String mic;
    
    private String exchange;
} 