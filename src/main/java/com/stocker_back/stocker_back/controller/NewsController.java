package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
public class NewsController {

    private final NewsService newsService;

    /**
     * 특정 주식 심볼의 회사 뉴스를 조회합니다.
     * 
     * @param symbol 주식 심볼 (예: AAPL)
     * @param from 시작 날짜 (YYYY-MM-DD 형식)
     * @param to 종료 날짜 (YYYY-MM-DD 형식)
     * @param count 반환할 뉴스 항목 수 제한 (선택사항)
     * @return 회사 뉴스 데이터와 성공 여부
     */
    @Operation(
        summary = "회사 뉴스 조회",
        description = "특정 주식 심볼의 회사 뉴스를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = CompanyNewsDTO.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/companies/{symbol}")
    public ResponseEntity<?> getCompanyNews(
            @PathVariable String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get company news for symbol: {}, from: {}, to: {}, count: {}", 
                symbol, from, to, count);
        
        if (count <= 0) {
            return ResponseEntity.badRequest().body(AuthResponseDto.error(
                ResponseMessages.ERROR_INVALID_INPUT
            ));
        }
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchCompanyNews(symbol, from, to, count);
            
            String message = newsItems.isEmpty() ? 
                ResponseMessages.format("No news found for %s in date range %s to %s", symbol, from, to) :
                ResponseMessages.format("Successfully fetched %d news items for %s", newsItems.size(), symbol);
            
            Map<String, Object> data = Map.of(
                "symbol", symbol.toUpperCase(),
                "data", newsItems,
                "count", newsItems.size(),
                "from", from,
                "to", to
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(message, data));
            
        } catch (Exception e) {
            log.error("Error retrieving company news", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }

    /**
     * 전체 시장 뉴스를 조회합니다.
     * 
     * @param from 시작 날짜 (YYYY-MM-DD 형식)
     * @param to 종료 날짜 (YYYY-MM-DD 형식)
     * @param count 반환할 뉴스 항목 수 제한 (선택사항)
     * @return 시장 뉴스 데이터와 성공 여부
     */
    @Operation(
        summary = "시장 뉴스 조회",
        description = "특정 날짜 범위의 시장 뉴스를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = CompanyNewsDTO.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/market")
    public ResponseEntity<?> getMarketNews(
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get market news from {} to {} with count: {}", from, to, count);
        
        try {
            if (count != null && count <= 0) {
                return ResponseEntity.badRequest().body(AuthResponseDto.error(
                    ResponseMessages.ERROR_INVALID_INPUT
                ));
            }
            
            List<CompanyNewsDTO> newsItems = newsService.fetchMarketNews(from, to, count);
            
            String message = newsItems.isEmpty() ? 
                ResponseMessages.format("No market news found in date range %s to %s", from, to) :
                ResponseMessages.format("Successfully fetched %d market news items", newsItems.size());
            
            Map<String, Object> data = Map.of(
                "data", newsItems,
                "count", newsItems.size(),
                "from", from,
                "to", to
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(message, data));
            
        } catch (Exception e) {
            log.error("Error retrieving market news", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 