package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.service.Sp500ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class Sp500Controller {

    private final Sp500ScraperService sp500ScraperService;

    /**
     * S&P 500 리스트를 웹스크래핑하여 데이터베이스를 업데이트합니다.
     * @return 업데이트 결과를 담은 응답
     */
    @PostMapping("/update/sp500")
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

    /**
     * S&P 500에 포함된 모든 주식 심볼을 조회합니다.
     * @return S&P 500 심볼 목록을 담은 응답
     */
    @GetMapping("/sp500")
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