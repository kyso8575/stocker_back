package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.dto.UserRegistrationDto;
import com.stocker_back.stocker_back.dto.UserLoginDto;
import com.stocker_back.stocker_back.service.UserService;
import com.stocker_back.stocker_back.service.SessionManagementService;
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
    private final SessionManagementService sessionManagementService;
    
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
            
            // Process registration (with current session ID for session management)
            User user = userService.registerUser(registrationDto, session.getId());
            
            // Save user info to session (auto login) - 메모리 최적화: 필수 정보만 저장
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userFullName", user.getFullName());
            
            log.info("Registration successful and auto login: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration completed successfully",
                "user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName()
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
     * User login API
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
            
            // Process login (with current session ID for session management)
            User user = userService.loginUser(loginDto, session.getId());
            
            // Save user info to session - 메모리 최적화: 필수 정보만 저장
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userFullName", user.getFullName());
            
            log.info("Login successful: userId={}, username={}, sessionId={}", 
                    user.getId(), user.getUsername(), session.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail(),
                    "fullName", user.getFullName()
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
     * User logout API
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
            
            // Invalidate session
            session.invalidate();
            
            log.info("Logout successful: username={}, sessionId={}", username, sessionId);
            
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
            boolean exists = userService.isUsernameExists(username);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "Username is already taken" : "Username is available"
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
            boolean exists = userService.isEmailExists(email);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "Email is already in use" : "Email is available"
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
                    "fullName", userFullName
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
     * Get session information for current user
     */
    @GetMapping("/session-info")
    public ResponseEntity<?> getSessionInfo(HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");
            
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }
            
            int activeSessionCount = sessionManagementService.getActiveSessionCount(userId);
            var sessionInfoList = sessionManagementService.getUserSessionInfo(userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionInfo", Map.of(
                    "currentSessionId", session.getId(),
                    "userId", userId,
                    "username", username,
                    "activeSessionCount", activeSessionCount,
                    "sessionDetails", sessionInfoList
                )
            ));
        } catch (Exception e) {
            log.error("Error retrieving session info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "A server error occurred"
            ));
        }
    }
} 