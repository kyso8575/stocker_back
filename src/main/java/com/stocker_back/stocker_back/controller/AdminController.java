package com.stocker_back.stocker_back.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    /**
     * 시스템 상태 조회 (관리자 전용)
     */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(HttpServletRequest request) {
        String adminUsername = (String) request.getAttribute("username");
        log.info("Admin {} requested system status", adminUsername);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "시스템 상태 조회 성공",
            "data", Map.of(
                "status", "healthy",
                "uptime", "72h 15m 32s",
                "activeConnections", 156,
                "memoryUsage", "45%",
                "requestedBy", adminUsername
            )
        ));
    }
} 