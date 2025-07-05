package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 거래 데이터 관련 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@Tag(name = "Trade", description = "거래 데이터 및 웹소켓 관리 API")
public class TradeController {
    
    private final TradeService tradeService;
    
    // ===== 거래 데이터 조회 API =====
    
    /**
     * 특정 심볼의 최신 거래 데이터 조회
     */
    @GetMapping("/latest/{symbol}")
    public ResponseEntity<?> getLatestTradesBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Received request to get latest trades for symbol: {} with limit: {}", symbol, limit);
        
        try {
            List<Trade> trades = tradeService.getLatestTradesBySymbol(symbol, limit);
            
            if (trades.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    ResponseMessages.format(ResponseMessages.TEMPLATE_NOT_FOUND_FOR_SYMBOL, symbol.toUpperCase())
                ));
            }
            
            Map<String, Object> data = Map.of(
                "symbol", symbol.toUpperCase(),
                "data", trades,
                "count", trades.size(),
                "limit", limit
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_RETRIEVED_FOR_SYMBOL, symbol.toUpperCase()),
                data
            ));
        } catch (Exception e) {
            log.error("Failed to get latest trades for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * 특정 심볼의 최신 가격 조회
     */
    @GetMapping("/{symbol}/price")
    public ResponseEntity<?> getLatestPrice(@PathVariable String symbol) {
        log.info("Received request to get latest price for symbol: {}", symbol);
        
        try {
            BigDecimal latestPrice = tradeService.getLatestPriceBySymbol(symbol);
            
            if (latestPrice == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(
                    ResponseMessages.format(ResponseMessages.TEMPLATE_NOT_FOUND_FOR_SYMBOL, symbol.toUpperCase())
                ));
            }
            
            Map<String, Object> data = Map.of(
                "symbol", symbol.toUpperCase(),
                "price", latestPrice,
                "currency", "USD"
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_RETRIEVED_FOR_SYMBOL, symbol.toUpperCase()),
                data
            ));
        } catch (Exception e) {
            log.error("Failed to get latest price for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * 시간 범위별 거래 데이터 조회
     */
    @GetMapping("/history")
    public ResponseEntity<?> getTradeHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String symbol) {
        log.info("Received request to get trade history from {} to {} for symbol: {}", from, to, symbol);
        
        try {
            List<Trade> trades = tradeService.getTradeHistory(from, to, symbol);
            
            Map<String, Object> data = Map.of(
                "data", trades,
                "count", trades.size(),
                "from", from,
                "to", to,
                "symbol", symbol != null ? symbol.toUpperCase() : "All"
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_RETRIEVED_COUNT, trades.size()),
                data
            ));
        } catch (Exception e) {
            log.error("Failed to get trade history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    // ===== WebSocket 관리 API =====
    
    @Operation(
        summary = "웹소켓 연결 상태 조회",
        description = "실시간 거래 데이터 수집을 위한 웹소켓 연결 상태를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "웹소켓 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/websocket/status")
    public ResponseEntity<?> getWebSocketStatus() {
        log.info("Received request to get WebSocket status");
        
        try {
            Map<String, Object> statusData = tradeService.getWebSocketStatus();
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                statusData
            ));
        } catch (Exception e) {
            log.error("Failed to get WebSocket status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(
        summary = "웹소켓 연결 시작",
        description = "실시간 거래 데이터 수집을 위한 웹소켓 연결을 시작합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "웹소켓 연결 시작 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/websocket/admin/connect")
    public ResponseEntity<?> connectWebSocket() {
        log.info("Received request to connect WebSocket");
        
        try {
            tradeService.connectWebSocket();
            Map<String, Object> statusData = tradeService.getWebSocketStatus();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                "WebSocket connections initiated successfully",
                statusData
            ));
        } catch (Exception e) {
            log.error("Failed to connect WebSocket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(
        summary = "웹소켓 연결 해제",
        description = "실시간 거래 데이터 수집을 위한 웹소켓 연결을 해제합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "웹소켓 연결 해제 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/websocket/admin/disconnect")
    public ResponseEntity<?> disconnectWebSocket() {
        log.info("Received request to disconnect WebSocket");
        
        try {
            tradeService.disconnectWebSocket();
            
            return ResponseEntity.ok(AuthResponseDto.success(
                "WebSocket connections disconnected successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to disconnect WebSocket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 