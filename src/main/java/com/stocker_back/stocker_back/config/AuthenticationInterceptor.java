package com.stocker_back.stocker_back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                           @NonNull HttpServletResponse response, 
                           @NonNull Object handler) throws Exception {
        
        String requestURI = request.getRequestURI();
        log.debug("AuthenticationInterceptor checking: {}", requestURI);
        
        // 인증이 필요한 경로인지 확인
        if (requiresAuthentication(requestURI)) {
            return handleAuthentication(request, response, requestURI);
        }
        
        return true; // 공개 접근 허용
    }
    
    /**
     * 인증 처리 (일반 사용자 및 관리자)
     */
    private boolean handleAuthentication(HttpServletRequest request, HttpServletResponse response, String requestURI) throws Exception {
        HttpSession session = request.getSession(false);
        Long userId = null;
        
        if (session != null) {
            userId = (Long) session.getAttribute("userId");
        }
        
        if (userId == null) {
            log.warn("Unauthorized access attempt (no login) to: {}", requestURI);
            return sendUnauthorizedResponse(request, response, "로그인이 필요합니다");
        }
        
        // 사용자 정보 조회
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User not found for userId: {} accessing: {}", userId, requestURI);
            return sendUnauthorizedResponse(request, response, "사용자 정보를 찾을 수 없습니다");
        }
        
        // 관리자 권한이 필요한 경로인지 확인
        if (requiresAdminRole(requestURI)) {
            if (user.getRole() != User.Role.ADMIN) {
                log.warn("Forbidden admin access attempt by userId: {} to: {}", userId, requestURI);
                return sendForbiddenResponse(request, response, "관리자 권한이 필요합니다");
            }
            log.info("Admin access granted to userId: {} for: {}", userId, requestURI);
        } else {
            log.info("User access granted to userId: {} for: {}", userId, requestURI);
        }
        
        // 사용자 정보를 request에 저장
        request.setAttribute("userId", userId);
        request.setAttribute("username", user.getUsername());
        request.setAttribute("userRole", user.getRole().name());
        
        return true;
    }
    
    /**
     * 401 Unauthorized 응답 전송
     */
    private boolean sendUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        
        if (isApiRequest(request, acceptHeader, contentType)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", message,
                "redirectUrl", "/login"
            );
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } else {
            response.sendRedirect("/login");
        }
        
        return false;
    }
    
    /**
     * 403 Forbidden 응답 전송
     */
    private boolean sendForbiddenResponse(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        
        if (isApiRequest(request, acceptHeader, contentType)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", message,
                "code", "ADMIN_REQUIRED"
            );
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        }
        
        return false;
    }
    
    /**
     * 관리자 권한이 필요한 경로인지 확인
     */
    private boolean requiresAdminRole(String requestURI) {
        // /admin이 포함된 모든 경로는 관리자 권한 필요
        if (requestURI.contains("/admin")) {
            return true;
        }
        
        String[] adminPaths = {
            "/api/admin",           // 모든 관리자 API의 기본 경로
            "/api/admin/data",      // 데이터 수집/수정 API
            "/api/admin/system",    // 시스템 관리
            "/api/admin/users",     // 사용자 관리
            "/api/admin/websocket", // 웹소켓 제어
            "/api/admin/database",  // 데이터베이스 관리
            "/api/auth/admin"       // 관리자용 세션 관리
        };
        
        for (String path : adminPaths) {
            if (requestURI.startsWith(path)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 일반 인증이 필요한 경로인지 확인
     */
    private boolean requiresAuthentication(String requestURI) {
        // 관리자 권한이 필요한 경로도 인증이 필요하므로 먼저 확인
        if (requiresAdminRole(requestURI)) {
            return true;
        }
        
        String[] authenticatedPaths = {
            "/api/watchlist",
            "/api/auth/me",
            "/api/auth/logout",
            "/api/profile"
            // 필요에 따라 추가
        };
        
        for (String path : authenticatedPaths) {
            if (requestURI.startsWith(path)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * API 요청인지 확인
     */
    private boolean isApiRequest(HttpServletRequest request, String acceptHeader, String contentType) {
        return (acceptHeader != null && acceptHeader.contains("application/json")) ||
               (contentType != null && contentType.contains("application/json")) ||
               request.getRequestURI().startsWith("/api/");
    }
} 