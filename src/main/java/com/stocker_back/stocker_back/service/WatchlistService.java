package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.domain.Watchlist;
import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.dto.WatchlistRequestDto;
import com.stocker_back.stocker_back.dto.WatchlistResponseDto;
import com.stocker_back.stocker_back.repository.UserRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.WatchlistRepository;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WatchlistService {
    
    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final StockSymbolRepository stockSymbolRepository;
    private final QuoteRepository quoteRepository;
    private final TradeRepository tradeRepository;
    
    /**
     * 사용자의 관심 종목 목록 조회
     */
    public List<WatchlistResponseDto> getUserWatchlist(Long userId) {
        log.info("Getting watchlist for user: {}", userId);
        
        List<Watchlist> watchlists = watchlistRepository.findByUserIdWithStockSymbol(userId);
        
        return watchlists.stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 관심 종목 추가
     */
    @Transactional
    public WatchlistResponseDto addToWatchlist(Long userId, WatchlistRequestDto requestDto) {
        log.info("Adding stock to watchlist - userId: {}, symbol: {}", userId, requestDto.getSymbol());
        
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // 주식 심볼 조회
        StockSymbol stockSymbol = stockSymbolRepository.findBySymbol(requestDto.getSymbol().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock symbol not found: " + requestDto.getSymbol()));
        
        // 이미 관심 종목에 추가되어 있는지 확인
        if (watchlistRepository.existsByUserAndStockSymbol(user, stockSymbol)) {
            throw new IllegalArgumentException("Stock is already in watchlist");
        }
        
        // 관심 종목 추가
        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .stockSymbol(stockSymbol)
                .build();
        
        Watchlist savedWatchlist = watchlistRepository.save(watchlist);
        
        log.info("Successfully added to watchlist - userId: {}, symbol: {}", userId, requestDto.getSymbol());
        
        return convertToResponseDto(savedWatchlist);
    }
    
    /**
     * 관심 종목 제거
     */
    @Transactional
    public void removeFromWatchlist(Long userId, String symbol) {
        log.info("Removing stock from watchlist - userId: {}, symbol: {}", userId, symbol);
        
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // 주식 심볼 조회
        StockSymbol stockSymbol = stockSymbolRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock symbol not found: " + symbol));
        
        // 관심 종목 조회 및 삭제
        Watchlist watchlist = watchlistRepository.findByUserAndStockSymbol(user, stockSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock is not in watchlist"));
        
        watchlistRepository.delete(watchlist);
        
        log.info("Successfully removed from watchlist - userId: {}, symbol: {}", userId, symbol);
    }
    
    /**
     * 특정 주식이 사용자의 관심 종목에 있는지 확인
     */
    public boolean exist(Long userId, String symbol) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        StockSymbol stockSymbol = stockSymbolRepository.findBySymbol(symbol.toUpperCase())
                .orElse(null);
        
        if (stockSymbol == null) {
            return false;
        }
        
        return watchlistRepository.existsByUserAndStockSymbol(user, stockSymbol);
    }
    
    /**
     * 사용자의 관심 종목 개수 조회
     */
    public long getWatchlistCount(Long userId) {
        return watchlistRepository.countByUserId(userId);
    }
    
    /**
     * Watchlist 엔티티를 ResponseDto로 변환
     */
    private WatchlistResponseDto convertToResponseDto(Watchlist watchlist) {
        StockSymbol stockSymbol = watchlist.getStockSymbol();
        
        // 현재가: Trade에서 하루 안의 최신 데이터 조회, 없으면 null
        java.math.BigDecimal price = null;
        try {
            java.time.LocalDateTime oneDayAgo = java.time.LocalDateTime.now().minusDays(1);
            java.util.List<com.stocker_back.stocker_back.domain.Trade> recentTrades = tradeRepository.findTradesBySymbolAfter(stockSymbol.getSymbol(), oneDayAgo);
            if (!recentTrades.isEmpty()) {
                price = recentTrades.get(0).getPrice();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch price from Trade for symbol {}: {}", stockSymbol.getSymbol(), e.getMessage());
        }
        
        // change 계산 (Quote 기준)
        BigDecimal change = null;
        try {
            Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(stockSymbol.getSymbol());
            if (latestQuoteOpt.isPresent()) {
                Quote quote = latestQuoteOpt.get();
                if (quote.getCurrentPrice() != null && quote.getPreviousClosePrice() != null) {
                    change = quote.getCurrentPrice().subtract(quote.getPreviousClosePrice());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate change for symbol {}: {}", stockSymbol.getSymbol(), e.getMessage());
        }
        
        return WatchlistResponseDto.builder()
                .id(watchlist.getId())
                .symbol(stockSymbol.getSymbol())
                .description(stockSymbol.getDescription())
                .displaySymbol(stockSymbol.getDisplaySymbol())
                .name(stockSymbol.getName())
                .currency(stockSymbol.getCurrency())
                .exchange(stockSymbol.getExchange())
                .addedAt(watchlist.getAddedAt())
                .logo(stockSymbol.getLogo())
                .change(change)
                .price(price)
                .build();
    }
} 