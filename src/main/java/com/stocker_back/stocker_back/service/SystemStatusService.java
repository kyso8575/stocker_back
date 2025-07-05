package com.stocker_back.stocker_back.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemStatusService {
    
    /**
     * 시스템 상태 정보를 반환
     * @param requestedBy 요청한 관리자 사용자명
     * @return 시스템 상태 데이터
     */
    public Map<String, Object> getSystemStatus(String requestedBy) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "healthy");
        data.put("uptime", "72h 15m 32s");
        data.put("activeConnections", 156);
        data.put("memoryUsage", "45%");
        data.put("requestedBy", requestedBy);
        return data;
    }
} 