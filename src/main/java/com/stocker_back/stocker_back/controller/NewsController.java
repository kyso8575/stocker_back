package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.stocker_back.stocker_back.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = CompanyNewsDTO.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/companies/{symbol}")
    public ResponseEntity<?> getCompanyNews(
            @PathVariable String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get company news for symbol: {}, from: {}, to: {}, count: {}", 
                symbol, from, to, count);
        
        if (count != null && count <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("잘못된 입력입니다"));
        }
        
        try {
            List<CompanyNewsDTO> newsItems = newsService.fetchCompanyNews(symbol, from, to, count);
            
            Map<String, Object> data = Map.of(
                "symbol", symbol.toUpperCase(),
                "data", newsItems,
                "count", newsItems.size(),
                "from", from,
                "to", to
            );
            
            return ResponseEntity.ok(ApiResponse.success(data));
            
        } catch (Exception e) {
            log.error("Error retrieving company news", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다"));
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = CompanyNewsDTO.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/market")
    public ResponseEntity<?> getMarketNews(
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam(required = false) Integer count) {
        
        log.info("Received request to get market news from {} to {} with count: {}", from, to, count);
        
        try {
            if (count != null && count <= 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error("잘못된 입력입니다"));
            }
            
            List<CompanyNewsDTO> newsItems = newsService.fetchMarketNews(from, to, count);
            
            Map<String, Object> data = Map.of(
                "data", newsItems,
                "count", newsItems.size(),
                "from", from,
                "to", to
            );
            
            return ResponseEntity.ok(ApiResponse.success(data));
            
        } catch (Exception e) {
            log.error("Error retrieving market news", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다"));
        }
    }
} 