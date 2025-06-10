package com.stocker_back.stocker_back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Component
@Slf4j
public class AuthenticationInterceptor implements HandlerInterceptor {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        String requestURI = request.getRequestURI();
        log.debug("AuthenticationInterceptor checking: {}", requestURI);
        
        // 인증이 필요한 경로인지 확인
        if (requiresAuthentication(requestURI)) {
            HttpSession session = request.getSession(false);
            Long userId = null;
            
            if (session != null) {
                userId = (Long) session.getAttribute("userId");
            }
            
            if (userId == null) {
                log.warn("Unauthorized access attempt to: {}", requestURI);
                
                // API 요청인지 확인
                String acceptHeader = request.getHeader("Accept");
                String contentType = request.getHeader("Content-Type");
                
                if (isApiRequest(request, acceptHeader, contentType)) {
                    // API 요청의 경우 JSON 응답
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    
                    Map<String, Object> errorResponse = Map.of(
                        "success", false,
                        "message", "로그인이 필요합니다",
                        "redirectUrl", "/login"
                    );
                    
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                } else {
                    // 웹 요청의 경우 로그인 페이지로 리다이렉트
                    response.sendRedirect("/login");
                }
                
                return false; // 요청 처리 중단
            }
            
            // 사용자 정보를 request에 저장 (컨트롤러에서 사용 가능)
            request.setAttribute("userId", userId);
            request.setAttribute("username", session.getAttribute("username"));
        }
        
        return true; // 요청 계속 처리
    }
    
    /**
     * 인증이 필요한 경로인지 확인
     */
    private boolean requiresAuthentication(String requestURI) {
        // 인증이 필요한 경로들
        String[] authenticatedPaths = {
            "/api/watchlist",
            "/api/auth/me",
            "/api/auth/logout"
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