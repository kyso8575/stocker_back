package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.ApiResponse;
import com.stocker_back.stocker_back.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 스케줄러 상태 조회 컨트롤러
 * 자동 스케줄링 시스템의 완전 통합 모니터링
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Slf4j
public class SchedulerController {

    private final SchedulerService schedulerService;

    /**
     * 스케줄러 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSchedulerStatus() {
        try {
            Map<String, Object> statusData = schedulerService.getComprehensiveSchedulerStatus();
            
            // 문제가 있는 스케줄러가 있는지 확인
            boolean isHealthy = (Boolean) statusData.get("success");
            if (!isHealthy) {
                log.warn("Scheduler issues detected");
                return ResponseEntity.ok(ApiResponse.success(
                    "Some automated scheduler services have issues",
                    statusData
                ));
            }
            
            return ResponseEntity.ok(ApiResponse.success(
                "조회 성공",
                statusData
            ));
            
        } catch (Exception e) {
            log.error("Error retrieving scheduler status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류"));
        }
    }
} 