package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.dto.FinnhubTradeDTO;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 간단한 SSE 컨트롤러 - 특정 심볼의 최신 거래 데이터를 실시간으로 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SimpleSSEController {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // 활성 SSE 연결 관리
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();
    
    /**
     * 통합된 거래 데이터 SSE 스트리밍
     * 
     * @param symbols 심볼들 (단일: "AAPL" 또는 다중: "AAPL,MSFT,GOOGL")
     * @param interval 업데이트 간격 (초, 기본: 5초)
     */
    @GetMapping(value = "/trades", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTrades(
            @RequestParam String symbols,
            @RequestParam(defaultValue = "5") int interval) {
        
        // 심볼 파싱
        List<String> symbolList = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .toList();
        
        if (symbolList.isEmpty()) {
            throw new IllegalArgumentException("최소 하나의 심볼이 필요합니다");
        }
        
        String connectionId = String.join(",", symbolList) + "_" + System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        activeConnections.put(connectionId, emitter);
        
        boolean isSingle = symbolList.size() == 1;
        log.info("📡 새로운 SSE 연결: {} ({}초 간격, {} 모드)", 
                symbolList, interval, isSingle ? "단일" : "다중");
        
        // 연결 해제 시 정리
        emitter.onCompletion(() -> {
            activeConnections.remove(connectionId);
            log.info("✅ SSE 연결 완료: {}", symbolList);
        });
        
        emitter.onTimeout(() -> {
            activeConnections.remove(connectionId);
            log.info("⏰ SSE 연결 타임아웃: {}", symbolList);
        });
        
        emitter.onError(throwable -> {
            activeConnections.remove(connectionId);
            log.error("❌ SSE 연결 에러: {}", symbolList, throwable);
        });
        
        // 즉시 첫 번째 데이터 전송
        if (isSingle) {
            sendLatestTradeData(emitter, symbolList.get(0), true);
        } else {
            sendMultipleLatestTradeData(emitter, symbolList, true);
        }
        
        // 주기적으로 데이터 전송
        scheduler.scheduleAtFixedRate(() -> {
            if (activeConnections.containsKey(connectionId)) {
                if (isSingle) {
                    sendLatestTradeData(emitter, symbolList.get(0), false);
                } else {
                    sendMultipleLatestTradeData(emitter, symbolList, false);
                }
            }
        }, interval, interval, TimeUnit.SECONDS);
        
        return emitter;
    }
    
    /**
     * 현재 활성 SSE 연결 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSSEStatus() {
        return ResponseEntity.ok(Map.of(
                "activeConnections", activeConnections.size(),
                "connectionIds", activeConnections.keySet(),
                "websocketBackendStatus", multiKeyWebSocketService.isAnyConnected(),
                "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * 특정 심볼의 최신 거래 데이터를 한번만 조회 (SSE 아님)
     */
    @GetMapping("/trades/{symbol}/latest")
    public ResponseEntity<Map<String, Object>> getLatestTrade(@PathVariable String symbol) {
        String upperSymbol = symbol.toUpperCase();
        
        // 1. 먼저 메모리에서 실시간 데이터 확인
        Map<String, FinnhubTradeDTO.TradeData> latestTrades = 
                multiKeyWebSocketService.getLatestTradeBySymbol();
        
        FinnhubTradeDTO.TradeData realtimeData = latestTrades.get(upperSymbol);
        
        if (realtimeData != null) {
            return ResponseEntity.ok(Map.of(
                    "symbol", upperSymbol,
                    "price", realtimeData.getPrice(),
                    "volume", realtimeData.getVolume(),
                    "timestamp", realtimeData.getTimestamp(),
                    "conditions", realtimeData.getConditions(),
                    "source", "realtime",
                    "retrievedAt", LocalDateTime.now()
            ));
        }
        
        // 2. 메모리에 없으면 DB에서 조회
        List<Trade> dbTrades = tradeRepository.findLatestTradesBySymbol(upperSymbol);
        if (!dbTrades.isEmpty()) {
            Trade latestTrade = dbTrades.get(0);
            List<String> conditions = latestTrade.getTradeConditions() != null ? 
                    Arrays.asList(latestTrade.getTradeConditions().split(",")) : 
                    Collections.emptyList();
            
            return ResponseEntity.ok(Map.of(
                    "symbol", upperSymbol,
                    "price", latestTrade.getPrice(),
                    "volume", latestTrade.getVolume(),
                    "timestamp", latestTrade.getTimestamp(),
                    "conditions", conditions,
                    "source", "database",
                    "retrievedAt", LocalDateTime.now()
            ));
        }
        
        // 3. 데이터가 없는 경우
        return ResponseEntity.ok(Map.of(
                "symbol", upperSymbol,
                "message", "데이터를 찾을 수 없습니다",
                "source", "none",
                "retrievedAt", LocalDateTime.now()
        ));
    }
    
    // ===== Private Helper Methods =====
    
    /**
     * 단일 심볼의 최신 거래 데이터 전송
     */
    private void sendLatestTradeData(SseEmitter emitter, String symbol, boolean isInitial) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbol", symbol);
            data.put("timestamp", LocalDateTime.now());
            data.put("type", isInitial ? "initial" : "update");
            
            // 1. 메모리에서 실시간 데이터 확인
            Map<String, FinnhubTradeDTO.TradeData> latestTrades = 
                    multiKeyWebSocketService.getLatestTradeBySymbol();
            
            FinnhubTradeDTO.TradeData realtimeData = latestTrades.get(symbol);
            
            if (realtimeData != null) {
                data.put("trade", Map.of(
                        "price", realtimeData.getPrice(),
                        "volume", realtimeData.getVolume(),
                        "timestamp", realtimeData.getTimestamp(),
                        "conditions", realtimeData.getConditions(),
                        "source", "realtime"
                ));
            } else {
                // 2. DB에서 최신 데이터 조회
                List<Trade> dbTrades = tradeRepository.findLatestTradesBySymbol(symbol);
                if (!dbTrades.isEmpty()) {
                    Trade latestTrade = dbTrades.get(0);
                    List<String> conditions = latestTrade.getTradeConditions() != null ? 
                            Arrays.asList(latestTrade.getTradeConditions().split(",")) : 
                            Collections.emptyList();
                    
                    data.put("trade", Map.of(
                            "price", latestTrade.getPrice(),
                            "volume", latestTrade.getVolume(),
                            "timestamp", latestTrade.getTimestamp(),
                            "conditions", conditions,
                            "source", "database"
                    ));
                } else {
                    data.put("trade", null);
                    data.put("message", "데이터를 찾을 수 없습니다");
                }
            }
            
            emitter.send(SseEmitter.event()
                    .name("trade_data")
                    .data(data));
            
        } catch (IOException e) {
            log.error("SSE 데이터 전송 실패: {}", symbol, e);
        } catch (Exception e) {
            log.error("거래 데이터 처리 에러: {}", symbol, e);
        }
    }
    
    /**
     * 여러 심볼의 최신 거래 데이터 전송
     */
    private void sendMultipleLatestTradeData(SseEmitter emitter, List<String> symbols, boolean isInitial) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("symbols", symbols);
            data.put("timestamp", LocalDateTime.now());
            data.put("type", isInitial ? "initial" : "update");
            
            Map<String, FinnhubTradeDTO.TradeData> latestTrades = 
                    multiKeyWebSocketService.getLatestTradeBySymbol();
            
            Map<String, Object> trades = new HashMap<>();
            int realtimeCount = 0;
            int databaseCount = 0;
            
            for (String symbol : symbols) {
                FinnhubTradeDTO.TradeData realtimeData = latestTrades.get(symbol);
                
                if (realtimeData != null) {
                    trades.put(symbol, Map.of(
                            "price", realtimeData.getPrice(),
                            "volume", realtimeData.getVolume(),
                            "timestamp", realtimeData.getTimestamp(),
                            "conditions", realtimeData.getConditions(),
                            "source", "realtime"
                    ));
                    realtimeCount++;
                } else {
                    // DB에서 조회
                    List<Trade> dbTrades = tradeRepository.findLatestTradesBySymbol(symbol);
                    if (!dbTrades.isEmpty()) {
                        Trade latestTrade = dbTrades.get(0);
                        List<String> conditions = latestTrade.getTradeConditions() != null ? 
                                Arrays.asList(latestTrade.getTradeConditions().split(",")) : 
                                Collections.emptyList();
                        
                        trades.put(symbol, Map.of(
                                "price", latestTrade.getPrice(),
                                "volume", latestTrade.getVolume(),
                                "timestamp", latestTrade.getTimestamp(),
                                "conditions", conditions,
                                "source", "database"
                        ));
                        databaseCount++;
                    }
                }
            }
            
            data.put("trades", trades);
            data.put("stats", Map.of(
                    "totalSymbols", symbols.size(),
                    "realtimeData", realtimeCount,
                    "databaseData", databaseCount,
                    "noData", symbols.size() - realtimeCount - databaseCount
            ));
            
            emitter.send(SseEmitter.event()
                    .name("trades_data")
                    .data(data));
            
        } catch (IOException e) {
            log.error("다중 SSE 데이터 전송 실패: {}", symbols, e);
        } catch (Exception e) {
            log.error("다중 거래 데이터 처리 에러: {}", symbols, e);
        }
    }
} 