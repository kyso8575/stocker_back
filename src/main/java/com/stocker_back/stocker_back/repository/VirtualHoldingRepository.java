package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.VirtualAccount;
import com.stocker_back.stocker_back.domain.VirtualHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VirtualHoldingRepository extends JpaRepository<VirtualHolding, Long> {
    List<VirtualHolding> findByVirtualAccount(VirtualAccount virtualAccount);
    
    // 계좌별 모든 보유 종목 삭제
    void deleteByVirtualAccount(VirtualAccount virtualAccount);
} 