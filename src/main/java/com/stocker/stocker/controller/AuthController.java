package com.stocker.stocker.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocker.stocker.domain.User;
import com.stocker.stocker.dto.AuthResponse;
import com.stocker.stocker.dto.LoginRequest;
import com.stocker.stocker.dto.SignupRequest;
import com.stocker.stocker.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    
    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }
    
    /**
     * 회원가입 처리
     * @param signupRequest 회원가입 정보
     * @return 회원가입 결과
     */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signupRequest) {
        logger.info("회원가입 요청: {}", signupRequest.getUsername());
        
        try {
            User user = userService.registerUser(signupRequest);
            
            AuthResponse response = AuthResponse.builder()
                    .success(true)
                    .username(user.getUsername())
                    .message("회원가입이 완료되었습니다.")
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("회원가입 실패: {}", e.getMessage());
            
            AuthResponse response = AuthResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
            
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("회원가입 처리 중 오류 발생", e);
            
            AuthResponse response = AuthResponse.builder()
                    .success(false)
                    .message("회원가입 처리 중 오류가 발생했습니다.")
                    .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 로그인 상태 확인
     * @param session 세션 정보
     * @return 로그인 상태
     */
    @GetMapping("/status")
    public ResponseEntity<?> getAuthStatus(HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal().toString())) {
            User user = (User) auth.getPrincipal();
            
            // 마지막 로그인 시간 업데이트
            userService.updateLastLogin(user.getUsername());
            
            AuthResponse response = AuthResponse.builder()
                    .success(true)
                    .username(user.getUsername())
                    .message("로그인 상태입니다.")
                    .build();
            
            return ResponseEntity.ok(response);
        } else {
            AuthResponse response = AuthResponse.builder()
                    .success(false)
                    .message("로그인되지 않은 상태입니다.")
                    .build();
            
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 수동 로그인 처리 (RESTful API용)
     * @param loginRequest 로그인 정보
     * @param request HTTP 요청
     * @return 로그인 결과
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        logger.info("REST API 로그인 요청: {}", loginRequest.getUsername());
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword())
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 세션에 사용자 정보 저장
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            
            User userDetails = (User) authentication.getPrincipal();
            
            // 마지막 로그인 시간 업데이트
            userService.updateLastLogin(userDetails.getUsername());
            
            AuthResponse response = AuthResponse.builder()
                    .success(true)
                    .username(userDetails.getUsername())
                    .message("로그인에 성공했습니다.")
                    .sessionId(session.getId())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.warn("로그인 실패: {}", e.getMessage());
            
            AuthResponse response = AuthResponse.builder()
                    .success(false)
                    .message("로그인에 실패했습니다. 사용자명과 비밀번호를 확인해주세요.")
                    .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 수동 로그아웃 처리 (RESTful API용)
     * @param request HTTP 요청
     * @param response HTTP 응답
     * @return 로그아웃 결과
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        
        SecurityContextHolder.clearContext();
        
        AuthResponse authResponse = AuthResponse.builder()
                .success(true)
                .message("로그아웃 되었습니다.")
                .build();
        
        return ResponseEntity.ok(authResponse);
    }
}