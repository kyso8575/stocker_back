package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.SystemStatusService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final SystemStatusService systemStatusService;
    
    /**
     * 시스템 상태 조회 (관리자 전용)
     */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(HttpServletRequest request) {
        try {
            String adminUsername = (String) request.getAttribute("username");
            log.info("Admin {} requested system status", adminUsername);
            
            Map<String, Object> systemData = systemStatusService.getSystemStatus(adminUsername);
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, systemData));
            
        } catch (Exception e) {
            log.error("Error retrieving system status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 