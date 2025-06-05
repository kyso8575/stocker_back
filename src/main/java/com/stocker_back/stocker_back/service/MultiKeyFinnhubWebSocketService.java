package com.stocker_back.stocker_back.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.dto.FinnhubSubscriptionDTO;
import com.stocker_back.stocker_back.dto.FinnhubTradeDTO;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 멀티 API 키 기반 Finnhub WebSocket 연결 관리 서비스
 * 
 * 주요 기능:
 * - 여러 API 키를 사용한 WebSocket 연결 관리
 * - S&P 500 종목 알파벳 순 고정 구독 (50개씩 분배)
 * - 실시간 거래 데이터 수신 및 데이터베이스 저장
 * - 자동 재연결 및 에러 처리
 * 
 * 구독 방식: 고정된 알파벳 순서로 connection-1(A~), connection-2(M~)...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiKeyFinnhubWebSocketService {
    
    // ===== Dependencies =====
    private final StockSymbolRepository stockSymbolRepository;
    private final TradeRepository tradeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // ===== Configuration =====
    @Value("${finnhub.api.key.1:}")
    private String apiKey1;
    
    @Value("${finnhub.api.key.2:}")
    private String apiKey2;
    
    @Value("${finnhub.api.key.3:}")
    private String apiKey3;
    
    @Value("${finnhub.websocket.url:wss://ws.finnhub.io}")
    private String websocketUrl;
    
    @Value("${finnhub.websocket.max-symbols:50}")
    private int maxSymbolsPerKey;
    
    // ===== State Management =====
    private final Map<String, WebSocketClient> webSocketClients = new HashMap<>();
    private final Map<String, Boolean> connectionStatus = new HashMap<>();
    private ScheduledExecutorService scheduler;
    
    // ===== Initialization =====
    
    @PostConstruct
    public void init() {
        this.scheduler = Executors.newScheduledThreadPool(4);
        log.info("🔧 MultiKeyFinnhubWebSocketService initialized with {} thread pool", 4);
    }
    
    // ===== Public API Methods =====
    
    /**
     * 모든 API 키로 WebSocket 연결 시작
     */
    public void connectAll() {
        List<String> apiKeys = getValidApiKeys();
        log.info("🚀 Starting connections with {} API keys", apiKeys.size());
        
        for (int i = 0; i < apiKeys.size(); i++) {
            String apiKey = apiKeys.get(i);
            String connectionId = "connection-" + (i + 1);
            connectWithApiKey(connectionId, apiKey);
            
            // 연결 간 지연 (API 제한 고려)
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Connection process interrupted");
                break;
            }
        }
        
        // 모든 연결이 완료된 후 구독 시작
        scheduler.schedule(this::subscribeToSp500Stocks, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 모든 WebSocket 연결 해제
     */
    public void disconnectAll() {
        log.info("🔌 Disconnecting all WebSocket connections...");
        
        webSocketClients.forEach((connectionId, client) -> {
            if (client != null && client.getReadyState() == ReadyState.OPEN) {
                log.info("🔌 Closing connection [{}]", connectionId);
                client.close();
            }
        });
        
        // 연결 상태 초기화
        connectionStatus.replaceAll((k, v) -> false);
        
        log.info("✅ All WebSocket connections closed");
    }
    
    /**
     * 연결 상태 조회
     */
    public Map<String, Boolean> getConnectionStatus() {
        return new HashMap<>(connectionStatus);
    }
    
    /**
     * 하나 이상의 연결이 활성화되어 있는지 확인
     */
    public boolean isAnyConnected() {
        return connectionStatus.values().stream().anyMatch(Boolean::booleanValue);
    }
    
    // ===== Private Implementation Methods =====
    
    /**
     * 유효한 API 키 목록 반환
     */
    private List<String> getValidApiKeys() {
        List<String> keys = new ArrayList<>();
        
        if (apiKey1 != null && !apiKey1.trim().isEmpty()) {
            keys.add(apiKey1.trim());
        }
        if (apiKey2 != null && !apiKey2.trim().isEmpty()) {
            keys.add(apiKey2.trim());
        }
        if (apiKey3 != null && !apiKey3.trim().isEmpty()) {
            keys.add(apiKey3.trim());
        }
        
        log.debug("📋 Found {} valid API keys", keys.size());
        return keys;
    }
    
    /**
     * 특정 API 키로 WebSocket 연결
     */
    private void connectWithApiKey(String connectionId, String apiKey) {
        try {
            String wsUri = websocketUrl + "?token=" + apiKey;
            URI serverUri = URI.create(wsUri);
            
            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("✅ Connected to Finnhub WebSocket [{}]", connectionId);
                    connectionStatus.put(connectionId, true);
                }
                
                @Override
                public void onMessage(String message) {
                    handleMessage(connectionId, message);
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("⚠️ WebSocket connection [{}] closed: code={}, reason={}", 
                            connectionId, code, reason);
                    connectionStatus.put(connectionId, false);
                    scheduleReconnect(connectionId, apiKey);
                }
                
                @Override
                public void onError(Exception ex) {
                    log.error("❌ WebSocket error [{}]", connectionId, ex);
                    connectionStatus.put(connectionId, false);
                }
            };
            
            webSocketClients.put(connectionId, client);
            connectionStatus.put(connectionId, false);
            client.connect();
            
        } catch (Exception e) {
            log.error("❌ Failed to connect WebSocket [{}]", connectionId, e);
        }
    }
    
    /**
     * WebSocket 메시지 처리
     */
    private void handleMessage(String connectionId, String message) {
        try {
            // Ping 메시지 무시
            if ("{\"type\":\"ping\"}".equals(message)) {
                return;
            }
            
            // 오류 메시지 처리
            if (message.contains("\"type\":\"error\"")) {
                log.error("❌ Finnhub WebSocket error [{}]: {}", connectionId, message);
                
                if (message.contains("Subscribing to too many symbols")) {
                    log.warn("⚠️ Hit symbol subscription limit for [{}]", connectionId);
                }
                return;
            }
            
            FinnhubTradeDTO tradeDTO = objectMapper.readValue(message, FinnhubTradeDTO.class);
            
            if ("trade".equals(tradeDTO.getType()) && tradeDTO.getData() != null) {
                CompletableFuture.runAsync(() -> saveTrades(tradeDTO.getData(), connectionId));
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to parse WebSocket message [{}]: {}", connectionId, message, e);
        }
    }
    
    /**
     * 거래 데이터 저장
     */
    private void saveTrades(List<FinnhubTradeDTO.TradeData> tradeDataList, String connectionId) {
        try {
            List<Trade> trades = tradeDataList.stream()
                    .map(this::convertToTrade)
                    .filter(Objects::nonNull)
                    .toList();
            
            if (!trades.isEmpty()) {
                tradeRepository.saveAll(trades);
                log.debug("💾 Saved {} trades from [{}]", trades.size(), connectionId);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to save trades from [{}]", connectionId, e);
        }
    }
    
    /**
     * Finnhub 거래 데이터를 Trade 엔티티로 변환
     */
    private Trade convertToTrade(FinnhubTradeDTO.TradeData tradeData) {
        try {
            Long volume = tradeData.getVolume() != null ? tradeData.getVolume() : 0L;
            
            return Trade.builder()
                    .symbol(tradeData.getSymbol())
                    .price(tradeData.getPrice() != null ? tradeData.getPrice() : BigDecimal.ZERO)
                    .volume(volume)
                    .timestamp(tradeData.getTimestamp())
                    .tradeConditions(tradeData.getConditions() != null ? 
                            String.join(",", tradeData.getConditions()) : null)
                    .receivedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("❌ Failed to convert trade data: {}", tradeData, e);
            return null;
        }
    }
    
    private void subscribeToSp500Stocks() {
        try {
            Set<String> sp500Symbols = stockSymbolRepository.findAllSp500Symbols();
            
            // 알파벳 순으로 정렬하여 고정된 순서 보장
            List<String> sortedSymbolList = new ArrayList<>(sp500Symbols);
            Collections.sort(sortedSymbolList);
            
            List<String> connectedClients = webSocketClients.entrySet().stream()
                    .filter(entry -> connectionStatus.getOrDefault(entry.getKey(), false))
                    .map(Map.Entry::getKey)
                    .sorted() // 연결 순서도 정렬하여 일관성 보장
                    .toList();
            
            if (connectedClients.isEmpty()) {
                log.warn("No connected WebSocket clients available for subscription");
                return;
            }
            
            log.info("Starting FIXED alphabetical subscription for {} SP500 symbols across {} connections", 
                    sortedSymbolList.size(), connectedClients.size());
            log.info("Each connection will subscribe to EXACTLY {} symbols in alphabetical order", maxSymbolsPerKey);
            
            // 각 연결에 고정된 알파벳 범위 할당
            for (int connectionIndex = 0; connectionIndex < connectedClients.size(); connectionIndex++) {
                String connectionId = connectedClients.get(connectionIndex);
                
                // 고정된 시작/끝 인덱스 계산
                int startIndex = connectionIndex * maxSymbolsPerKey;
                int endIndex = Math.min(startIndex + maxSymbolsPerKey, sortedSymbolList.size());
                
                if (startIndex >= sortedSymbolList.size()) {
                    log.info("Connection [{}] - No more symbols to assign (start index {} >= total {})", 
                            connectionId, startIndex, sortedSymbolList.size());
                    break;
                }
                
                // 고정된 심볼 범위 추출
                List<String> fixedSymbolsForConnection = sortedSymbolList.subList(startIndex, endIndex);
                
                log.info("Connection [{}] - FIXED assignment: symbols {} to {} (indices {}-{})", 
                        connectionId, 
                        fixedSymbolsForConnection.get(0),
                        fixedSymbolsForConnection.get(fixedSymbolsForConnection.size() - 1),
                        startIndex, 
                        endIndex - 1);
                
                subscribeSymbolsToConnection(connectionId, fixedSymbolsForConnection);
            }
            
            // 총 구독된 심볼 수 계산
            int totalSubscribed = Math.min(connectedClients.size() * maxSymbolsPerKey, sortedSymbolList.size());
            int totalMissed = Math.max(0, sortedSymbolList.size() - totalSubscribed);
            
            log.info("FIXED subscription summary:");
            log.info("- Total SP500 symbols: {}", sortedSymbolList.size());
            log.info("- Symbols subscribed: {} ({})", totalSubscribed, 
                    totalSubscribed > 0 ? sortedSymbolList.get(0) + " ~ " + sortedSymbolList.get(totalSubscribed - 1) : "none");
            log.info("- Symbols NOT subscribed: {}", totalMissed);
            
            if (totalMissed > 0) {
                log.warn("WARNING: {} symbols are NOT being monitored due to API key limits", totalMissed);
                log.warn("Missing symbols start from: {}", sortedSymbolList.get(totalSubscribed));
            }
            
        } catch (Exception e) {
            log.error("Failed to subscribe to SP500 stocks", e);
        }
    }
    
    private void subscribeSymbolsToConnection(String connectionId, List<String> symbols) {
        WebSocketClient client = webSocketClients.get(connectionId);
        if (client == null || client.getReadyState() != ReadyState.OPEN) {
            log.warn("Connection [{}] is not available for subscription", connectionId);
            return;
        }
        
        log.info("Starting subscription for [{}]: {} symbols from {} to {}", 
                connectionId, symbols.size(), 
                symbols.get(0), symbols.get(symbols.size() - 1));
        
        log.info("Full symbol list for [{}]: {}", connectionId, symbols);
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            
            try {
                FinnhubSubscriptionDTO subscription = FinnhubSubscriptionDTO.subscribe(symbol);
                String message = objectMapper.writeValueAsString(subscription);
                client.send(message);
                successCount++;
                
                // 10개마다 1초 대기
                if ((i + 1) % 10 == 0) {
                    Thread.sleep(1000);
                    log.debug("[{}] Subscribed {} symbols so far, pausing 1 sec...", connectionId, i + 1);
                }
                
            } catch (Exception e) {
                log.error("Failed to subscribe symbol {} to [{}]: {}", symbol, connectionId, e.getMessage());
                failCount++;
            }
        }
        
        log.info("Subscription completed for [{}]: {} success, {} failed", 
                connectionId, successCount, failCount);
        
        if (successCount > 0) {
            log.info("[{}] Successfully monitoring: {} to {}", 
                    connectionId, symbols.get(0), symbols.get(symbols.size() - 1));
        }
    }
    
    private void scheduleReconnect(String connectionId, String apiKey) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(() -> {
                log.info("Attempting to reconnect [{}]...", connectionId);
                connectWithApiKey(connectionId, apiKey);
            }, 10, TimeUnit.SECONDS);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        webSocketClients.values().forEach(client -> {
            if (client != null) {
                client.close();
            }
        });
        
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
} 