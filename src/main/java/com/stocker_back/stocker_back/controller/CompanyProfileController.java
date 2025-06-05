package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.service.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;
    private final StockSymbolRepository stockSymbolRepository;

    /**
     * Finnhub API에서 모든 주식 심볼에 대한 회사 프로필 정보를 가져와 데이터베이스에 저장
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 500)
     * @param symbol 선택적으로 특정 심볼을 지정하면 해당 심볼만 처리
     * @return 업데이트된 회사 프로필 수와 성공 여부를 담은 응답
     */
    @GetMapping("/fetch/company_profiles")
    public ResponseEntity<Map<String, Object>> fetchCompanyProfiles(
            @RequestParam(defaultValue = "20") int batchSize,
            @RequestParam(defaultValue = "500") int delayMs,
            @RequestParam(required = false) String symbol) {
        
        // 단일 심볼이 지정된 경우 fetchCompanyProfile 메소드 호출
        if (symbol != null && !symbol.isEmpty()) {
            log.info("Received request to fetch company profile for symbol: {} via company_profiles endpoint", symbol);
            return fetchCompanyProfile(symbol);
        }
        
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
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching company profiles: ", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 특정 주식 심볼에 대한 회사 프로필 정보를 가져오는 헬퍼 메서드
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @return 업데이트된 회사 프로필 정보와 성공 여부를 담은 응답
     */
    private ResponseEntity<Map<String, Object>> fetchCompanyProfile(String symbol) {
        
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
                } else {
                    response.put("message", String.format("No profile data found for %s, marked as empty", symbol));
                    response.put("isEmpty", true);
                }
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("symbol", symbol);
                response.put("message", String.format("Symbol %s not found in database", symbol));
                
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error fetching company profile for symbol {}: ", symbol, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("symbol", symbol);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 데이터베이스에서 특정 주식 심볼에 대한 회사 프로필 정보를 조회합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 회사 프로필 정보와 성공 여부를 담은 응답
     */
    @GetMapping("/info/company_profiles")
    public ResponseEntity<Map<String, Object>> getCompanyProfile(@RequestParam String symbol) {
        log.info("Received request to get company profile for symbol: {}", symbol);
        
        Map<String, Object> response = new HashMap<>();
        response.put("symbol", symbol);
        
        try {
            // 데이터베이스에서 심볼 정보 조회
            Optional<StockSymbol> stockSymbolOpt = stockSymbolRepository.findBySymbol(symbol.toUpperCase());
            
            if (stockSymbolOpt.isPresent()) {
                StockSymbol stockSymbol = stockSymbolOpt.get();
                
                // 프로필이 비어있는 경우에만 업데이트
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
                
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("Error retrieving company profile for symbol {}: {}", symbol, e.getMessage());
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 