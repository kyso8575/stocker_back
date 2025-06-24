package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.service.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/company-profiles")  // stocks 제거
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Company Profile", description = "회사 프로필 API (일반 사용자용)")
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;
    private final StockSymbolRepository stockSymbolRepository;

    @Operation(
        summary = "단일 회사 프로필 조회",
        description = "특정 주식 심볼의 회사 프로필 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "회사 프로필 조회 성공"),
        @ApiResponse(responseCode = "404", description = "회사 프로필을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getCompanyProfile(@PathVariable String symbol) {
        log.info("Received request to get company profile for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            // 데이터베이스에서 심볼 정보 조회
            Optional<StockSymbol> stockSymbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
            
            if (stockSymbolOpt.isPresent()) {
                StockSymbol stockSymbol = stockSymbolOpt.get();
                
                // 프로필이 비어있는 경우
                if (stockSymbol.isProfileEmpty()) {
                    response.put("success", false);
                    response.put("message", String.format("No profile information available for %s", symbol));
                    return ResponseEntity.ok(response);
                }
                
                response.put("success", true);
                response.put("data", stockSymbol);
                response.put("message", String.format("Successfully retrieved company profile for %s", symbol));
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", String.format("Symbol %s not found in database", symbol));
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving company profile for symbol {}: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "단일 회사 프로필 수집",
        description = "특정 주식 심볼의 회사 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "회사 프로필 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/symbol/{symbol}")  // 관리자용 경로
    public ResponseEntity<Map<String, Object>> fetchCompanyProfile(@PathVariable String symbol) {
        
        log.info("Processing company profile for symbol: {}", symbol);
        
        try {
            StockSymbol updatedSymbol = companyProfileService.fetchAndSaveSingleCompanyProfile(symbol);
            
            Map<String, Object> response = new HashMap<>();
            
            if (updatedSymbol != null) {
                response.put("success", true);
                response.put("data", updatedSymbol);
                
                // 프로필이 비어있지 않은 경우에만 응답에 포함
                if (!updatedSymbol.isProfileEmpty()) {
                    response.put("message", String.format("Successfully updated company profile for %s", symbol));
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                    response.put("message", String.format("No profile data found for %s, marked as empty", symbol));
                    response.put("isEmpty", true);
                    return ResponseEntity.ok(response);
                }
            } else {
                response.put("success", false);
                response.put("symbol", symbol);
                response.put("message", String.format("Symbol %s not found in database", symbol));
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            log.error("Error fetching company profile for symbol {}: ", symbol, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("symbol", symbol);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "회사 프로필 일괄 수집",
        description = "여러 주식 심볼의 회사 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "회사 프로필 일괄 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/batch")
    public ResponseEntity<Map<String, Object>> fetchCompanyProfilesBatch(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch all company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        try {
            int updatedCount = companyProfileService.fetchAndSaveAllCompanyProfiles(batchSize, delayMs);
            
            // 빈 프로필과 유효한 프로필 개수 조회
            long validProfilesCount = stockSymbolRepository.countByProfileEmptyFalse();
            long emptyProfilesCount = stockSymbolRepository.countByProfileEmptyTrue();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalProcessed", updatedCount);
            response.put("validProfiles", validProfilesCount);
            response.put("emptyProfiles", emptyProfilesCount);
            response.put("message", String.format("Successfully processed %d company profiles (valid: %d, empty: %d)", 
                    updatedCount, validProfilesCount, emptyProfilesCount));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error fetching company profiles: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "S&P 500 회사 프로필 일괄 수집",
        description = "S&P 500에 포함된 모든 회사의 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장합니다. (관리자 전용)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "회사 프로필 일괄 수집 성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 접근"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/admin/sp500")  // S&P 500 관련 경로 수정
    public ResponseEntity<Map<String, Object>> fetchSp500CompanyProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch S&P 500 company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        try {
            int updatedCount = companyProfileService.fetchAndSaveSp500CompanyProfiles(batchSize, delayMs);
            
            // S&P 500 중 빈 프로필과 유효한 프로필 개수 조회
            long validSp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyFalse();
            long emptySp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyTrue();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalProcessed", updatedCount);
            response.put("validSp500Profiles", validSp500ProfilesCount);
            response.put("emptySp500Profiles", emptySp500ProfilesCount);
            response.put("message", String.format("Successfully processed %d S&P 500 company profiles (valid: %d, empty: %d)", 
                    updatedCount, validSp500ProfilesCount, emptySp500ProfilesCount));
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error fetching S&P 500 company profiles: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 