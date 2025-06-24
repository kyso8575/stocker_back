package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteDTO {
    
    @JsonProperty("c")
    private BigDecimal currentPrice;
    
    @JsonProperty("d")
    private BigDecimal change;
    
    @JsonProperty("dp")
    private BigDecimal percentChange;
    
    @JsonProperty("h")
    private BigDecimal highPrice;
    
    @JsonProperty("l")
    private BigDecimal lowPrice;
    
    @JsonProperty("o")
    private BigDecimal openPrice;
    
    @JsonProperty("pc")
    private BigDecimal previousClosePrice;
    
    @JsonProperty("t")
    private Long timestamp;
} 