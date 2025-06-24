package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.dto.UserRegistrationDto;
import com.stocker_back.stocker_back.dto.UserLoginDto;
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
            // Check validation errors
            if (bindingResult.hasErrors()) {
                Map<String, String> errors = new HashMap<>();
                bindingResult.getFieldErrors().forEach(error -> 
                    errors.put(error.getField(), error.getDefaultMessage())
                );
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Input validation failed",
                    "errors", errors
                ));
            }
            
            // Process registration
            User user = userService.registerUser(registrationDto);
            
            // 중복 로그인 관리: 회원가입 후 자동 로그인 시에도 기존 세션 무효화
            sessionManagerService.registerUserSession(user.getId(), session.getId());
            
            // Save user info to session (auto login) - 메모리 최적화: 필수 정보만 저장
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userFullName", user.getFullName());
            session.setAttribute("userRole", user.getRole().name());
            
            log.info("Registration successful and auto login with session management: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration completed successfully",
                "user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName(),
                    "role", user.getRole().name()
                )
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Server error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
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
            // Check validation errors
            if (bindingResult.hasErrors()) {
                Map<String, String> errors = new HashMap<>();
                bindingResult.getFieldErrors().forEach(error -> 
                    errors.put(error.getField(), error.getDefaultMessage())
                );
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Input validation failed",
                    "errors", errors
                ));
            }
            
            // Process login
            User user = userService.loginUser(loginDto);
            
            // 중복 로그인 관리: 기존 세션 무효화 및 새 세션 등록
            sessionManagerService.registerUserSession(user.getId(), session.getId());
            
            // Save user info to session - 메모리 최적화: 필수 정보만 저장
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userFullName", user.getFullName());
            session.setAttribute("userRole", user.getRole().name());
            
            log.info("Login successful with duplicate login management: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName(),
                    "role", user.getRole().name()
                )
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Server error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
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
            
            // Check if user is logged in
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "User is not logged in"
                ));
            }
            
            String sessionId = session.getId();
            
            // 세션 관리자에서 사용자 세션 제거
            sessionManagerService.removeUserSession(userId);
            
            // Invalidate session
            session.invalidate();
            
            log.info("Logout successful with session cleanup: username={}, sessionId={}", username, sessionId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logout successful"
            ));
            
        } catch (Exception e) {
            log.error("Server error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
        }
    }
    
    /**
     * Username availability check API
     */
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        try {
            boolean exist = userService.isUsernameExists(username);
            return ResponseEntity.ok(Map.of(
                "exist", exist,
                "message", exist ? "Username is already taken" : "Username is available"
            ));
        } catch (Exception e) {
            log.error("Error checking username availability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
        }
    }
    
    /**
     * Email availability check API
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        try {
            boolean exist = userService.isEmailExists(email);
            return ResponseEntity.ok(Map.of(
                "exist", exist,
                "message", exist ? "Email is already in use" : "Email is available"
            ));
        } catch (Exception e) {
            log.error("Error checking email availability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "user", Map.of(
                    "id", userId,
                    "username", username,
                    "email", userEmail,
                    "fullName", userFullName,
                    "role", userRole
                )
            ));
        } catch (Exception e) {
            log.error("Error retrieving current user info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
        }
    }
    
    /**
     * 관리자용: 특정 사용자 강제 로그아웃 API
     */
    @PostMapping("/admin/force-logout/{targetUserId}")
    public ResponseEntity<?> forceLogoutUser(@PathVariable Long targetUserId, HttpSession session) {
        try {
            Long adminUserId = (Long) session.getAttribute("userId");
            if (adminUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }
            
            // 관리자 권한 확인은 AuthenticationInterceptor에서 처리됨
            sessionManagerService.forceLogoutUser(targetUserId);
            
            log.info("Admin {} forced logout for user: {}", adminUserId, targetUserId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User " + targetUserId + " has been forcefully logged out"
            ));
            
        } catch (Exception e) {
            log.error("Error during force logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
        }
    }
} 