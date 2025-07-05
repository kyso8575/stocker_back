package com.stocker_back.stocker_back.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Slf4j
public class DateUtils {
    
    /**
     * 날짜 문자열을 LocalDateTime으로 파싱하는 메서드
     * @param dateStr 날짜 문자열 (YYYY-MM-DD 또는 YYYY-MM-DDTHH:mm:ss)
     * @param isStartOfDay true이면 하루의 시작(00:00:00), false이면 하루의 끝(23:59:59)
     * @return LocalDateTime 객체
     */
    public LocalDateTime parseDateTime(String dateStr, boolean isStartOfDay) {
        if (dateStr == null || dateStr.isEmpty()) {
            // 기본값: from은 과거 1년, to는 현재 시간
            return isStartOfDay ? 
                LocalDateTime.now().minusYears(1) : 
                LocalDateTime.now();
        }
        
        try {
            // YYYY-MM-DD 형식인 경우
            if (dateStr.length() == 10 && dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(dateStr);
                return isStartOfDay ? 
                    date.atStartOfDay() : 
                    date.atTime(23, 59, 59);
            }
            // YYYY-MM-DDTHH:mm:ss 형식인 경우
            else if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            // 기타 형식 시도
            else {
                LocalDate date = LocalDate.parse(dateStr);
                return isStartOfDay ? 
                    date.atStartOfDay() : 
                    date.atTime(23, 59, 59);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date: {}. Using default value.", dateStr);
            return isStartOfDay ? 
                LocalDateTime.now().minusYears(1) : 
                LocalDateTime.now();
        }
    }
} 