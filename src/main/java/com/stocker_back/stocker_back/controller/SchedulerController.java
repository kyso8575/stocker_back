package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

/**
 * 스케줄러 상태 조회 컨트롤러
 * 자동 스케줄링 시스템의 완전 통합 모니터링
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scheduler", description = "자동화된 데이터 수집 스케줄러 관리 API")
public class SchedulerController {

    private final SchedulerService schedulerService;

    @Operation(
        summary = "통합 스케줄러 상태 조회",
        description = "모든 자동화된 스케줄러(재무 지표, 월간 데이터, 시세, 웹소켓)의 상태와 설정을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "스케줄러 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/status")
    public ResponseEntity<?> getSchedulerStatus() {
        log.info("Received request to get comprehensive scheduler status");
        
        try {
            Map<String, Object> schedulerData = schedulerService.getComprehensiveSchedulerStatus();
            boolean isHealthy = (Boolean) schedulerData.get("success");
            
            // 메시지와 노트 추가
            String message = isHealthy ? 
                "All automated scheduler services are running normally" : 
                ResponseMessages.format("Some automated scheduler services have issues: %s", 
                    schedulerData.get("health") instanceof Map ? 
                        ((Map<?, ?>) schedulerData.get("health")).get("error") : "Unknown error");
            
            schedulerData.put("message", message);
            schedulerData.put("note", "This is a fully automated system with 4 schedulers - no manual intervention required");
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                schedulerData
            ));
        } catch (Exception e) {
            log.error("Error getting comprehensive scheduler status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 