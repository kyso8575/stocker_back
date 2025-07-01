package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.domain.VirtualAccount;
import com.stocker_back.stocker_back.repository.UserRepository;
import com.stocker_back.stocker_back.repository.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.stocker_back.stocker_back.domain.VirtualHolding;
import com.stocker_back.stocker_back.dto.VirtualHoldingDto;
import com.stocker_back.stocker_back.repository.VirtualHoldingRepository;
import com.stocker_back.stocker_back.repository.TradeRepository;
import com.stocker_back.stocker_back.repository.QuoteRepository;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.domain.VirtualTradeHistory;
import com.stocker_back.stocker_back.repository.VirtualTradeHistoryRepository;
import com.stocker_back.stocker_back.dto.PortfolioSummaryDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class VirtualAccountService {
    private final VirtualAccountRepository virtualAccountRepository;
    private final UserRepository userRepository;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000");
    private final VirtualHoldingRepository virtualHoldingRepository;
    private final TradeRepository tradeRepository;
    private final QuoteRepository quoteRepository;
    private final StockSymbolRepository stockSymbolRepository;
    private final VirtualTradeHistoryRepository virtualTradeHistoryRepository;

    @Transactional
    public VirtualAccount createOrResetAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        return virtualAccountRepository.findByUser(user)
                .map(account -> {
                    // 기존 계좌가 있으면 모든 데이터 초기화
                    // 1. 보유 종목 삭제
                    virtualHoldingRepository.deleteByVirtualAccount(account);
                    
                    // 2. 거래 기록 삭제
                    virtualTradeHistoryRepository.deleteByVirtualAccount(account);
                    
                    // 3. 잔고 초기화
                    account.setBalance(INITIAL_BALANCE);
                    
                    return account;
                })
                .orElseGet(() -> {
                    // 새 계좌 생성
                    VirtualAccount account = VirtualAccount.builder()
                            .user(user)
                            .balance(INITIAL_BALANCE)
                            .build();
                    return virtualAccountRepository.save(account);
                });
    }

    public List<VirtualHoldingDto> getPortfolio(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        VirtualAccount account = virtualAccountRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Virtual account not found"));
        List<VirtualHolding> holdings = virtualHoldingRepository.findByVirtualAccount(account);
        List<VirtualHoldingDto> result = new ArrayList<>();
        for (VirtualHolding h : holdings) {
            // 현재가: Trade(1일 이내), 없으면 Quote
            java.math.BigDecimal currentPrice = null;
            java.time.LocalDateTime oneDayAgo = java.time.LocalDateTime.now().minusDays(1);
            List<com.stocker_back.stocker_back.domain.Trade> trades = tradeRepository.findTradesBySymbolAfter(h.getSymbol(), oneDayAgo);
            if (!trades.isEmpty()) {
                currentPrice = trades.get(0).getPrice();
            } else {
                Optional<com.stocker_back.stocker_back.domain.Quote> quoteOpt = quoteRepository.findLatestQuoteBySymbol(h.getSymbol());
                if (quoteOpt.isPresent()) {
                    currentPrice = quoteOpt.get().getCurrentPrice();
                }
            }
            // 로고
            String logo = null;
            Optional<StockSymbol> symbolOpt = stockSymbolRepository.findBySymbol(h.getSymbol());
            if (symbolOpt.isPresent()) {
                logo = symbolOpt.get().getLogo();
            }
            // 평가금액/손익
            java.math.BigDecimal evalAmount = null;
            java.math.BigDecimal evalProfit = null;
            if (currentPrice != null) {
                evalAmount = currentPrice.multiply(h.getQuantity());
                evalProfit = currentPrice.subtract(h.getAvgPrice()).multiply(h.getQuantity());
            }
            result.add(VirtualHoldingDto.builder()
                    .symbol(h.getSymbol())
                    .quantity(h.getQuantity())
                    .avgPrice(h.getAvgPrice())
                    .currentPrice(currentPrice)
                    .evalAmount(evalAmount)
                    .evalProfit(evalProfit)
                    .logo(logo)
                    .build());
        }
        return result;
    }

    @Transactional
    public void buy(Long userId, String symbol, BigDecimal quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        VirtualAccount account = virtualAccountRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Virtual account not found"));
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("수량은 0보다 커야 합니다");

        // 현재가 결정 (Trade 1일 이내, 없으면 Quote)
        BigDecimal price = null;
        java.time.LocalDateTime oneDayAgo = java.time.LocalDateTime.now().minusDays(1);
        var trades = tradeRepository.findTradesBySymbolAfter(symbol, oneDayAgo);
        if (!trades.isEmpty()) {
            price = trades.get(0).getPrice();
        } else {
            var quoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
            if (quoteOpt.isPresent()) price = quoteOpt.get().getCurrentPrice();
        }
        if (price == null) throw new IllegalArgumentException("현재가를 찾을 수 없습니다");

        BigDecimal totalCost = price.multiply(quantity);
        if (account.getBalance().compareTo(totalCost) < 0) throw new IllegalArgumentException("잔고가 부족합니다");
        account.setBalance(account.getBalance().subtract(totalCost));

        // 보유 종목 처리
        VirtualHolding holding = virtualHoldingRepository.findByVirtualAccount(account).stream()
                .filter(h -> h.getSymbol().equalsIgnoreCase(symbol))
                .findFirst().orElse(null);
        if (holding == null) {
            holding = VirtualHolding.builder()
                    .virtualAccount(account)
                    .symbol(symbol.toUpperCase())
                    .quantity(quantity)
                    .avgPrice(price)
                    .build();
            virtualHoldingRepository.save(holding);
        } else {
            BigDecimal totalQty = holding.getQuantity().add(quantity);
            BigDecimal newAvg = (holding.getAvgPrice().multiply(holding.getQuantity()).add(price.multiply(quantity))).divide(totalQty, 4, RoundingMode.HALF_UP);
            holding.setQuantity(totalQty);
            holding.setAvgPrice(newAvg);
        }

        // 거래 기록 저장
        VirtualTradeHistory tradeHistory = VirtualTradeHistory.builder()
                .virtualAccount(account)
                .symbol(symbol.toUpperCase())
                .tradeType(VirtualTradeHistory.TradeType.BUY)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalCost)
                .tradeDate(LocalDateTime.now())
                .build();
        virtualTradeHistoryRepository.save(tradeHistory);
    }

    @Transactional
    public void sell(Long userId, String symbol, BigDecimal quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        VirtualAccount account = virtualAccountRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Virtual account not found"));
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("수량은 0보다 커야 합니다");

        VirtualHolding holding = virtualHoldingRepository.findByVirtualAccount(account).stream()
                .filter(h -> h.getSymbol().equalsIgnoreCase(symbol))
                .findFirst().orElse(null);
        if (holding == null || holding.getQuantity().compareTo(quantity) < 0) throw new IllegalArgumentException("보유 수량이 부족합니다");

        // 현재가 결정 (Trade 1일 이내, 없으면 Quote)
        BigDecimal price = null;
        java.time.LocalDateTime oneDayAgo = java.time.LocalDateTime.now().minusDays(1);
        var trades = tradeRepository.findTradesBySymbolAfter(symbol, oneDayAgo);
        if (!trades.isEmpty()) {
            price = trades.get(0).getPrice();
        } else {
            var quoteOpt = quoteRepository.findLatestQuoteBySymbol(symbol);
            if (quoteOpt.isPresent()) price = quoteOpt.get().getCurrentPrice();
        }
        if (price == null) throw new IllegalArgumentException("현재가를 찾을 수 없습니다");

        BigDecimal totalGain = price.multiply(quantity);
        account.setBalance(account.getBalance().add(totalGain));

        // 실현손익 계산
        BigDecimal realizedProfit = (price.subtract(holding.getAvgPrice())).multiply(quantity);

        // 보유 수량 차감/삭제
        if (holding.getQuantity().compareTo(quantity) == 0) {
            virtualHoldingRepository.delete(holding);
        } else {
            holding.setQuantity(holding.getQuantity().subtract(quantity));
        }

        // 거래 기록 저장
        VirtualTradeHistory tradeHistory = VirtualTradeHistory.builder()
                .virtualAccount(account)
                .symbol(symbol.toUpperCase())
                .tradeType(VirtualTradeHistory.TradeType.SELL)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalGain)
                .realizedProfit(realizedProfit)
                .tradeDate(LocalDateTime.now())
                .build();
        virtualTradeHistoryRepository.save(tradeHistory);
    }

    public PortfolioSummaryDto getPortfolioSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        VirtualAccount account = virtualAccountRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Virtual account not found"));

        // 현재 포트폴리오 조회
        List<VirtualHoldingDto> holdings = getPortfolio(userId);
        
        // 거래 통계 조회
        BigDecimal totalInvested = virtualTradeHistoryRepository.getTotalBuyAmount(account);
        BigDecimal realizedProfit = virtualTradeHistoryRepository.getTotalRealizedProfit(account);
        Integer totalTrades = virtualTradeHistoryRepository.getTotalTradeCount(account).intValue();
        Integer buyTrades = virtualTradeHistoryRepository.getBuyTradeCount(account).intValue();
        Integer sellTrades = virtualTradeHistoryRepository.getSellTradeCount(account).intValue();

        // 평가손익 계산
        BigDecimal unrealizedProfit = BigDecimal.ZERO;
        BigDecimal totalMarketValue = account.getBalance(); // 현금부터 시작
        
        for (VirtualHoldingDto holding : holdings) {
            if (holding.getEvalProfit() != null) {
                unrealizedProfit = unrealizedProfit.add(holding.getEvalProfit());
            }
            if (holding.getEvalAmount() != null) {
                totalMarketValue = totalMarketValue.add(holding.getEvalAmount());
            }
        }

        // 총 손익 및 수익률 계산
        BigDecimal totalProfit = unrealizedProfit.add(realizedProfit);
        BigDecimal totalReturn = BigDecimal.ZERO;
        BigDecimal returnRate = BigDecimal.ZERO;
        
        if (totalInvested != null && totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalReturn = totalProfit.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            returnRate = totalReturn;
        }

        // 보유 종목별 분석
        List<PortfolioSummaryDto.HoldingSummaryDto> holdingSummaries = new ArrayList<>();
        for (VirtualHoldingDto holding : holdings) {
            BigDecimal weight = BigDecimal.ZERO;
            if (totalMarketValue.compareTo(BigDecimal.ZERO) > 0 && holding.getEvalAmount() != null) {
                weight = holding.getEvalAmount().divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }
            
            BigDecimal profitRate = BigDecimal.ZERO;
            if (holding.getAvgPrice() != null && holding.getAvgPrice().compareTo(BigDecimal.ZERO) > 0 && holding.getCurrentPrice() != null) {
                profitRate = (holding.getCurrentPrice().subtract(holding.getAvgPrice()))
                        .divide(holding.getAvgPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            holdingSummaries.add(PortfolioSummaryDto.HoldingSummaryDto.builder()
                    .symbol(holding.getSymbol())
                    .logo(holding.getLogo())
                    .quantity(holding.getQuantity())
                    .avgPrice(holding.getAvgPrice())
                    .currentPrice(holding.getCurrentPrice())
                    .investedAmount(holding.getAvgPrice() != null ? holding.getAvgPrice().multiply(holding.getQuantity()) : null)
                    .marketValue(holding.getEvalAmount())
                    .unrealizedProfit(holding.getEvalProfit())
                    .profitRate(profitRate)
                    .weight(weight)
                    .build());
        }

        // 최근 거래 내역 조회
        List<VirtualTradeHistory> recentTrades = virtualTradeHistoryRepository.findByVirtualAccountOrderByTradeDateDesc(account);
        List<PortfolioSummaryDto.TradeHistoryDto> tradeHistoryDtos = new ArrayList<>();
        
        for (int i = 0; i < Math.min(10, recentTrades.size()); i++) {
            VirtualTradeHistory trade = recentTrades.get(i);
            tradeHistoryDtos.add(PortfolioSummaryDto.TradeHistoryDto.builder()
                    .id(trade.getId())
                    .symbol(trade.getSymbol())
                    .tradeType(trade.getTradeType().name())
                    .quantity(trade.getQuantity())
                    .price(trade.getPrice())
                    .totalAmount(trade.getTotalAmount())
                    .realizedProfit(trade.getRealizedProfit())
                    .tradeDate(trade.getTradeDate())
                    .build());
        }

        return PortfolioSummaryDto.builder()
                .totalBalance(account.getBalance())
                .totalInvested(totalInvested != null ? totalInvested : BigDecimal.ZERO)
                .totalMarketValue(totalMarketValue)
                .totalReturn(totalReturn)
                .totalProfit(totalProfit)
                .unrealizedProfit(unrealizedProfit)
                .realizedProfit(realizedProfit != null ? realizedProfit : BigDecimal.ZERO)
                .initialBalance(INITIAL_BALANCE)
                .returnRate(returnRate)
                .profitRate(returnRate)
                .totalTrades(totalTrades)
                .buyTrades(buyTrades)
                .sellTrades(sellTrades)
                .totalTradingVolume(BigDecimal.ZERO) // TODO: 거래량 계산 추가
                .holdings(holdingSummaries)
                .totalHoldings(holdings.size())
                .recentTrades(tradeHistoryDtos)
                .analysisDate(LocalDateTime.now())
                .build();
    }

    public Map<String, Object> getAccountStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        Optional<VirtualAccount> accountOpt = virtualAccountRepository.findByUser(user);
        
        if (accountOpt.isPresent()) {
            VirtualAccount account = accountOpt.get();
            Map<String, Object> result = new HashMap<>();
            result.put("hasAccount", true);
            result.put("balance", account.getBalance());
            result.put("createdAt", account.getCreatedAt());
            result.put("updatedAt", account.getUpdatedAt());
            return result;
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("hasAccount", false);
            result.put("balance", null);
            result.put("createdAt", null);
            result.put("updatedAt", null);
            return result;
        }
    }
} 

