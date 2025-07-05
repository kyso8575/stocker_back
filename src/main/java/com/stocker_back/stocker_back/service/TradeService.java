package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.MultiKeyFinnhubWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {

    private final TradeRepository tradeRepository;
    private final MultiKeyFinnhubWebSocketService multiKeyWebSocketService;

    public List<Trade> getLatestTradesBySymbol(String symbol, int limit) {
        List<Trade> trades = tradeRepository.findLatestTradesBySymbol(symbol.toUpperCase());
        return trades.stream().limit(limit).toList();
    }

    public BigDecimal getLatestPriceBySymbol(String symbol) {
        return tradeRepository.findLatestPriceBySymbol(symbol.toUpperCase());
    }

    public List<Trade> getTradeHistory(LocalDateTime from, LocalDateTime to, String symbol) {
        List<Trade> trades = tradeRepository.findTradesBetween(from, to);
        
        if (symbol != null && !symbol.isEmpty()) {
            return trades.stream()
                    .filter(trade -> trade.getSymbol().equalsIgnoreCase(symbol))
                    .toList();
        }
        
        return trades;
    }

    public Map<String, Object> getWebSocketStatus() {
        Map<String, Boolean> connectionStatus = multiKeyWebSocketService.getConnectionStatus();
        long activeConnections = connectionStatus.values().stream().mapToLong(b -> b ? 1 : 0).sum();
        
        return Map.of(
                "connections", connectionStatus,
                "totalConnections", connectionStatus.size(),
                "activeConnections", activeConnections,
                "anyConnected", multiKeyWebSocketService.isAnyConnected()
        );
    }

    public void connectWebSocket() {
        multiKeyWebSocketService.connectAll();
    }

    public void disconnectWebSocket() {
        multiKeyWebSocketService.disconnectAll();
    }
} 