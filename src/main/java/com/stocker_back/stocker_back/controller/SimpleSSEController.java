package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
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

import java.util.Map;

/**
 * SSE 실시간 거래 데이터 스트리밍 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/trades/stream")
@RequiredArgsConstructor
@Tag(name = "Trade Stream", description = "실시간 거래 데이터 스트리밍 API")
public class SimpleSSEController {
    
    private final SSEService sseService;
    
    @Operation(
        summary = "실시간 거래 데이터 스트리밍",
        description = "특정 주식 심볼의 실시간 거래 데이터를 SSE(Server-Sent Events)로 스트리밍합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "스트리밍 시작",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping(value = "/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTradesBySymbol(
            @Parameter(description = "주식 심볼 (예: AAPL)", required = true, example = "AAPL")
            @PathVariable String symbol,
            @Parameter(description = "업데이트 간격 (초)", example = "5")
            @RequestParam(defaultValue = "5") int interval) {
        
        log.info("Received request to stream trades for symbol: {} with interval: {} seconds", symbol, interval);
        
        try {
            return sseService.createSSEConnection(symbol, interval);
        } catch (Exception e) {
            log.error("Error creating SSE connection for symbol {}: {}", symbol, e.getMessage());
            throw new RuntimeException(ResponseMessages.format("Failed to create SSE connection for symbol %s", symbol), e);
        }
    }
    
    /**
     * SSE 연결 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSSEStatus() {
        try {
            Map<String, Object> statusData = Map.of(
                "service", "SSE Service",
                "status", "Active",
                "description", "Server-Sent Events streaming service"
            );
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.SUCCESS,
                statusData
            ));
        } catch (Exception e) {
            log.error("Error retrieving SSE status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 