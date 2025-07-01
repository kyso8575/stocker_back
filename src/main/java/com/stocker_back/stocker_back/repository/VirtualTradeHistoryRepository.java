package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.VirtualAccount;
import com.stocker_back.stocker_back.domain.VirtualTradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface VirtualTradeHistoryRepository extends JpaRepository<VirtualTradeHistory, Long> {
    
    // 계좌별 모든 거래 내역 조회 (최신순)
    List<VirtualTradeHistory> findByVirtualAccountOrderByTradeDateDesc(VirtualAccount virtualAccount);
    
    // 계좌별 특정 종목 거래 내역 조회
    List<VirtualTradeHistory> findByVirtualAccountAndSymbolOrderByTradeDateDesc(VirtualAccount virtualAccount, String symbol);
    
    // 계좌별 매수 거래 내역 조회
    List<VirtualTradeHistory> findByVirtualAccountAndTradeTypeOrderByTradeDateDesc(VirtualAccount virtualAccount, VirtualTradeHistory.TradeType tradeType);
    
    // 계좌별 총 매수 금액 계산
    @Query("SELECT COALESCE(SUM(vth.totalAmount), 0) FROM VirtualTradeHistory vth WHERE vth.virtualAccount = :account AND vth.tradeType = 'BUY'")
    BigDecimal getTotalBuyAmount(@Param("account") VirtualAccount account);
    
    // 계좌별 총 실현손익 계산
    @Query("SELECT COALESCE(SUM(vth.realizedProfit), 0) FROM VirtualTradeHistory vth WHERE vth.virtualAccount = :account AND vth.tradeType = 'SELL'")
    BigDecimal getTotalRealizedProfit(@Param("account") VirtualAccount account);
    
    // 계좌별 거래 횟수 계산
    @Query("SELECT COUNT(vth) FROM VirtualTradeHistory vth WHERE vth.virtualAccount = :account")
    Long getTotalTradeCount(@Param("account") VirtualAccount account);
    
    // 계좌별 매수 횟수 계산
    @Query("SELECT COUNT(vth) FROM VirtualTradeHistory vth WHERE vth.virtualAccount = :account AND vth.tradeType = 'BUY'")
    Long getBuyTradeCount(@Param("account") VirtualAccount account);
    
    // 계좌별 매도 횟수 계산
    @Query("SELECT COUNT(vth) FROM VirtualTradeHistory vth WHERE vth.virtualAccount = :account AND vth.tradeType = 'SELL'")
    Long getSellTradeCount(@Param("account") VirtualAccount account);
    
    // 계좌별 모든 거래 기록 삭제
    void deleteByVirtualAccount(VirtualAccount virtualAccount);
} 