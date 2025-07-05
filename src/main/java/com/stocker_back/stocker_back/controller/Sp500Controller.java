package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.service.Sp500Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sp500")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "S&P 500", description = "S&P 500 관련 API")
public class Sp500Controller {

    private final Sp500Service sp500Service;

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
    public ResponseEntity<?> updateSp500List() {
        log.info("Received request to update S&P 500 list");
        
        try {
            Set<String> updatedSymbols = sp500Service.updateSp500List();
            
            Map<String, Object> data = Map.of(
                "symbols", updatedSymbols,
                "count", updatedSymbols.size()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_PROCESSED_ITEMS, updatedSymbols.size()),
                data
            ));
        } catch (Exception e) {
            log.error("Error updating S&P 500 list: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }

    @Operation(
        summary = "S&P 500 심볼 목록 조회",
        description = "S&P 500에 포함된 모든 주식 심볼과 회사명을 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 심볼 목록 조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<?> getSp500Symbols() {
        log.info("Received request to get S&P 500 symbols");
        
        try {
            List<Map<String, String>> stockList = sp500Service.getSp500Symbols();
            
            Map<String, Object> data = Map.of(
                "stocks", stockList,
                "count", stockList.size()
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_RETRIEVED_COUNT, stockList.size()),
                data
            ));
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 symbols: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }

    @Operation(
        summary = "S&P 500 테이블 데이터 조회",
        description = "S&P 500 주식들의 테이블 표시용 데이터를 조회합니다. (가격, 변화율, 시가총액 등)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "S&P 500 테이블 데이터 조회 성공"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/table")
    public ResponseEntity<?> getSp500TableData() {
        log.info("Received request to get S&P 500 table data");
        
        try {
            List<Map<String, Object>> tableData = sp500Service.getSp500TableData();
            
            Map<String, Object> data = Map.of(
                "data", tableData,
                "totalCount", tableData.size()
            );
            
            return ResponseEntity.ok(AuthResponseDto.success(
                ResponseMessages.format(ResponseMessages.TEMPLATE_RETRIEVED_COUNT, tableData.size()),
                data
            ));
        } catch (Exception e) {
            log.error("Error retrieving S&P 500 table data: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
} 