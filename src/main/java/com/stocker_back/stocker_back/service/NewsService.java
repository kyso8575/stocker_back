package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.dto.CompanyNewsDTO;
import com.stocker_back.stocker_back.util.FinnhubApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 뉴스 정보 관리를 담당하는 서비스
 */
@Service
@Slf4j
public class NewsService {

    private final FinnhubApiClient finnhubApiClient;
    
    public NewsService(FinnhubApiClient finnhubApiClient) {
        this.finnhubApiClient = finnhubApiClient;
    }
    
    /**
     * 특정 기업의 뉴스를 Finnhub API에서 가져옴
     * 
     * @param symbol 주식 심볼 (예: AAPL)
     * @param from 시작 날짜 (형식: YYYY-MM-DD)
     * @param to 종료 날짜 (형식: YYYY-MM-DD)
     * @param count 반환할 뉴스 항목 제한 (선택사항)
     * @return 기업 뉴스 항목 목록
     */
    public List<CompanyNewsDTO> fetchCompanyNews(String symbol, String from, String to, Integer count) {
        log.info("Fetching company news for symbol: {}, from: {}, to: {}, count: {}", symbol, from, to, count);
        
        try {
            // Finnhub API 호출
            List<CompanyNewsDTO> newsItems = finnhubApiClient.get(
                    "/company-news",
                    new ParameterizedTypeReference<List<CompanyNewsDTO>>() {},
                    "symbol", symbol,
                    "from", from,
                    "to", to
            );
            
            if (newsItems == null || newsItems.isEmpty()) {
                log.warn("No news found for symbol: {} in date range {} to {}", symbol, from, to);
                return Collections.emptyList();
            }
            
            log.info("Fetched {} news items for {}", newsItems.size(), symbol);
            
            // count 제한이 지정된 경우 적용
            if (count != null && count > 0 && count < newsItems.size()) {
                return newsItems.subList(0, count);
            }
            
            return newsItems;
        } catch (Exception e) {
            log.error("Error fetching company news for symbol {}: ", symbol, e);
            throw e;
        }
    }
    
    /**
     * Finnhub API에서 일반 시장 뉴스를 가져옴
     * 
     * @param from 시작 날짜 (형식: YYYY-MM-DD)
     * @param to 종료 날짜 (형식: YYYY-MM-DD)
     * @param count 반환할 뉴스 항목 제한 (선택사항)
     * @return 시장 뉴스 항목 목록
     */
    public List<CompanyNewsDTO> fetchMarketNews(String from, String to, Integer count) {
        log.info("Fetching market news from: {}, to: {}, count: {}", from, to, count);
        
        try {
            // Finnhub API 호출
            List<CompanyNewsDTO> newsItems = finnhubApiClient.get(
                    "/news",
                    new ParameterizedTypeReference<List<CompanyNewsDTO>>() {},
                    "category", "general",
                    "from", from,
                    "to", to
            );
            
            if (newsItems == null || newsItems.isEmpty()) {
                log.warn("No market news found in date range {} to {}", from, to);
                return Collections.emptyList();
            }
            
            log.info("Fetched {} market news items", newsItems.size());
            
            // count 제한이 지정된 경우 적용
            if (count != null && count > 0 && count < newsItems.size()) {
                return newsItems.subList(0, count);
            }
            
            return newsItems;
        } catch (Exception e) {
            log.error("Error fetching market news: ", e);
            throw e;
        }
    }
} 