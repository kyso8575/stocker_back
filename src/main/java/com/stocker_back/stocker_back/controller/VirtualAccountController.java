package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.VirtualAccount;
import com.stocker_back.stocker_back.dto.VirtualTradeRequestDto;
import com.stocker_back.stocker_back.service.VirtualAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/virtual-account")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "VirtualAccount", description = "모의 투자 계좌 API")
public class VirtualAccountController {
    private final VirtualAccountService virtualAccountService;

    @Operation(summary = "가상 계좌 생성/초기화", description = "로그인한 사용자의 가상 계좌를 생성하거나 초기화합니다.")
    @PostMapping("/init")
    public ResponseEntity<?> initAccount(HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            VirtualAccount account = virtualAccountService.createOrResetAccount(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("id", account.getId());
            data.put("balance", account.getBalance());
            data.put("createdAt", account.getCreatedAt());
            data.put("updatedAt", account.getUpdatedAt());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "가상 계좌가 생성/초기화되었습니다.",
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error initializing virtual account", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }

    @Operation(summary = "가상 계좌 상태 조회", description = "로그인한 사용자의 가상 계좌 존재 여부와 상태를 조회합니다.")
    @GetMapping("/status")
    public ResponseEntity<?> getAccountStatus(HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            var status = virtualAccountService.getAccountStatus(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", status
            ));
        } catch (Exception e) {
            log.error("Error getting account status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }

    @Operation(summary = "가상 포트폴리오 조회", description = "로그인한 사용자의 가상 포트폴리오(보유 종목 목록)를 조회합니다.")
    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            var portfolio = virtualAccountService.getPortfolio(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", portfolio
            ));
        } catch (Exception e) {
            log.error("Error getting virtual portfolio", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }

    @Operation(summary = "포트폴리오 성과 요약", description = "로그인한 사용자의 포트폴리오 성과를 종합적으로 분석하여 제공합니다.")
    @GetMapping("/summary")
    public ResponseEntity<?> getPortfolioSummary(HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            var summary = virtualAccountService.getPortfolioSummary(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", summary
            ));
        } catch (Exception e) {
            log.error("Error getting portfolio summary", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "서버 오류가 발생했습니다"
            ));
        }
    }

    @Operation(summary = "가상 매수", description = "가상 계좌로 주식을 매수합니다.")
    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody VirtualTradeRequestDto req, HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            virtualAccountService.buy(userId, req.getSymbol(), req.getQuantity());
            var account = virtualAccountService.createOrResetAccount(userId); // get updated account
            var portfolio = virtualAccountService.getPortfolio(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "매수 완료",
                "balance", account.getBalance(),
                "portfolio", portfolio
            ));
        } catch (Exception e) {
            log.error("Error in virtual buy", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "가상 매도", description = "가상 계좌로 주식을 매도합니다.")
    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody VirtualTradeRequestDto req, HttpSession session) {
        log.info("[DEBUG] Session ID: {}", session.getId());
        log.info("[DEBUG] User ID from session: {}", session.getAttribute("userId"));
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }
        
        try {
            virtualAccountService.sell(userId, req.getSymbol(), req.getQuantity());
            var account = virtualAccountService.createOrResetAccount(userId); // get updated account
            var portfolio = virtualAccountService.getPortfolio(userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "매도 완료",
                "balance", account.getBalance(),
                "portfolio", portfolio
            ));
        } catch (Exception e) {
            log.error("Error in virtual sell", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
} 