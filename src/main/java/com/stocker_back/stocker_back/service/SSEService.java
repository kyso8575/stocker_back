package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.dto.FinnhubTradeDTO;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SSEService {

    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;
    private final TradeRepository tradeRepository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // 활성 SSE 연결 관리
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();

    public SseEmitter createSSEConnection(String symbol, int interval) {
        String upperSymbol = symbol.toUpperCase();
        String connectionId = upperSymbol + "_" + System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        activeConnections.put(connectionId, emitter);
        
        log.info("📡 새로운 SSE 연결: {} ({}초 간격)", upperSymbol, interval);
        
        // 연결 해제 시 정리
        setupEmitterCallbacks(emitter, connectionId, upperSymbol);
        
        // 즉시 첫 번째 데이터 전송
        sendLatestTradeData(emitter, upperSymbol, true);
        
        // 주기적으로 데이터 전송
        scheduleDataUpdates(emitter, connectionId, upperSymbol, interval);
        
        return emitter;
    }

    private void setupEmitterCallbacks(SseEmitter emitter, String connectionId, String symbol) {
        emitter.onCompletion(() -> {
            activeConnections.remove(connectionId);
            log.info("✅ SSE 연결 완료: {}", symbol);
        });
        
        emitter.onTimeout(() -> {
            activeConnections.remove(connectionId);
            log.info("⏰ SSE 연결 타임아웃: {}", symbol);
        });
        
        emitter.onError(throwable -> {
            activeConnections.remove(connectionId);
            log.error("❌ SSE 연결 에러: {}", symbol, throwable);
        });
    }

    private void scheduleDataUpdates(SseEmitter emitter, String connectionId, String symbol, int interval) {
        scheduler.scheduleAtFixedRate(() -> {
            if (activeConnections.containsKey(connectionId)) {
                sendLatestTradeData(emitter, symbol, false);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void sendLatestTradeData(SseEmitter emitter, String symbol, boolean isInitial) {
        try {
            Map<String, Object> data = createTradeDataPayload(symbol, isInitial);
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

    private Map<String, Object> createTradeDataPayload(String symbol, boolean isInitial) {
        Map<String, Object> data = new HashMap<>();
        data.put("symbol", symbol);
        data.put("timestamp", LocalDateTime.now());
        data.put("type", isInitial ? "initial" : "update");
        
        // 1. 메모리에서 실시간 데이터 확인
        Map<String, FinnhubTradeDTO.TradeData> latestTrades = 
                multiKeyWebSocketService.getLatestTradeBySymbol();
        
        FinnhubTradeDTO.TradeData realtimeData = latestTrades.get(symbol);
        
        if (realtimeData != null) {
            data.put("trade", createTradeDataMap(realtimeData, "realtime"));
        } else {
            // 2. DB에서 최신 데이터 조회
            List<Trade> dbTrades = tradeRepository.findLatestTradesBySymbol(symbol);
            if (!dbTrades.isEmpty()) {
                Trade latestTrade = dbTrades.get(0);
                data.put("trade", createTradeDataMap(latestTrade, "database"));
            } else {
                data.put("trade", null);
                data.put("message", "데이터를 찾을 수 없습니다");
            }
        }
        
        return data;
    }

    private Map<String, Object> createTradeDataMap(FinnhubTradeDTO.TradeData tradeData, String source) {
        return Map.of(
                "price", tradeData.getPrice(),
                "volume", tradeData.getVolume(),
                "timestamp", tradeData.getTimestamp(),
                "conditions", tradeData.getConditions(),
                "source", source
        );
    }

    private Map<String, Object> createTradeDataMap(Trade trade, String source) {
        List<String> conditions = trade.getTradeConditions() != null ? 
                Arrays.asList(trade.getTradeConditions().split(",")) : 
                Collections.emptyList();
        
        return Map.of(
                "price", trade.getPrice(),
                "volume", trade.getVolume(),
                "timestamp", trade.getTimestamp(),
                "conditions", conditions,
                "source", source
        );
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public void closeAllConnections() {
        activeConnections.values().forEach(SseEmitter::complete);
        activeConnections.clear();
    }
} 