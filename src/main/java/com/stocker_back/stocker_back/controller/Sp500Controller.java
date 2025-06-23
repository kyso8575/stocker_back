package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.FinancialMetrics;
import com.stocker_back.stocker_back.domain.Quote;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.domain.Trade;
import com.stocker_back.stocker_back.repository.FinancialMetricsRepository;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.service.Sp500ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/sp500")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "S&P 500", description = "S&P 500 관련 API")
public class Sp500Controller {

    private final Sp500ScraperService sp500ScraperService;
    private final StockSymbolRepository stockSymbolRepository;
    private final QuoteRepository quoteRepository;
    private final TradeRepository tradeRepository;
    private final FinancialMetricsRepository financialMetricsRepository;

    @Operation(
        summary = "S&P 500 리스트 업데이트",
        description = "S&P 500 리스트를 웹스크래핑하여 데이터베이스를 업데이트합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "S&P 500 리스트 업데이트 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/update")
    public ResponseEntity<Map<String, Object>> updateSp500List() {
        log.info("Received request to update S&P 500 list");
        
        try {
            Set<String> updatedSymbols = sp500ScraperService.updateSp500List();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Successfully updated S&P 500 list with %d symbols", updatedSymbols.size()));
            response.put("symbols", updatedSymbols);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating S&P 500 list: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
        summary = "S&P 500 심볼 목록 조회",
        description = "S&P 500에 포함된 모든 주식 심볼을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 심볼 목록 조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSp500Symbols() {
        log.info("Received request to get S&P 500 symbols");
        
        try {
            Set<String> sp500Symbols = sp500ScraperService.findAllSp500Symbols();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Found %d S&P 500 symbols", sp500Symbols.size()));
            response.put("symbols", sp500Symbols);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 symbols: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
        summary = "S&P 500 테이블 데이터 조회",
        description = "S&P 500 주식들의 테이블 표시용 데이터를 조회합니다. (가격, 변화율, 시가총액 등)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 테이블 데이터 조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/table")
    public ResponseEntity<Map<String, Object>> getSp500TableData() {
        log.info("Received request to get S&P 500 table data");
        
        try {
            // S&P 500 주식 심볼들 조회
            List<StockSymbol> sp500Symbols = stockSymbolRepository.findByIsSp500TrueAndProfileEmptyFalse();
            
            List<Map<String, Object>> tableData = sp500Symbols.stream()
                    .map(this::buildStockTableData)
                    .filter(data -> data != null)
                    .toList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Successfully retrieved S&P 500 table data for %d stocks", tableData.size()));
            response.put("data", tableData);
            response.put("totalCount", tableData.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 table data: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * StockSymbol 데이터를 기반으로 테이블 데이터를 구성합니다.
     * @param stockSymbol 주식 심볼 엔티티
     * @return 테이블 데이터 Map
     */
    private Map<String, Object> buildStockTableData(StockSymbol stockSymbol) {
        String symbol = stockSymbol.getSymbol();
        
        Map<String, Object> stockData = new HashMap<>();
        stockData.put("symbol", symbol);
        stockData.put("name", stockSymbol.getName() != null ? stockSymbol.getName() : stockSymbol.getDescription());
        stockData.put("currency", stockSymbol.getCurrency());
        stockData.put("exchange", stockSymbol.getExchange());
        stockData.put("industry", stockSymbol.getFinnhubIndustry());
        stockData.put("logo", stockSymbol.getLogo());
        stockData.put("weburl", stockSymbol.getWeburl());

        // 현재가: Trade에서 최신 값 우선, 하루 안에 없으면 Quote 사용
        BigDecimal currentPrice = getCurrentPrice(symbol);
        stockData.put("price", currentPrice);
        
        // 이전 종가: Quote에서 가져오기
        BigDecimal previousPrice = getPreviousPrice(symbol);
        stockData.put("previousPrice", previousPrice);
        
        // 거래량: Quote에서 가져오기
        Long volume = getVolume(symbol);
        stockData.put("volume", volume);
        
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

        // 시가총액 정보 조회 (FinancialMetrics에서)
        Optional<FinancialMetrics> latestMetricsOpt = financialMetricsRepository.findTopBySymbolOrderByCreatedAtDesc(symbol);
        if (latestMetricsOpt.isPresent()) {
            FinancialMetrics metrics = latestMetricsOpt.get();
            if (metrics.getMarketCapitalization() != null) {
                stockData.put("marketCap", BigDecimal.valueOf(metrics.getMarketCapitalization()));
            } else {
                stockData.put("marketCap", BigDecimal.ZERO);
            }
        } else {
            stockData.put("marketCap", BigDecimal.ZERO);
        }

        return stockData;
    }
    
    /**
     * 현재가를 조회합니다. Trade에서 하루 안의 최신 값을 우선 사용하고, 없으면 Quote를 사용합니다.
     * @param symbol 주식 심볼
     * @return 현재가
     */
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
    
    /**
     * 이전 종가를 조회합니다. Quote 테이블에서 가져옵니다.
     * @param symbol 주식 심볼
     * @return 이전 종가
     */
    private BigDecimal getPreviousPrice(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getPreviousClosePrice();
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 거래량을 조회합니다. Quote 테이블에서 가져옵니다.
     * @param symbol 주식 심볼
     * @return 거래량
     */
    private Long getVolume(String symbol) {
        Optional<Quote> latestQuoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
        if (latestQuoteOpt.isPresent()) {
            return latestQuoteOpt.get().getVolume();
        }
        return 0L;
    }
} 