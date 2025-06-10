package com.stocker_back.stocker_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManagementService {
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 특정 사용자의 모든 활성 세션을 무효화합니다. (최적화된 버전)
     * @param userId 사용자 ID
     * @param currentSessionId 현재 세션 ID (무효화에서 제외)
     */
    @Transactional
    public void invalidateUserSessions(Long userId, String currentSessionId) {
        try {
            // 최적화: 직접적으로 해당 사용자의 세션만 조회
            String findUserSessionsSql = """
                SELECT DISTINCT ssa.SESSION_PRIMARY_ID 
                FROM SPRING_SESSION_ATTRIBUTES ssa 
                WHERE ssa.ATTRIBUTE_NAME = 'userId' 
                AND ssa.SESSION_PRIMARY_ID != ?
                AND EXISTS (
                    SELECT 1 FROM SPRING_SESSION ss 
                    WHERE ss.PRIMARY_ID = ssa.SESSION_PRIMARY_ID 
                    AND ss.EXPIRY_TIME > EXTRACT(EPOCH FROM NOW()) * 1000
                )
                """;
            
            List<String> userSessionIds = jdbcTemplate.queryForList(
                findUserSessionsSql, String.class, currentSessionId
            );
            
            if (userSessionIds.isEmpty()) {
                log.debug("No existing sessions found for userId: {}", userId);
                return;
            }
            
            log.info("Found {} potential sessions to check for userId: {}", userSessionIds.size(), userId);
            
            // 배치로 세션 무효화 처리
            int invalidatedCount = invalidateSessionsBatch(userSessionIds, userId);
            
            if (invalidatedCount > 0) {
                log.info("Successfully invalidated {} sessions for userId: {}", invalidatedCount, userId);
            }
            
        } catch (Exception e) {
            log.error("Error invalidating sessions for userId: {}", userId, e);
            // 예외가 발생해도 로그인은 계속 진행되도록 예외를 던지지 않습니다
        }
    }
    
    /**
     * 배치로 세션들을 무효화합니다.
     */
    private int invalidateSessionsBatch(List<String> sessionIds, Long userId) {
        int invalidatedCount = 0;
        
        for (String sessionId : sessionIds) {
            try {
                // 간단한 직접 삭제 방식 사용 (메모리 효율적)
                if (deleteSessionIfBelongsToUser(sessionId, userId)) {
                    invalidatedCount++;
                    log.debug("Invalidated session: {} for userId: {}", sessionId, userId);
                }
            } catch (Exception e) {
                log.debug("Error invalidating session {}: {}", sessionId, e.getMessage());
            }
        }
        
        return invalidatedCount;
    }
    
    /**
     * 세션이 특정 사용자의 것이면 삭제합니다. (메모리 효율적)
     */
    private boolean deleteSessionIfBelongsToUser(String sessionId, Long userId) {
        try {
            // 먼저 해당 세션이 userId를 가지고 있는지 간단히 확인
            String checkSql = """
                SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES 
                WHERE SESSION_PRIMARY_ID = ? AND ATTRIBUTE_NAME = 'userId'
                """;
            
            Integer hasUserId = jdbcTemplate.queryForObject(checkSql, Integer.class, sessionId);
            
            if (hasUserId == null || hasUserId == 0) {
                return false;
            }
            
            // 세션 삭제 (속성과 세션 모두)
            int attributesDeleted = jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ?", 
                sessionId
            );
            
            int sessionsDeleted = jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION WHERE PRIMARY_ID = ?", 
                sessionId
            );
            
            log.debug("Deleted session {}: {} attributes, {} sessions", 
                     sessionId, attributesDeleted, sessionsDeleted);
                     
            return sessionsDeleted > 0;
                     
        } catch (Exception e) {
            log.debug("Error deleting session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * 특정 사용자의 활성 세션 수를 조회합니다. (최적화된 버전)
     * @param userId 사용자 ID
     * @return 활성 세션 수
     */
    public int getActiveSessionCount(Long userId) {
        try {
            // 최적화: 만료되지 않은 세션만 카운트
            String countSql = """
                SELECT COUNT(DISTINCT ssa.SESSION_PRIMARY_ID) 
                FROM SPRING_SESSION_ATTRIBUTES ssa 
                JOIN SPRING_SESSION ss ON ssa.SESSION_PRIMARY_ID = ss.PRIMARY_ID
                WHERE ssa.ATTRIBUTE_NAME = 'userId'
                AND ss.EXPIRY_TIME > EXTRACT(EPOCH FROM NOW()) * 1000
                """;
            
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("Error counting active sessions for userId: {}", userId, e);
            return 0;
        }
    }
    
    /**
     * 주기적으로 만료된 세션을 정리합니다. (매 10분마다 실행)
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    @Transactional
    public void scheduledCleanupExpiredSessions() {
        try {
            cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("Error in scheduled session cleanup", e);
        }
    }
    
    /**
     * 모든 만료된 세션을 정리합니다. (성능 최적화)
     */
    @Transactional
    public void cleanupExpiredSessions() {
        try {
            // 속성부터 삭제 (외래키 제약 조건 고려)
            String deleteExpiredAttributesSql = """
                DELETE FROM SPRING_SESSION_ATTRIBUTES 
                WHERE SESSION_PRIMARY_ID IN (
                    SELECT PRIMARY_ID FROM SPRING_SESSION 
                    WHERE EXPIRY_TIME < EXTRACT(EPOCH FROM NOW()) * 1000
                )
                """;
            
            int attributesDeleted = jdbcTemplate.update(deleteExpiredAttributesSql);
            
            // 만료된 세션 삭제
            String deleteExpiredSessionsSql = """
                DELETE FROM SPRING_SESSION 
                WHERE EXPIRY_TIME < EXTRACT(EPOCH FROM NOW()) * 1000
                """;
            
            int sessionsDeleted = jdbcTemplate.update(deleteExpiredSessionsSql);
            
            if (sessionsDeleted > 0) {
                log.info("Cleaned up {} expired sessions ({} attributes)", 
                        sessionsDeleted, attributesDeleted);
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up expired sessions", e);
        }
    }
    
    /**
     * 특정 사용자의 세션 정보를 조회합니다. (최적화된 버전)
     * @param userId 사용자 ID
     * @return 세션 정보 목록
     */
    public List<Map<String, Object>> getUserSessionInfo(Long userId) {
        try {
            // 최적화: 만료되지 않은 세션만 조회
            String sessionInfoSql = """
                SELECT ss.PRIMARY_ID, ss.SESSION_ID, ss.CREATION_TIME, ss.LAST_ACCESS_TIME, ss.EXPIRY_TIME
                FROM SPRING_SESSION ss
                JOIN SPRING_SESSION_ATTRIBUTES ssa ON ss.PRIMARY_ID = ssa.SESSION_PRIMARY_ID
                WHERE ssa.ATTRIBUTE_NAME = 'userId'
                AND ss.EXPIRY_TIME > EXTRACT(EPOCH FROM NOW()) * 1000
                LIMIT 10
                """;
            
            return jdbcTemplate.queryForList(sessionInfoSql);
                
        } catch (Exception e) {
            log.error("Error getting session info for userId: {}", userId, e);
            return List.of();
        }
    }
} 