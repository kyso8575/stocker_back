package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.Sp500ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sp500")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "S&P 500", description = "S&P 500 관련 API")
public class Sp500Controller {

    private final Sp500ScraperService sp500ScraperService;

    @Operation(
        summary = "S&P 500 리스트 업데이트",
        description = "S&P 500 리스트를 웹스크래핑하여 데이터베이스를 업데이트합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "S&P 500 리스트 업데이트 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/update")
    public ResponseEntity<Map<String, Object>> updateSp500List() {
        log.info("Received request to update S&P 500 list");
        
        try {
            Set<String> updatedSymbols = sp500ScraperService.updateSp500List();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Successfully updated S&P 500 list with %d symbols", updatedSymbols.size()));
            response.put("symbols", updatedSymbols);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating S&P 500 list: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Operation(
        summary = "S&P 500 심볼 목록 조회",
        description = "S&P 500에 포함된 모든 주식 심볼을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 심볼 목록 조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSp500Symbols() {
        log.info("Received request to get S&P 500 symbols");
        
        try {
            Set<String> sp500Symbols = sp500ScraperService.findAllSp500Symbols();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Found %d S&P 500 symbols", sp500Symbols.size()));
            response.put("symbols", sp500Symbols);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 symbols: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 