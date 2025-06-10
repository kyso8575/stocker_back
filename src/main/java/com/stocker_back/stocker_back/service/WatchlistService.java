package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.domain.Watchlist;
import com.stocker_back.stocker_back.dto.WatchlistRequestDto;
import com.stocker_back.stocker_back.dto.WatchlistResponseDto;
import com.stocker_back.stocker_back.repository.UserRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WatchlistService {
    
    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final StockSymbolRepository stockSymbolRepository;
    
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
    public boolean isInWatchlist(Long userId, String symbol) {
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
        
        return WatchlistResponseDto.builder()
                .id(watchlist.getId())
                .symbol(stockSymbol.getSymbol())
                .description(stockSymbol.getDescription())
                .displaySymbol(stockSymbol.getDisplaySymbol())
                .name(stockSymbol.getName())
                .currency(stockSymbol.getCurrency())
                .exchange(stockSymbol.getExchange())
                .addedAt(watchlist.getAddedAt())
                .build();
    }
} 