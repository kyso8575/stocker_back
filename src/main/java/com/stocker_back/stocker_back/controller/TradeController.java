package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Trade 데이터 관련 REST API 컨트롤러
 * - 실시간 거래 데이터 조회
 * - WebSocket 연결 관리 (시장 시간 기반 스케줄링)
 * - 거래 통계 및 가격 정보
 */
@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledWebSocketService scheduledWebSocketService;
    
    // ===== 스케줄링된 WebSocket 관리 API =====
    
    /**
     * 스케줄링된 WebSocket 서비스 상태 확인
     * - 시장 시간 기반 자동 연결/해제
     * - 10초마다 연결 상태 모니터링
     */
    @GetMapping("/scheduled-websocket/status")
    public ResponseEntity<Map<String, Object>> getScheduledWebSocketStatus() {
        return ResponseEntity.ok(Map.of(
            "enabled", scheduledWebSocketService.isScheduledWebSocketEnabled(),
            "isMarketHours", scheduledWebSocketService.isMarketHours(),
            "isConnected", scheduledWebSocketService.isWebSocketConnected(),
            "nextMarketEvent", scheduledWebSocketService.getNextMarketEvent(),
            "description", "Automated WebSocket management during US market hours (9:30 AM - 4:00 PM ET)",
            "monitoringInterval", "10 seconds",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * 스케줄링된 WebSocket 서비스 활성화/비활성화
     */
    @PostMapping("/scheduled-websocket/toggle")
    public ResponseEntity<Map<String, Object>> toggleScheduledWebSocket(
            @RequestParam boolean enabled) {
        try {
            scheduledWebSocketService.setScheduledWebSocketEnabled(enabled);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "enabled", enabled,
                "isMarketHours", scheduledWebSocketService.isMarketHours(),
                "message", "Scheduled WebSocket service " + (enabled ? "enabled" : "disabled"),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to toggle scheduled WebSocket service", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to toggle scheduled WebSocket service: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 현재 미국 시장 시간 확인
     */
    @GetMapping("/market-hours")
    public ResponseEntity<Map<String, Object>> getMarketHours() {
        return ResponseEntity.ok(Map.of(
            "isMarketOpen", scheduledWebSocketService.isMarketHours(),
            "nextEvent", scheduledWebSocketService.getNextMarketEvent(),
            "timezone", "America/New_York (ET)",
            "regularHours", "9:30 AM - 4:00 PM (Monday-Friday)",
            "description", "US stock market trading hours",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    // ===== 수동 WebSocket 관리 API (테스트용) =====
    
    /**
     * 멀티키 WebSocket 연결 상태 확인
     */
    @GetMapping("/websocket/multi-status")
    public ResponseEntity<Map<String, Object>> getMultiWebSocketStatus() {
        Map<String, Boolean> connectionStatus = multiKeyWebSocketService.getConnectionStatus();
        boolean anyConnected = multiKeyWebSocketService.isAnyConnected();
        
        return ResponseEntity.ok(Map.of(
            "anyConnected", anyConnected,
            "connections", connectionStatus,
            "totalConnections", connectionStatus.size(),
            "activeConnections", connectionStatus.values().stream().mapToLong(b -> b ? 1 : 0).sum(),
            "description", "Manual WebSocket connection status (for testing)",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * 수동 WebSocket 연결 시작 (테스트용)
     */
    @PostMapping("/websocket/multi-connect")
    public ResponseEntity<Map<String, String>> connectMultiWebSocket() {
        try {
            multiKeyWebSocketService.connectAll();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Manual WebSocket connections initiated (for testing)",
                "note", "Use scheduled service for production"
            ));
        } catch (Exception e) {
            log.error("Failed to connect Multi-Key WebSocket", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to connect Multi-Key WebSocket: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 수동 WebSocket 연결 해제 (테스트용)
     */
    @PostMapping("/websocket/multi-disconnect")
    public ResponseEntity<Map<String, String>> disconnectMultiWebSocket() {
        try {
            multiKeyWebSocketService.disconnectAll();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Manual WebSocket connections disconnected (for testing)"
            ));
        } catch (Exception e) {
            log.error("Failed to disconnect Multi-Key WebSocket", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to disconnect Multi-Key WebSocket: " + e.getMessage()
            ));
        }
    }
    
    // ===== 거래 데이터 조회 API =====
    
    /**
     * 특정 심볼의 최신 거래 데이터 조회
     */
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<Map<String, Object>> getLatestTrades(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Trade> trades = tradeRepository.findLatestTradesBySymbol(symbol.toUpperCase());
            // limit 적용
            List<Trade> limitedTrades = trades.stream().limit(limit).toList();
            
            return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "trades", limitedTrades,
                "count", limitedTrades.size(),
                "limit", limit,
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get latest trades for symbol: {}", symbol, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve trade data",
                "symbol", symbol.toUpperCase(),
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 특정 심볼의 최신 가격 조회
     */
    @GetMapping("/{symbol}/latest-price")
    public ResponseEntity<Map<String, Object>> getLatestPrice(@PathVariable String symbol) {
        try {
            BigDecimal latestPrice = tradeRepository.findLatestPriceBySymbol(symbol.toUpperCase());
            if (latestPrice != null) {
                return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "price", latestPrice,
                    "currency", "USD",
                    "timestamp", LocalDateTime.now()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "price", null,
                    "message", "No price data available",
                    "timestamp", LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.error("❌ Failed to get latest price for symbol: {}", symbol, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve price data",
                "symbol", symbol.toUpperCase(),
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 시간 범위별 거래 데이터 조회
     */
    @GetMapping("/range")
    public ResponseEntity<Map<String, Object>> getTradesByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<Trade> trades = tradeRepository.findTradesBetween(startTime, endTime);
            return ResponseEntity.ok(Map.of(
                "trades", trades,
                "count", trades.size(),
                "startTime", startTime,
                "endTime", endTime,
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get trades by time range", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve trade data by time range",
                "startTime", startTime,
                "endTime", endTime,
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 심볼별 거래 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTradeStatistics() {
        try {
            List<Object[]> results = tradeRepository.countTradesBySymbol();
            List<Map<String, Object>> statistics = results.stream()
                    .map(result -> Map.of(
                        "symbol", result[0],
                        "tradeCount", result[1]
                    ))
                    .toList();
            
            long totalTrades = statistics.stream()
                    .mapToLong(stat -> (Long) stat.get("tradeCount"))
                    .sum();
                    
            return ResponseEntity.ok(Map.of(
                "statistics", statistics,
                "symbolCount", statistics.size(),
                "totalTrades", totalTrades,
                "description", "Trade count by symbol",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get trade statistics", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve trade statistics",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 모든 거래 데이터 페이징 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTrades(Pageable pageable) {
        try {
            Page<Trade> trades = tradeRepository.findAll(pageable);
            return ResponseEntity.ok(Map.of(
                "trades", trades.getContent(),
                "pagination", Map.of(
                    "page", trades.getNumber(),
                    "size", trades.getSize(),
                    "totalPages", trades.getTotalPages(),
                    "totalElements", trades.getTotalElements(),
                    "hasNext", trades.hasNext(),
                    "hasPrevious", trades.hasPrevious()
                ),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get all trades", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve paginated trade data",
                "message", e.getMessage()
            ));
        }
    }
} 