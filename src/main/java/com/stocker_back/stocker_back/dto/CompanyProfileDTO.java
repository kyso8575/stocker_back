package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for company profile data from Finnhub API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfileDTO {
    
    private String country;
    private String currency;
    private String estimateCurrency;
    private String exchange;
    private String finnhubIndustry;
    private String ipo;
    private String logo;
    private Double marketCapitalization;
    private String name;
    private String phone;
    private Double shareOutstanding;
    
    @JsonProperty("ticker")
    private String symbol; // Map "ticker" from Finnhub API to "symbol" field
    
    private String weburl;
} 