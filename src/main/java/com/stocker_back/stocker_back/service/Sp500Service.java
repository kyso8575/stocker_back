package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class Sp500Service {

    private final Sp500ScraperService sp500ScraperService;
    private final StockSymbolRepository stockSymbolRepository;
    private final QuoteRepository quoteRepository;
    private final TradeRepository tradeRepository;

    public Set<String> updateSp500List() {
        return sp500ScraperService.updateSp500List();
    }

    public List<Map<String, String>> getSp500Symbols() {
        List<StockSymbol> sp500Stocks = stockSymbolRepository.findByIsSp500TrueAndProfileEmptyFalse();
        
        return sp500Stocks.stream()
                .map(this::createStockInfoMap)
                .toList();
    }

    public List<Map<String, Object>> getSp500TableData() {
        List<StockSymbol> sp500Symbols = stockSymbolRepository.findByIsSp500TrueAndProfileEmptyFalse();
        
        return sp500Symbols.stream()
                .map(this::buildStockTableData)
                .filter(data -> data != null)
                .toList();
    }

    private Map<String, String> createStockInfoMap(StockSymbol stock) {
        Map<String, String> stockInfo = new HashMap<>();
        stockInfo.put("symbol", stock.getSymbol());
        stockInfo.put("name", stock.getName() != null ? stock.getName() : stock.getDescription());
        stockInfo.put("logo", stock.getLogo());
        return stockInfo;
    }

    private Map<String, Object> buildStockTableData(StockSymbol stockSymbol) {
        String symbol = stockSymbol.getSymbol();
        
        Map<String, Object> stockData = new HashMap<>();
        stockData.put("symbol", symbol);
        stockData.put("name", stockSymbol.getName() != null ? stockSymbol.getName() : stockSymbol.getDescription());
        stockData.put("logo", stockSymbol.getLogo());

        // 현재가: Trade에서 최신 값 우선, 하루 안에 없으면 Quote 사용
        BigDecimal currentPrice = getCurrentPrice(symbol);
        stockData.put("price", currentPrice);
        
        // 이전 종가: Quote에서 가져오기
        BigDecimal previousPrice = getPreviousPrice(symbol);
        
        // 거래량: Quote에서 가져오기
        Long volume = getVolume(symbol);
        stockData.put("volume", volume);
        
        // 고가, 저가: Quote에서 가져오기
        BigDecimal high = getHigh(symbol);
        BigDecimal low = getLow(symbol);
        stockData.put("high", high);
        stockData.put("low", low);
        
        // 가격 변화 계산
        if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal change = currentPrice.subtract(previousPrice);
            BigDecimal percentChange = change.divide(previousPrice, 4, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100));
            
            stockData.put("change", change);
            stockData.put("percentChange", percentChange);
        } else {
            stockData.put("change", BigDecimal.ZERO);
            stockData.put("percentChange", BigDecimal.ZERO);
        }

        return stockData;
    }
    
    private BigDecimal getCurrentPrice(String symbol) {
        // 1. Trade에서 하루 안의 최신 데이터 조회
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        List<Trade> recentTrades = tradeRepository.findTradesBySymbolAfter(symbol, oneDayAgo);
        
        if (!recentTrades.isEmpty()) {
            // 가장 최신 Trade의 가격 사용
            Trade latestTrade = recentTrades.get(0);
            log.debug("Using Trade price for {}: {}", symbol, latestTrade.getPrice());
            return latestTrade.getPrice();
        }
        
        // 2. Trade에 하루 안 데이터가 없으면 Quote 사용
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            Quote quote = latestQuoteOpt.get();
            log.debug("Using Quote price for {}: {}", symbol, quote.getCurrentPrice());
            return quote.getCurrentPrice();
        }
        
        log.warn("No price data found for symbol: {}", symbol);
        return BigDecimal.ZERO;
    }
    
    private BigDecimal getPreviousPrice(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getPreviousClosePrice();
        }
        return BigDecimal.ZERO;
    }
    
    private Long getVolume(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getVolume();
        }
        return 0L;
    }
    
    private BigDecimal getHigh(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getHighPrice();
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal getLow(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getLowPrice();
        }
        return BigDecimal.ZERO;
    }
} 