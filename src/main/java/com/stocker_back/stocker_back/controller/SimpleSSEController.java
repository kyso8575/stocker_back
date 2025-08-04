package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.ApiResponse;
import com.stocker_back.stocker_back.service.SSEService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * SSE 실시간 거래 데이터 스트리밍 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/trades/stream")
@RequiredArgsConstructor
@Tag(name = "SSE Stream", description = "Server-Sent Events 실시간 거래 데이터 스트리밍 API")
public class SimpleSSEController {
    
    private final SSEService sseService;
    
    @Operation(
        summary = "SSE 연결 생성",
        description = "특정 주식 심볼에 대한 실시간 거래 데이터 SSE 연결을 생성합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "SSE 연결 생성 성공",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping(value = "/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTrades(
        @Parameter(description = "스트리밍할 주식 심볼", required = true, example = "AAPL")
        @PathVariable String symbol,
        @Parameter(description = "업데이트 간격 (초)", example = "5")
        @RequestParam(defaultValue = "5") int interval
    ) {
        log.info("SSE connection request for symbol: {} with interval: {} seconds", symbol, interval);
        
        try {
            SseEmitter emitter = sseService.createSSEConnection(symbol, interval);
            
            log.info("SSE connection created successfully for symbol: {}", symbol);
            
            return emitter;
            
        } catch (Exception e) {
            log.error("Failed to create SSE connection for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to create SSE connection for symbol " + symbol, e);
        }
    }
    
    @Operation(
        summary = "SSE 연결 상태 조회",
        description = "현재 활성화된 SSE 연결 상태를 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "SSE 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/status")
    public ResponseEntity<?> getSSEStatus() {
        log.info("Received request to get SSE status");
        
        try {
            Map<String, Object> statusData = Map.of(
                "service", "SSE Service",
                "status", "Active",
                "activeConnections", sseService.getActiveConnectionCount(),
                "description", "Server-Sent Events streaming service"
            );
            
            return ResponseEntity.ok(ApiResponse.success(
                "조회 성공",
                statusData
            ));
            
        } catch (Exception e) {
            log.error("Failed to get SSE status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류"));
        }
    }
} 