package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {
    
    /**
     * Find the most recent quote for a given symbol
     */
    Optional<Quote> findTopBySymbolOrderByTimestampDesc(String symbol);
    
    /**
     * Find all quotes for a given symbol ordered by timestamp (descending)
     */
    List<Quote> findBySymbolOrderByTimestampDesc(String symbol);
    
    /**
     * Find quotes for a given symbol with a limit
     */
    List<Quote> findTop10BySymbolOrderByTimestampDesc(String symbol);
} 