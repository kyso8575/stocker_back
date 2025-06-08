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
 * SSE 실시간 거래 데이터 스트리밍 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/stocks/trades/stream")
@RequiredArgsConstructor
public class SimpleSSEController {
    
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // 활성 SSE 연결 관리
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();
    
    /**
     * 특정 심볼의 실시간 거래 데이터 스트리밍
     * 
     * @param symbol 심볼 (예: AAPL)
     * @param interval 업데이트 간격 (초, 기본: 5초)
     */
    @GetMapping(value = "/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTradesBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5") int interval) {
        
        String upperSymbol = symbol.toUpperCase();
        String connectionId = upperSymbol + "_" + System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        activeConnections.put(connectionId, emitter);
        
        log.info("📡 새로운 SSE 연결: {} ({}초 간격)", upperSymbol, interval);
        
        // 연결 해제 시 정리
        emitter.onCompletion(() -> {
            activeConnections.remove(connectionId);
            log.info("✅ SSE 연결 완료: {}", upperSymbol);
        });
        
        emitter.onTimeout(() -> {
            activeConnections.remove(connectionId);
            log.info("⏰ SSE 연결 타임아웃: {}", upperSymbol);
        });
        
        emitter.onError(throwable -> {
            activeConnections.remove(connectionId);
            log.error("❌ SSE 연결 에러: {}", upperSymbol, throwable);
        });
        
        // 즉시 첫 번째 데이터 전송
        sendLatestTradeData(emitter, upperSymbol, true);
        
        // 주기적으로 데이터 전송
        scheduler.scheduleAtFixedRate(() -> {
            if (activeConnections.containsKey(connectionId)) {
                sendLatestTradeData(emitter, upperSymbol, false);
            }
        }, interval, interval, TimeUnit.SECONDS);
        
        return emitter;
    }
    
    // ===== Private Helper Methods =====
    
    /**
     * 특정 심볼의 최신 거래 데이터 전송
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
            activeConnections.values().remove(emitter);
        } catch (Exception e) {
            log.error("거래 데이터 처리 에러: {}", symbol, e);
        }
    }
} 