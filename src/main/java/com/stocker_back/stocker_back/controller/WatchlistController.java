package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.WatchlistRequestDto;
import com.stocker_back.stocker_back.dto.WatchlistResponseDto;
import com.stocker_back.stocker_back.service.WatchlistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks/watchlist")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {
    
    private final WatchlistService watchlistService;
    
    /**
     * 로그인한 사용자의 관심 종목 목록 조회
     */
    @GetMapping
    public ResponseEntity<?> getWatchlist(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            
            List<WatchlistResponseDto> watchlist = watchlistService.getUserWatchlist(userId);
            long totalCount = watchlistService.getWatchlistCount(userId);
            
            log.info("Retrieved watchlist for user: {}, count: {}", userId, totalCount);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "관심 종목 목록을 성공적으로 조회했습니다",
                "data", watchlist,
                "totalCount", totalCount
            ));
            
        } catch (Exception e) {
            log.error("Error retrieving watchlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 관심 종목 추가
     */
    @PostMapping
    public ResponseEntity<?> addToWatchlist(@Valid @RequestBody WatchlistRequestDto requestDto,
                                          BindingResult bindingResult,
                                          HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            
            // 입력 검증 오류 확인
            if (bindingResult.hasErrors()) {
                Map<String, String> errors = new HashMap<>();
                bindingResult.getFieldErrors().forEach(error -> 
                    errors.put(error.getField(), error.getDefaultMessage())
                );
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "입력 값이 올바르지 않습니다",
                    "errors", errors
                ));
            }
            
            WatchlistResponseDto result = watchlistService.addToWatchlist(userId, requestDto);
            
            log.info("Added stock to watchlist - userId: {}, symbol: {}", userId, requestDto.getSymbol());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "관심 종목에 성공적으로 추가했습니다",
                "data", result
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Failed to add to watchlist: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error adding to watchlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 관심 종목 제거
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> removeFromWatchlist(@PathVariable String symbol,
                                               HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            
            watchlistService.removeFromWatchlist(userId, symbol);
            
            log.info("Removed stock from watchlist - userId: {}, symbol: {}", userId, symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "관심 종목에서 성공적으로 제거했습니다"
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Failed to remove from watchlist: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error removing from watchlist", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 특정 주식이 관심 종목에 있는지 확인
     */
    @GetMapping("/check/{symbol}")
    public ResponseEntity<?> checkInWatchlist(@PathVariable String symbol,
                                            HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            
            boolean isInWatchlist = watchlistService.isInWatchlist(userId, symbol);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "inWatchlist", isInWatchlist,
                "symbol", symbol.toUpperCase()
            ));
            
        } catch (Exception e) {
            log.error("Error checking watchlist status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 관심 종목 개수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<?> getWatchlistCount(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            
            long count = watchlistService.getWatchlistCount(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", count
            ));
            
        } catch (Exception e) {
            log.error("Error getting watchlist count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }
} 