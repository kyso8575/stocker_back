package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.WatchlistRequestDto;
import com.stocker_back.stocker_back.dto.WatchlistResponseDto;
import com.stocker_back.stocker_back.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Watchlist", description = "관심 종목 관리 API")
public class WatchlistController {
    
    private final WatchlistService watchlistService;
    
    @Operation(
        summary = "관심 종목 목록 조회",
        description = "로그인한 사용자의 관심 종목 목록을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = WatchlistResponseDto.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
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
    
    @Operation(
        summary = "관심 종목 추가",
        description = "새로운 주식을 관심 종목에 추가합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "추가 성공",
            content = @Content(schema = @Schema(implementation = WatchlistResponseDto.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<?> addToWatchlist(
        @Parameter(description = "추가할 주식 정보", required = true)
        @Valid @RequestBody WatchlistRequestDto requestDto,
                                          BindingResult bindingResult,
        HttpServletRequest request
    ) {
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
    
    @Operation(
        summary = "관심 종목 제거",
        description = "관심 종목에서 특정 주식을 제거합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "제거 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "404", description = "관심 종목에 없는 주식"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> removeFromWatchlist(
        @Parameter(description = "제거할 주식 심볼", required = true, example = "AAPL")
        @PathVariable String symbol,
        HttpServletRequest request
    ) {
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
    
    @Operation(
        summary = "관심 종목 확인",
        description = "특정 주식이 관심 종목에 있는지 확인합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "확인 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/check/{symbol}")
    public ResponseEntity<?> checkInWatchlist(
        @Parameter(description = "확인할 주식 심볼", required = true, example = "AAPL")
        @PathVariable String symbol,
        HttpServletRequest request
    ) {
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
    
    @Operation(
        summary = "관심 종목 개수 조회",
        description = "사용자의 관심 종목 개수를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
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