package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.repository.StockSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * S&P 500 리스트를 웹스크래핑하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Sp500ScraperService {

    private final StockSymbolRepository stockSymbolRepository;
    private static final String SP500_URL = "https://www.slickcharts.com/sp500";

    /**
     * S&P 500 리스트를 웹스크래핑하여 데이터베이스를 업데이트합니다.
     * @return 업데이트된 심볼 목록
     */
    @Transactional
    public Set<String> updateSp500List() {
        log.info("Starting S&P 500 list update");
        
        try {
            // 웹스크래핑으로 S&P 500 심볼 목록 가져오기
            Set<String> sp500Symbols = scrapeSp500Symbols();
            log.info("Scraped {} S&P 500 symbols", sp500Symbols.size());
            
            // 모든 심볼의 S&P 500 상태를 false로 초기화
            stockSymbolRepository.resetSp500Status();
            log.info("Reset S&P 500 status for all symbols");
            
            // 웹스크래핑한 심볼들의 S&P 500 상태를 true로 설정
            stockSymbolRepository.updateSp500Status(sp500Symbols);
            log.info("Updated S&P 500 status for {} symbols", sp500Symbols.size());
            
            return sp500Symbols;
        } catch (Exception e) {
            log.error("Error updating S&P 500 list: ", e);
            throw new RuntimeException("Failed to update S&P 500 list: " + e.getMessage(), e);
        }
    }

    /**
     * S&P 500에 포함된 모든 주식 심볼을 조회합니다.
     * @return S&P 500 심볼 목록
     */
    @Transactional(readOnly = true)
    public Set<String> findAllSp500Symbols() {
        log.info("Retrieving all S&P 500 symbols");
        return new HashSet<>(stockSymbolRepository.findAllSp500Symbols());
    }

    /**
     * slickcharts.com에서 S&P 500 심볼 목록을 웹스크래핑합니다.
     * @return 웹스크래핑한 S&P 500 심볼 목록
     */
    private Set<String> scrapeSp500Symbols() throws IOException {
        log.info("Scraping S&P 500 symbols from {}", SP500_URL);
        
        Document doc = Jsoup.connect(SP500_URL)
                .userAgent("Mozilla/5.0")
                .get();
        
        Elements rows = doc.select("table.table-hover tbody tr");
        Set<String> symbols = new HashSet<>();
        
        for (Element row : rows) {
            // 세 번째 컬럼이 심볼 (예: AAPL)
            Element symbolCell = row.select("td:nth-child(3)").first();
            if (symbolCell != null) {
                String symbol = symbolCell.text().trim();
                if (!symbol.isEmpty()) {
                    symbols.add(symbol);
                }
            }
        }
        
        log.info("Successfully scraped {} S&P 500 symbols", symbols.size());
        return symbols;
    }
} 