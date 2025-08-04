package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.dto.UserRegistrationDto;
import com.stocker_back.stocker_back.dto.UserLoginDto;
import com.stocker_back.stocker_back.dto.ApiResponse;
import com.stocker_back.stocker_back.service.UserService;
import com.stocker_back.stocker_back.service.SessionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final UserService userService;
    private final SessionManagerService sessionManagerService;
    
    /**
     * User registration API
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegistrationDto registrationDto, 
                                    BindingResult bindingResult,
                                    HttpSession session) {
        try {
            // 입력값 검증
            ResponseEntity<?> validationError = handleValidationErrors(bindingResult);
            if (validationError != null) {
                return validationError;
            }
            
            // 회원가입 처리
            User user = userService.registerUser(registrationDto);
            
            // 중복 로그인 관리: 회원가입 후 자동 로그인 시에도 기존 세션 무효화
            sessionManagerService.registerUserSession(user.getId(), session.getId());
            
            // 세션에 사용자 정보 저장 (자동 로그인)
            saveUserToSession(user, session);
            
            log.info("Registration successful and auto login with session management: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(ApiResponse.success(convertUserToMap(user)));
            
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("잘못된 입력입니다"));
        } catch (Exception e) {
            log.error("Server error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * User login API with duplicate login management
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody UserLoginDto loginDto,
                                 BindingResult bindingResult,
                                 HttpSession session) {
        try {
            // 입력값 검증
            ResponseEntity<?> validationError = handleValidationErrors(bindingResult);
            if (validationError != null) {
                return validationError;
            }
            
            // 로그인 처리
            User user = userService.loginUser(loginDto);
            
            // 중복 로그인 관리: 기존 세션 무효화 및 새 세션 등록
            sessionManagerService.registerUserSession(user.getId(), session.getId());
            
            // 세션에 사용자 정보 저장
            saveUserToSession(user, session);
            
            log.info("Login successful with duplicate login management: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(ApiResponse.success("로그인 성공", convertUserToMap(user)));
            
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("잘못된 입력입니다"));
        } catch (Exception e) {
            log.error("Server error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * User logout API
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        try {
            // 세션에서 사용자 정보 가져오기
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("User is not logged in"));
            }
            
            // 세션 무효화
            session.invalidate();
            
            // 중복 로그인 관리: 세션 등록 해제
            sessionManagerService.removeUserSession(userId);
            
            log.info("Logout successful: userId={}", userId);
            
            return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
            
        } catch (Exception e) {
            log.error("Server error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * Check username availability
     */
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        try {
            boolean isAvailable = !userService.isUsernameExists(username);
            
            Map<String, Object> data = Map.of(
                "username", username,
                "available", isAvailable
            );
            
            return ResponseEntity.ok(ApiResponse.success("Username availability checked", data));
            
        } catch (Exception e) {
            log.error("Server error during username check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * Check email availability
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        try {
            boolean isAvailable = !userService.isEmailExists(email);
            
            Map<String, Object> data = Map.of(
                "email", email,
                "available", isAvailable
            );
            
            return ResponseEntity.ok(ApiResponse.success("Email availability checked", data));
            
        } catch (Exception e) {
            log.error("Server error during email check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * Get current user information
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        try {
            // 세션에서 사용자 정보 가져오기
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("인증이 필요합니다"));
            }
            
            // 사용자 정보 조회
            User user = userService.findByUsername((String) session.getAttribute("username"));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("사용자를 찾을 수 없습니다"));
            }
            
            Map<String, Object> userData = convertUserToMap(user);
            
            return ResponseEntity.ok(ApiResponse.success("현재 사용자 정보 조회 성공", userData));
            
        } catch (Exception e) {
            log.error("Server error during get current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * Force logout user (Admin only)
     */
    @PostMapping("/admin/force-logout/{targetUserId}")
    public ResponseEntity<?> forceLogoutUser(@PathVariable Long targetUserId, HttpSession session) {
        try {
            // 현재 사용자가 관리자인지 확인
            Long currentUserId = (Long) session.getAttribute("userId");
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("인증이 필요합니다"));
            }
            
            User currentUser = userService.findByUsername((String) session.getAttribute("username"));
            if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Admin access required"));
            }
            
            // 대상 사용자의 세션 무효화
            sessionManagerService.removeUserSession(targetUserId);
            
            log.info("Force logout successful: adminUserId={}, targetUserId={}", currentUserId, targetUserId);
            
            return ResponseEntity.ok(ApiResponse.success("강제 로그아웃 성공"));
            
        } catch (Exception e) {
            log.error("Server error during force logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
    
    /**
     * Handle validation errors
     */
    private ResponseEntity<?> handleValidationErrors(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(ApiResponse.error("잘못된 입력입니다", errors));
        }
        return null;
    }
    
    /**
     * Save user information to session
     */
    private void saveUserToSession(User user, HttpSession session) {
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("isAdmin", user.getRole() == User.Role.ADMIN);
    }
    
    /**
     * Convert User entity to Map for response
     */
    private Map<String, Object> convertUserToMap(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("email", user.getEmail());
        userMap.put("isAdmin", user.getRole() == User.Role.ADMIN);
        userMap.put("createdAt", user.getCreatedAt());
        return userMap;
    }
} 