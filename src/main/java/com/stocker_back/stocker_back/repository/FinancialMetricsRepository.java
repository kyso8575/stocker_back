package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialMetricsRepository extends JpaRepository<FinancialMetrics, Long> {
    
    /**
     * Find the most recent financial metrics for a specific symbol
     * @param symbol the stock symbol (e.g., AAPL)
     * @return Optional containing the most recent FinancialMetrics if found
     */
    Optional<FinancialMetrics> findTopBySymbolOrderByCreatedAtDesc(String symbol);
    
    /**
     * Find all financial metrics for a specific symbol ordered by creation date (newest first)
     * @param symbol the stock symbol (e.g., AAPL)
     * @return List of FinancialMetrics for the symbol
     */
    List<FinancialMetrics> findBySymbolOrderByCreatedAtDesc(String symbol);
    
    /**
     * Find financial metrics for a specific symbol within a date range
     * @param symbol the stock symbol (e.g., AAPL)
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return List of FinancialMetrics within the date range
     */
    @Query("SELECT fm FROM FinancialMetrics fm WHERE fm.symbol = :symbol " +
           "AND fm.createdAt >= :fromDate AND fm.createdAt <= :toDate " +
           "ORDER BY fm.createdAt DESC")
    List<FinancialMetrics> findBySymbolAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("symbol") String symbol, 
            @Param("fromDate") LocalDateTime fromDate, 
            @Param("toDate") LocalDateTime toDate);
    
    /**
     * Count distinct symbols in the financial metrics table
     * @return the number of distinct symbols
     */
    @Query("SELECT COUNT(DISTINCT fm.symbol) FROM FinancialMetrics fm")
    long countDistinctSymbols();
    
    /**
     * Check if a financial metrics record exists for a specific symbol on a given date
     * @param symbol the stock symbol (e.g., AAPL)
     * @param date the date to check (LocalDate)
     * @return true if a record exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(fm) > 0 THEN true ELSE false END FROM FinancialMetrics fm " +
           "WHERE fm.symbol = :symbol AND CAST(fm.createdAt AS LocalDate) = :date")
    boolean existsBySymbolAndCreatedAtDate(@Param("symbol") String symbol, @Param("date") LocalDate date);
    
    /**
     * Find all financial metrics for S&P 500 symbols on a specific date
     * @param date the date to find metrics for (LocalDate)
     * @return List of FinancialMetrics for S&P 500 symbols on the given date
     */
    @Query("SELECT fm FROM FinancialMetrics fm " +
           "JOIN StockSymbol ss ON fm.symbol = ss.symbol " +
           "WHERE ss.isSp500 = true AND CAST(fm.createdAt AS LocalDate) = :date " +
           "ORDER BY fm.symbol ASC")
    List<FinancialMetrics> findSp500FinancialMetricsByDate(@Param("date") LocalDate date);
    
    /**
     * Find the most recent financial metrics for all S&P 500 symbols
     * @return List of FinancialMetrics for S&P 500 symbols (most recent for each symbol)
     */
    @Query("SELECT fm FROM FinancialMetrics fm " +
           "JOIN StockSymbol ss ON fm.symbol = ss.symbol " +
           "WHERE ss.isSp500 = true " +
           "AND fm.createdAt = (SELECT MAX(fm2.createdAt) FROM FinancialMetrics fm2 " +
           "                    WHERE fm2.symbol = fm.symbol) " +
           "ORDER BY fm.symbol ASC")
    List<FinancialMetrics> findLatestSp500FinancialMetrics();
    
    /**
     * Find the most recent date when S&P 500 financial metrics were collected
     * @return Optional containing the most recent date, or empty if no data exists
     */
    @Query("SELECT MAX(CAST(fm.createdAt AS LocalDate)) FROM FinancialMetrics fm " +
           "JOIN StockSymbol ss ON fm.symbol = ss.symbol " +
           "WHERE ss.isSp500 = true")
    Optional<LocalDate> findMostRecentSp500MetricsDate();
} 