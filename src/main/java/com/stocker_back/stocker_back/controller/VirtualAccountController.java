package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.dto.VirtualTradeRequestDto;
import com.stocker_back.stocker_back.service.VirtualAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/virtual-account")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Virtual Account", description = "가상 계좌 API")
public class VirtualAccountController {
    
    private final VirtualAccountService virtualAccountService;
    
    @Operation(summary = "가상 계좌 생성/초기화", description = "로그인한 사용자의 가상 계좌를 생성하거나 초기화합니다.")
    @PostMapping("/init")
    public ResponseEntity<?> initAccount(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to initialize virtual account for user: {}", userId);
        
        try {
            var account = virtualAccountService.createOrResetAccount(userId);
            Map<String, Object> data = Map.of(
                "id", account.getId(),
                "balance", account.getBalance(),
                "createdAt", account.getCreatedAt(),
                "updatedAt", account.getUpdatedAt()
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS_VIRTUAL_ACCOUNT_INIT,
                data
            ));
        } catch (Exception e) {
            log.error("Error initializing virtual account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getAccountStatus(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to get account status for user: {}", userId);
        
        try {
            var status = virtualAccountService.getAccountStatus(userId);
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                Map.of("data", status)
            ));
        } catch (Exception e) {
            log.error("Error getting account status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolio(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to get portfolio for user: {}", userId);
        
        try {
            var portfolio = virtualAccountService.getPortfolio(userId);
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                Map.of("data", portfolio)
            ));
        } catch (Exception e) {
            log.error("Error getting portfolio", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(summary = "포트폴리오 성과 요약", description = "로그인한 사용자의 포트폴리오 성과를 종합적으로 분석하여 제공합니다.")
    @GetMapping("/summary")
    public ResponseEntity<?> getPortfolioSummary(HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to get portfolio summary for user: {}", userId);
        
        try {
            var summary = virtualAccountService.getPortfolioSummary(userId);
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                Map.of("data", summary)
            ));
        } catch (Exception e) {
            log.error("Error getting portfolio summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(summary = "가상 매수", description = "가상 계좌로 주식을 매수합니다.")
    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody VirtualTradeRequestDto req, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to buy {} shares of {} for user: {}", req.getQuantity(), req.getSymbol(), userId);
        
        try {
            virtualAccountService.buy(userId, req.getSymbol(), req.getQuantity());
            var account = virtualAccountService.createOrResetAccount(userId);
            var portfolio = virtualAccountService.getPortfolio(userId);
            
            Map<String, Object> data = Map.of(
                "balance", account.getBalance(),
                "portfolio", portfolio
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS_VIRTUAL_BUY,
                data
            ));
        } catch (Exception e) {
            log.error("Error buying stock", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(summary = "가상 매도", description = "가상 계좌로 주식을 매도합니다.")
    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody VirtualTradeRequestDto req, HttpSession session) {
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_UNAUTHORIZED));
        }
        
        log.info("Received request to sell {} shares of {} for user: {}", req.getQuantity(), req.getSymbol(), userId);
        
        try {
            virtualAccountService.sell(userId, req.getSymbol(), req.getQuantity());
            var account = virtualAccountService.createOrResetAccount(userId);
            var portfolio = virtualAccountService.getPortfolio(userId);
            
            Map<String, Object> data = Map.of(
                "balance", account.getBalance(),
                "portfolio", portfolio
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS_VIRTUAL_SELL,
                data
            ));
        } catch (Exception e) {
            log.error("Error selling stock", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * 세션에서 사용자 ID를 추출하는 헬퍼 메서드
     */
    private Long getUserIdFromSession(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
} 