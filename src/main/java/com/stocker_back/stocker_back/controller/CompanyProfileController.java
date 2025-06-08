package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.StockSymbol;
import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import com.stocker_back.stocker_back.service.CompanyProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stocks/company-profiles")
@RequiredArgsConstructor
@Slf4j
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;
    private final StockSymbolRepository stockSymbolRepository;

    /**
     * 모든 주식 심볼에 대한 회사 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 0, API 클라이언트에서 rate limit 적용)
     * @return 업데이트된 회사 프로필 수와 성공 여부를 담은 응답
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> fetchAllCompanyProfiles(
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
    
    /**
     * 특정 주식 심볼에 대한 회사 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param symbol 주식 심볼 코드 (예: AAPL)
     * @return 업데이트된 회사 프로필 정보와 성공 여부를 담은 응답
     */
    @PostMapping("/{symbol}")
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
    
    /**
     * 데이터베이스에서 특정 주식 심볼에 대한 회사 프로필 정보를 조회합니다.
     * @param symbol 주식 심볼 (예: AAPL)
     * @return 회사 프로필 정보와 성공 여부를 담은 응답
     */
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

    /**
     * S&P 500 종목들에 대한 회사 프로필 정보를 Finnhub API에서 가져와 데이터베이스에 저장
     * @param batchSize 한 번에 처리할 주식 수 (기본값: 20)
     * @param delayMs API 호출 사이의 지연 시간(밀리초) (기본값: 0, API 클라이언트에서 rate limit 적용)
     * @return 업데이트된 회사 프로필 수와 성공 여부를 담은 응답
     */
    @PostMapping("/sp500")
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