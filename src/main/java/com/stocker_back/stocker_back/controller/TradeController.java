package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import com.stocker_back.stocker_back.dto.FinnhubTradeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 거래 데이터 관련 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/stocks/trades")
@RequiredArgsConstructor
public class TradeController {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledWebSocketService scheduledWebSocketService;
    
    // ===== 거래 데이터 조회 API =====
    
    /**
     * 특정 심볼의 최신 거래 데이터 조회
     */
    @GetMapping("/latest/{symbol}")
    public ResponseEntity<Map<String, Object>> getLatestTradesBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Trade> trades = tradeRepository.findLatestTradesBySymbol(symbol.toUpperCase());
            List<Trade> limitedTrades = trades.stream().limit(limit).toList();
            
            if (limitedTrades.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "symbol", symbol.toUpperCase(),
                    "message", "No trade data found for symbol " + symbol.toUpperCase(),
                    "timestamp", LocalDateTime.now()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", symbol.toUpperCase(),
                "data", limitedTrades,
                "count", limitedTrades.size(),
                "limit", limit,
                "message", "Successfully retrieved latest trade data for " + symbol.toUpperCase(),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to get latest trades for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "symbol", symbol.toUpperCase(),
                "error", "Failed to retrieve trade data: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 특정 심볼의 최신 가격 조회
     */
    @GetMapping("/{symbol}/price")
    public ResponseEntity<Map<String, Object>> getLatestPrice(@PathVariable String symbol) {
        try {
            BigDecimal latestPrice = tradeRepository.findLatestPriceBySymbol(symbol.toUpperCase());
            
            if (latestPrice == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "symbol", symbol.toUpperCase(),
                    "message", "No price data found for symbol " + symbol.toUpperCase(),
                    "timestamp", LocalDateTime.now()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", symbol.toUpperCase(),
                "price", latestPrice,
                "currency", "USD",
                "message", "Successfully retrieved latest price for " + symbol.toUpperCase(),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to get latest price for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "symbol", symbol.toUpperCase(),
                "error", "Failed to retrieve price data: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 시간 범위별 거래 데이터 조회
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getTradeHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String symbol) {
        try {
            List<Trade> trades;
            if (symbol != null && !symbol.isEmpty()) {
                trades = tradeRepository.findTradesBetween(from, to)
                        .stream()
                        .filter(trade -> trade.getSymbol().equalsIgnoreCase(symbol))
                        .toList();
            } else {
                trades = tradeRepository.findTradesBetween(from, to);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", trades,
                "count", trades.size(),
                "from", from,
                "to", to,
                "symbol", symbol != null ? symbol.toUpperCase() : "All",
                "message", String.format("Successfully retrieved %d trades within time range", trades.size()),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to get trade history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to retrieve trade history: " + e.getMessage(),
                "from", from,
                "to", to,
                "symbol", symbol,
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    // ===== WebSocket 관리 API =====
    
    /**
     * WebSocket 연결 상태 조회
     */
    @GetMapping("/websocket/status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus() {
        Map<String, Boolean> connectionStatus = multiKeyWebSocketService.getConnectionStatus();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "connections", connectionStatus,
            "totalConnections", connectionStatus.size(),
            "activeConnections", connectionStatus.values().stream().mapToLong(b -> b ? 1 : 0).sum(),
            "anyConnected", multiKeyWebSocketService.isAnyConnected(),
            "message", "WebSocket connection status retrieved successfully",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * WebSocket 연결 시작
     */
    @PostMapping("/websocket/connect")
    public ResponseEntity<Map<String, Object>> connectWebSocket() {
        try {
            multiKeyWebSocketService.connectAll();
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "WebSocket connections initiated successfully",
                "connections", multiKeyWebSocketService.getConnectionStatus(),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to connect WebSocket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to connect WebSocket: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * WebSocket 연결 해제
     */
    @PostMapping("/websocket/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectWebSocket() {
        try {
            multiKeyWebSocketService.disconnectAll();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "WebSocket connections disconnected successfully",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to disconnect WebSocket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Failed to disconnect WebSocket: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
} 