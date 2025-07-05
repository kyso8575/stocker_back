package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.DevService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
public class DevController {
    
    private final DevService devService;
    
    /**
     * 개발용: 사용자에게 관리자 권한 부여
     */
    @PostMapping("/make-admin/{username}")
    public ResponseEntity<?> makeUserAdmin(@PathVariable String username) {
        try {
            Map<String, Object> userData = devService.makeUserAdmin(username);
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format("User %s is now an ADMIN", username), 
                userData
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Failed to make user admin: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AuthResponseDto.error(
                ResponseMessages.format("User %s not found", username)
            ));
        } catch (Exception e) {
            log.error("Error making user admin for username {}: ", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 