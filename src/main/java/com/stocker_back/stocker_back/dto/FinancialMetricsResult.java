package com.stocker_back.stocker_back.dto;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO class to represent the result of a financial metrics fetch operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetricsResult {
    
    /**
     * The financial metrics data if successfully fetched
     */
    private FinancialMetrics metrics;
    
    /**
     * The status of the operation
     */
    private Status status;
    
    /**
     * Reason for skipping or error message if applicable
     */
    private String message;
    
    /**
     * Enum representing the status of the financial metrics fetch operation
     */
    public enum Status {
        SUCCESS,         // Successfully fetched and saved
        SKIPPED,         // Operation was skipped (e.g., already exists)
        NO_DATA,         // No data found for the symbol
        ERROR            // Error occurred during fetch
    }
    
    /**
     * Create a success result
     * 
     * @param metrics The financial metrics data
     * @return FinancialMetricsResult with SUCCESS status
     */
    public static FinancialMetricsResult success(FinancialMetrics metrics) {
        return FinancialMetricsResult.builder()
                .metrics(metrics)
                .status(Status.SUCCESS)
                .message("Successfully fetched and saved financial metrics")
                .build();
    }
    
    /**
     * Create a skipped result
     * 
     * @param symbol The stock symbol
     * @return FinancialMetricsResult with SKIPPED status
     */
    public static FinancialMetricsResult skipped(String symbol) {
        return FinancialMetricsResult.builder()
                .status(Status.SKIPPED)
                .message(String.format("Financial metrics for symbol %s already exist for today, skipping", symbol))
                .build();
    }
    
    /**
     * Create a no data result
     * 
     * @param symbol The stock symbol
     * @return FinancialMetricsResult with NO_DATA status
     */
    public static FinancialMetricsResult noData(String symbol) {
        return FinancialMetricsResult.builder()
                .status(Status.NO_DATA)
                .message(String.format("No financial metrics data found for symbol %s", symbol))
                .build();
    }
    
    /**
     * Create an error result
     * 
     * @param symbol The stock symbol
     * @param errorMessage The error message
     * @return FinancialMetricsResult with ERROR status
     */
    public static FinancialMetricsResult error(String symbol, String errorMessage) {
        return FinancialMetricsResult.builder()
                .status(Status.ERROR)
                .message(String.format("Error fetching financial metrics for symbol %s: %s", symbol, errorMessage))
                .build();
    }
} 