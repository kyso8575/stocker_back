package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.constant.ResponseMessages;
import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.dto.AuthResponseDto;
import com.stocker_back.stocker_back.dto.CompanyProfileDTO;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/company-profiles")
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
    public ResponseEntity<?> getCompanyProfile(@PathVariable String symbol) {
        log.info("Received request to get company profile for symbol: {}", symbol);
        
        try {
            Optional<StockSymbol> stockSymbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
            
            if (stockSymbolOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AuthResponseDto.error(ResponseMessages.format("Symbol %s not found in database", symbol)));
            }
            
            StockSymbol stockSymbol = stockSymbolOpt.get();
            
            if (stockSymbol.isProfileEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AuthResponseDto.error(ResponseMessages.format("No profile information available for %s", symbol)));
            }
            
            CompanyProfileDTO profileData = convertToCompanyProfileDTO(stockSymbol);
            return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, Map.of("profile", profileData)));
            
        } catch (Exception e) {
            log.error("Error retrieving company profile for symbol: {}", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @PostMapping("/admin/symbol/{symbol}")
    public ResponseEntity<?> fetchCompanyProfile(@PathVariable String symbol) {
        log.info("Processing company profile for symbol: {}", symbol);
        
        try {
            StockSymbol updatedSymbol = companyProfileService.fetchAndSaveSingleCompanyProfile(symbol);
            
            if (updatedSymbol == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AuthResponseDto.error(ResponseMessages.format("Symbol %s not found in database", symbol)));
            }
            
            if (!updatedSymbol.isProfileEmpty()) {
                CompanyProfileDTO profileData = convertToCompanyProfileDTO(updatedSymbol);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(AuthResponseDto.success(ResponseMessages.SUCCESS, Map.of("profile", profileData)));
            } else {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("symbol", symbol);
                responseData.put("isEmpty", true);
                return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, responseData));
            }
            
        } catch (Exception e) {
            log.error("Error fetching company profile for symbol {}: ", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    public ResponseEntity<?> fetchCompanyProfilesBatch(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch all company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        try {
            int updatedCount = companyProfileService.fetchAndSaveAllCompanyProfiles(batchSize, delayMs);
            
            long validProfilesCount = stockSymbolRepository.countByProfileEmptyFalse();
            long emptyProfilesCount = stockSymbolRepository.countByProfileEmptyTrue();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalProcessed", updatedCount);
            responseData.put("validProfiles", validProfilesCount);
            responseData.put("emptyProfiles", emptyProfilesCount);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponseDto.success(ResponseMessages.format(ResponseMessages.TEMPLATE_PROCESSED_ITEMS, updatedCount), responseData));
            
        } catch (Exception e) {
            log.error("Error fetching company profiles: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
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
    @PostMapping("/admin/sp500")
    public ResponseEntity<?> fetchSp500CompanyProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "0") int delayMs) {
        
        log.info("Received request to fetch S&P 500 company profiles with batchSize={}, delayMs={}", batchSize, delayMs);
        
        try {
            int updatedCount = companyProfileService.fetchAndSaveSp500CompanyProfiles(batchSize, delayMs);
            
            long validSp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyFalse();
            long emptySp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyTrue();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalProcessed", updatedCount);
            responseData.put("validSp500Profiles", validSp500ProfilesCount);
            responseData.put("emptySp500Profiles", emptySp500ProfilesCount);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponseDto.success(ResponseMessages.format(ResponseMessages.TEMPLATE_BATCH_PROCESSED, updatedCount, "S&P 500"), responseData));
            
        } catch (Exception e) {
            log.error("Error fetching S&P 500 company profiles: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(
        summary = "회사 프로필 업데이트",
        description = "특정 주식 심볼의 회사 프로필을 업데이트합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "업데이트 성공",
            content = @Content(schema = @Schema(implementation = CompanyProfileDTO.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{symbol}")
    public ResponseEntity<?> updateCompanyProfile(
        @PathVariable String symbol) {
        
        log.info("Received request to update company profile for symbol: {}", symbol);
        
        try {
            StockSymbol updatedSymbol = companyProfileService.fetchAndSaveSingleCompanyProfile(symbol);
            
            if (updatedSymbol != null) {
                CompanyProfileDTO profileData = convertToCompanyProfileDTO(updatedSymbol);
                return ResponseEntity.status(HttpStatus.CREATED)
                    .body(AuthResponseDto.success(ResponseMessages.SUCCESS, Map.of("profile", profileData)));
            } else {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("symbol", symbol);
                responseData.put("isEmpty", true);
                return ResponseEntity.ok(AuthResponseDto.success(ResponseMessages.SUCCESS, responseData));
            }
            
        } catch (Exception e) {
            log.error("Error fetching company profile for symbol {}: ", symbol, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(
        summary = "배치 회사 프로필 처리",
        description = "여러 주식 심볼의 회사 프로필을 배치로 처리합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "배치 처리 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> processBatchCompanyProfiles(
        @RequestBody List<String> symbols) {
        
        log.info("Received request to process batch company profiles for {} symbols", symbols.size());
        
        try {
            // Convert symbols to StockSymbol entities and process them
            List<StockSymbol> stockSymbols = new ArrayList<>();
            for (String symbol : symbols) {
                Optional<StockSymbol> stockSymbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
                if (stockSymbolOpt.isPresent()) {
                    stockSymbols.add(stockSymbolOpt.get());
                }
            }
            
            int updatedCount = 0;
            for (StockSymbol stockSymbol : stockSymbols) {
                StockSymbol updated = companyProfileService.fetchAndSaveSingleCompanyProfile(stockSymbol.getSymbol());
                if (updated != null) {
                    updatedCount++;
                }
            }
            
            long validProfilesCount = stockSymbolRepository.countByProfileEmptyFalse();
            long emptyProfilesCount = stockSymbolRepository.countByProfileEmptyTrue();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("updatedCount", updatedCount);
            responseData.put("validProfilesCount", validProfilesCount);
            responseData.put("emptyProfilesCount", emptyProfilesCount);
            responseData.put("symbols", symbols);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponseDto.success(
                    ResponseMessages.format(ResponseMessages.TEMPLATE_PROCESSED_ITEMS, updatedCount),
                    responseData
                ));
            
        } catch (Exception e) {
            log.error("Error fetching company profiles: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    @Operation(
        summary = "S&P 500 회사 프로필 처리",
        description = "S&P 500 지수의 모든 회사 프로필을 처리합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "S&P 500 처리 성공",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/sp500")
    public ResponseEntity<?> processSp500CompanyProfiles() {
        log.info("Received request to process S&P 500 company profiles");
        
        try {
            int updatedCount = companyProfileService.fetchAndSaveSp500CompanyProfiles(20, 0);
            
            long validSp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyFalse();
            long emptySp500ProfilesCount = stockSymbolRepository.countByIsSp500TrueAndProfileEmptyTrue();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("updatedCount", updatedCount);
            responseData.put("validSp500ProfilesCount", validSp500ProfilesCount);
            responseData.put("emptySp500ProfilesCount", emptySp500ProfilesCount);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponseDto.success(
                    ResponseMessages.format(ResponseMessages.TEMPLATE_BATCH_PROCESSED, updatedCount, "S&P 500"),
                    responseData
                ));
            
        } catch (Exception e) {
            log.error("Error fetching S&P 500 company profiles: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDto.error(ResponseMessages.ERROR_SERVER));
        }
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * StockSymbol 엔티티를 CompanyProfileDTO로 변환
     */
    private CompanyProfileDTO convertToCompanyProfileDTO(StockSymbol stockSymbol) {
        return CompanyProfileDTO.builder()
                .symbol(stockSymbol.getSymbol())
                .name(stockSymbol.getName())
                .country(stockSymbol.getCountry())
                .currency(stockSymbol.getCurrency())
                .estimateCurrency(stockSymbol.getEstimateCurrency())
                .exchange(stockSymbol.getExchange())
                .finnhubIndustry(stockSymbol.getFinnhubIndustry())
                .ipo(stockSymbol.getIpo())
                .logo(stockSymbol.getLogo())
                .phone(stockSymbol.getPhone())
                .shareOutstanding(stockSymbol.getShareOutstanding())
                .weburl(stockSymbol.getWeburl())
                .build();
    }
} 