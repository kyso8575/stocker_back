package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.dto.UserRegistrationDto;
import com.stocker_back.stocker_back.dto.UserLoginDto;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
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
            
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS_REGISTER, convertUserToMap(user)));
            
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AuthResponseDto.error(ResponseMessages.ERROR_INVALID_INPUT));
        } catch (Exception e) {
            log.error("Server error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
            
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS_LOGIN, convertUserToMap(user)));
            
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AuthResponseDto.error(ResponseMessages.ERROR_INVALID_INPUT));
        } catch (Exception e) {
            log.error("Server error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * User logout API with session cleanup
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");
            
            // 로그인 상태 확인
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponseDto.error("User is not logged in"));
            }
            
            String sessionId = session.getId();
            
            // 세션 관리자에서 사용자 세션 제거
            sessionManagerService.removeUserSession(userId);
            
            // 세션 무효화
            session.invalidate();
            
            log.info("Logout successful with session cleanup: username={}, sessionId={}", username, sessionId);
            
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS_LOGOUT));
            
        } catch (Exception e) {
            log.error("Server error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * Username availability check API
     */
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        try {
            boolean exist = userService.isUsernameExists(username);
            Map<String, Object> data = Map.of(
                "exist", exist,
                "message", exist ? ResponseMessages.ERROR_USERNAME_EXISTS : "Username is available"
            );
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, data));
        } catch (Exception e) {
            log.error("Error checking username availability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * Email availability check API
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        try {
            boolean exist = userService.isEmailExists(email);
            Map<String, Object> data = Map.of(
                "exist", exist,
                "message", exist ? ResponseMessages.ERROR_EMAIL_EXISTS : "Email is available"
            );
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, data));
        } catch (Exception e) {
            log.error("Error checking email availability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * Current user information API
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");
            String userEmail = (String) session.getAttribute("userEmail");
            String userFullName = (String) session.getAttribute("userFullName");
            String userRole = (String) session.getAttribute("userRole");
            
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponseDto.error("Authentication required"));
            }
            
            Map<String, Object> userData = Map.of(
                "id", userId,
                "username", username,
                "email", userEmail,
                "fullName", userFullName,
                "role", userRole
            );
            
            return ResponseEntity.ok(AuthResponseDto.success("Current user information retrieved", userData));
            
        } catch (Exception e) {
            log.error("Server error while getting current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * Admin API: Force logout a specific user
     */
    @PostMapping("/admin/force-logout/{targetUserId}")
    public ResponseEntity<?> forceLogoutUser(@PathVariable Long targetUserId, HttpSession session) {
        try {
            // 현재 사용자가 관리자인지 확인
            String currentUserRole = (String) session.getAttribute("userRole");
            if (!"ADMIN".equals(currentUserRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AuthResponseDto.error("Admin access required"));
            }
            
            // 대상 사용자의 세션 무효화
            sessionManagerService.removeUserSession(targetUserId);
            
            log.info("Admin force logout successful: adminUserId={}, targetUserId={}", 
                    session.getAttribute("userId"), targetUserId);
            
            return ResponseEntity.ok(AuthResponseDto.success("User has been force logged out"));
            
        } catch (Exception e) {
            log.error("Server error during force logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    /**
     * 입력값 검증 오류 처리 헬퍼 메서드
     */
    private ResponseEntity<?> handleValidationErrors(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage()));
            
            log.warn("Validation failed: {}", errors);
            return ResponseEntity.badRequest().body(AuthResponseDto.error(ResponseMessages.ERROR_INVALID_INPUT));
        }
        return null;
    }
    
    /**
     * 사용자 정보를 세션에 저장하는 헬퍼 메서드
     */
    private void saveUserToSession(User user, HttpSession session) {
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userFullName", user.getFullName());
        session.setAttribute("userRole", user.getRole().name());
    }
    
    /**
     * User 객체를 Map으로 변환하는 헬퍼 메서드
     */
    private Map<String, Object> convertUserToMap(User user) {
        return Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "fullName", user.getFullName(),
            "role", user.getRole().name()
        );
    }
} 