package com.stocker_back.stocker_back.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinancialMetricsDTO {
    
    @JsonProperty("metricType")
    private String metricType;
    
    @JsonProperty("metric")
    private Map<String, Object> metric;
    
    @JsonProperty("series")
    private Map<String, Object> series;
} 