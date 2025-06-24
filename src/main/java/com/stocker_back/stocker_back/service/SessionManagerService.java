package com.stocker_back.stocker_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManagerService {
    
    private final JdbcTemplate jdbcTemplate;
    
    // 사용자별 활성 세션 ID를 메모리에 캐시
    private final Map<Long, String> userActiveSessionMap = new ConcurrentHashMap<>();
    
    /**
     * 새로운 세션 등록 및 기존 세션 무효화
     */
    public void registerUserSession(Long userId, String newSessionId) {
        log.info("Registering new session for user: {}, sessionId: {}", userId, newSessionId);
        
        // 기존 세션이 있다면 무효화
        String oldSessionId = userActiveSessionMap.get(userId);
        if (oldSessionId != null && !oldSessionId.equals(newSessionId)) {
            invalidateSession(oldSessionId);
            log.info("Invalidated old session for user: {}, oldSessionId: {}", userId, oldSessionId);
        }
        
        // 새로운 세션 등록
        userActiveSessionMap.put(userId, newSessionId);
        log.info("Registered new session for user: {}, newSessionId: {}", userId, newSessionId);
    }
    
    /**
     * 사용자 세션 제거 (로그아웃 시)
     */
    public void removeUserSession(Long userId) {
        String sessionId = userActiveSessionMap.remove(userId);
        if (sessionId != null) {
            log.info("Removed session for user: {}, sessionId: {}", userId, sessionId);
        }
    }
    
    /**
     * 특정 세션 ID를 데이터베이스에서 무효화
     */
    private void invalidateSession(String sessionId) {
        try {
            // Spring Session JDBC 테이블에서 세션 삭제
            int deletedRows = jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION WHERE SESSION_ID = ?", 
                sessionId
            );
            
            if (deletedRows > 0) {
                log.info("Successfully invalidated session from database: {}", sessionId);
                
                // 세션 속성도 삭제
                jdbcTemplate.update(
                    "DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ?", 
                    sessionId
                );
            } else {
                log.warn("Session not found in database: {}", sessionId);
            }
            
        } catch (Exception e) {
            log.error("Error invalidating session: {}", sessionId, e);
        }
    }
    
    /**
     * 특정 사용자의 모든 세션 정리 (관리자용)
     */
    public void forceLogoutUser(Long userId) {
        String sessionId = userActiveSessionMap.remove(userId);
        if (sessionId != null) {
            invalidateSession(sessionId);
            log.info("Force logout user: {}, sessionId: {}", userId, sessionId);
        }
    }
    
    /**
     * 애플리케이션 시작 시 기존 세션 정보 복구
     */
    @PostConstruct
    public void initializeActiveSessions() {
        try {
            log.info("Initializing active sessions from database...");
            
            // 데이터베이스에서 현재 유효한 세션들을 조회하여 메모리 맵에 복구
            List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT s.SESSION_ID, a.ATTRIBUTE_BYTES " +
                "FROM SPRING_SESSION s " +
                "JOIN SPRING_SESSION_ATTRIBUTES a ON s.SESSION_ID = a.SESSION_PRIMARY_ID " +
                "WHERE a.ATTRIBUTE_NAME = 'userId' AND s.EXPIRY_TIME > ?"
                , System.currentTimeMillis()
            );
            
            for (Map<String, Object> sessionData : sessions) {
                String sessionId = (String) sessionData.get("SESSION_ID");
                // 실제 환경에서는 ATTRIBUTE_BYTES를 역직렬화해야 함
                // 여기서는 단순화를 위해 스킵하고, 런타임에서 동적으로 관리
                log.debug("Found existing session in database: {}", sessionId);
            }
            
            log.info("Completed initializing active sessions");
            
        } catch (Exception e) {
            log.error("Error initializing active sessions", e);
        }
    }
} 