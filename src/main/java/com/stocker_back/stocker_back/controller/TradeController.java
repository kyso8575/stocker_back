package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.ScheduledWebSocketService;
import com.stocker_back.stocker_back.dto.FinnhubTradeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/stocks/info/trades")
@RequiredArgsConstructor
public class TradeController {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledWebSocketService scheduledWebSocketService;
    
    // ===== WebSocket 상태 및 관리 API =====
    
    /**
     * 1. WebSocket 연결 상태 확인
     * 멀티키 WebSocket 연결 상태 확인
     */
    @GetMapping("/websocket/status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus() {
        Map<String, Boolean> connectionStatus = multiKeyWebSocketService.getConnectionStatus();
        return ResponseEntity.ok(Map.of(
            "connections", connectionStatus,
            "totalConnections", connectionStatus.size(),
            "activeConnections", connectionStatus.values().stream().mapToLong(b -> b ? 1 : 0).sum(),
            "anyConnected", multiKeyWebSocketService.isAnyConnected(),
            "description", "Multi-key WebSocket connection status for manual control",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * 2. 스케줄링된 WebSocket 상태 확인
     * 시장 시간 기반 자동 연결/해제 서비스 상태
     */
    @GetMapping("/websocket/schedule_status")
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
     * 3. WebSocket 연결 시작
     * 수동으로 WebSocket 연결 시작
     */
    @PostMapping("/websocket/connect")
    public ResponseEntity<Map<String, Object>> connectWebSocket() {
        try {
            multiKeyWebSocketService.connectAll();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Manual WebSocket connections initiated",
                "connections", multiKeyWebSocketService.getConnectionStatus(),
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to connect Multi-Key WebSocket", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to connect Multi-Key WebSocket: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 4. WebSocket 연결 해제
     * 수동으로 WebSocket 연결 해제
     */
    @PostMapping("/websocket/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectWebSocket() {
        try {
            multiKeyWebSocketService.disconnectAll();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Manual WebSocket connections disconnected",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to disconnect Multi-Key WebSocket", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to disconnect Multi-Key WebSocket: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 5. WebSocket 구독 관리
     * 특정 심볼 구독 추가 (현재는 고정된 S&P 500 심볼을 사용하므로 정보성 엔드포인트)
     */
    @PostMapping("/websocket/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeSymbol(@RequestParam String symbol) {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "info",
                "message", "This system uses fixed S&P 500 symbol subscriptions. Symbol: " + symbol.toUpperCase(),
                "note", "To modify subscriptions, restart the WebSocket connections",
                "currentSubscriptions", "Fixed S&P 500 symbols (alphabetically distributed)",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Invalid symbol: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 6. WebSocket 구독 해제
     * 특정 심볼 구독 해제 (현재는 고정된 S&P 500 심볼을 사용하므로 정보성 엔드포인트)
     */
    @PostMapping("/websocket/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribeSymbol(@RequestParam String symbol) {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "info",
                "message", "This system uses fixed S&P 500 symbol subscriptions. Symbol: " + symbol.toUpperCase(),
                "note", "To modify subscriptions, restart the WebSocket connections",
                "currentSubscriptions", "Fixed S&P 500 symbols (alphabetically distributed)",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Invalid symbol: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    // ===== 거래 데이터 조회 API =====
    
    /**
     * 1. 최신 거래 데이터 조회 (전체 또는 특정 심볼)
     * ?symbol=All - 모든 심볼의 최신 거래 데이터
     * ?symbol=AAPL - 특정 심볼의 최신 거래 데이터
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestTrades(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            if ("All".equalsIgnoreCase(symbol)) {
                // 모든 심볼의 최신 거래 데이터
                List<Object[]> results = tradeRepository.countTradesBySymbol();
                List<Map<String, Object>> allLatestTrades = results.stream()
                        .limit(limit)
                        .map(result -> {
                            String sym = (String) result[0];
                            List<Trade> trades = tradeRepository.findLatestTradesBySymbol(sym);
                            return Map.of(
                                "symbol", sym,
                                "latestTrade", trades.isEmpty() ? null : trades.get(0),
                                "tradeCount", result[1]
                            );
                        })
                        .toList();
                
                return ResponseEntity.ok(Map.of(
                    "symbol", "All",
                    "trades", allLatestTrades,
                    "count", allLatestTrades.size(),
                    "limit", limit,
                    "description", "Latest trade data for all symbols",
                    "timestamp", LocalDateTime.now()
                ));
            } else {
                // 특정 심볼의 최신 거래 데이터
                List<Trade> trades = tradeRepository.findLatestTradesBySymbol(symbol.toUpperCase());
                List<Trade> limitedTrades = trades.stream().limit(limit).toList();
                
                return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "trades", limitedTrades,
                    "count", limitedTrades.size(),
                    "limit", limit,
                    "description", "Latest trade data for specific symbol",
                    "timestamp", LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.error("❌ Failed to get latest trades for symbol: {}", symbol, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve trade data",
                "symbol", symbol,
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 2. 시간 범위별 거래 데이터 조회
     */
    @GetMapping("/range")
    public ResponseEntity<Map<String, Object>> getTradesByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String symbol) {
        try {
            List<Trade> trades;
            if (symbol != null && !symbol.isEmpty() && !"All".equalsIgnoreCase(symbol)) {
                // 특정 심볼의 시간 범위 데이터는 별도 쿼리 필요 (현재 구현되지 않음)
                trades = tradeRepository.findTradesBetween(startTime, endTime)
                        .stream()
                        .filter(trade -> trade.getSymbol().equalsIgnoreCase(symbol))
                        .toList();
            } else {
                // 모든 심볼의 시간 범위 데이터
                trades = tradeRepository.findTradesBetween(startTime, endTime);
            }
            
            return ResponseEntity.ok(Map.of(
                "trades", trades,
                "count", trades.size(),
                "startTime", startTime,
                "endTime", endTime,
                "symbol", symbol != null ? symbol : "All",
                "description", "Trade data within specified time range",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get trades by time range", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve trade data by time range",
                "startTime", startTime,
                "endTime", endTime,
                "symbol", symbol,
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    // ===== 추가 유틸리티 API =====
    
    /**
     * 특정 심볼의 최신 가격 조회
     */
    @GetMapping("/{symbol}/latest-price")
    public ResponseEntity<Map<String, Object>> getLatestPrice(@PathVariable String symbol) {
        try {
            BigDecimal latestPrice = tradeRepository.findLatestPriceBySymbol(symbol.toUpperCase());
            return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "price", latestPrice,
                "currency", "USD",
                "description", "Latest trade price for symbol",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to get latest price for symbol: {}", symbol, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to retrieve price data",
                "symbol", symbol.toUpperCase(),
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now()
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
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now()
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
    
    /**
     * 스케줄링된 WebSocket 서비스 활성화/비활성화
     */
    @PostMapping("/websocket/schedule-toggle")
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
                "message", "Failed to toggle scheduled WebSocket service: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 심볼별 저장 상태 조회 (10초 간격 저장 정보)
     */
    @GetMapping("/websocket/save-status")
    public ResponseEntity<Map<String, Object>> getSaveStatus() {
        try {
            Map<String, Object> saveStatus = multiKeyWebSocketService.getSaveStatusSummary();
            Map<String, LocalDateTime> lastSaveTimes = multiKeyWebSocketService.getLastSaveTimeBySymbol();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "saveInterval", multiKeyWebSocketService.getSaveIntervalSeconds() + " seconds",
                "summary", saveStatus,
                "recentSaves", lastSaveTimes.entrySet().stream()
                    .sorted(Map.Entry.<String, LocalDateTime>comparingByValue().reversed())
                    .limit(10)
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toString(),
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                    )),
                "description", "Symbol-based save status with 10-second interval",
                "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Failed to get save status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to retrieve save status: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 메모리의 최신 거래 데이터 조회 (실시간 데이터)
     */
    @GetMapping("/websocket/latest-memory")
    public ResponseEntity<Map<String, Object>> getLatestMemoryData(
            @RequestParam(required = false) String symbol) {
        try {
            Map<String, FinnhubTradeDTO.TradeData> latestTrades = multiKeyWebSocketService.getLatestTradeBySymbol();
            
            if (symbol != null && !symbol.isEmpty() && !"All".equalsIgnoreCase(symbol)) {
                // 특정 심볼의 메모리 데이터
                FinnhubTradeDTO.TradeData tradeData = latestTrades.get(symbol.toUpperCase());
                if (tradeData != null) {
                    return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "symbol", symbol.toUpperCase(),
                        "latestTrade", tradeData,
                        "source", "memory (real-time)",
                        "description", "Latest trade data from WebSocket memory",
                        "timestamp", LocalDateTime.now()
                    ));
                } else {
                    return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "symbol", symbol.toUpperCase(),
                        "latestTrade", null,
                        "message", "No data available for this symbol",
                        "timestamp", LocalDateTime.now()
                    ));
                }
            } else {
                // 모든 심볼의 메모리 데이터 요약
                List<Map<String, Object>> summary = latestTrades.entrySet().stream()
                    .limit(20) // 상위 20개만
                    .map(entry -> {
                        Map<String, Object> tradeInfo = new java.util.HashMap<>();
                        tradeInfo.put("symbol", entry.getKey());
                        tradeInfo.put("price", entry.getValue().getPrice());
                        tradeInfo.put("volume", entry.getValue().getVolume());
                        tradeInfo.put("timestamp", entry.getValue().getTimestamp());
                        return tradeInfo;
                    })
                    .toList();
                
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "totalSymbols", latestTrades.size(),
                    "samples", summary,
                    "source", "memory (real-time)",
                    "description", "Latest trade data from WebSocket memory (top 20 symbols)",
                    "timestamp", LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to get latest memory data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to retrieve memory data: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
} 