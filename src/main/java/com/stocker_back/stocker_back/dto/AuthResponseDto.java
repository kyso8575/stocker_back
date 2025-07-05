package com.stocker_back.stocker_back.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AuthResponseDto {
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private Map<String, String> errors;
    
    // 기본 생성자
    public AuthResponseDto(boolean success, String message, Map<String, Object> data, Map<String, String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }
    
    // 성공 응답용 정적 메서드
    public static AuthResponseDto success(String message, Map<String, Object> data) {
        return new AuthResponseDto(true, message, data, null);
    }
    
    // 단순 성공 응답용 정적 메서드
    public static AuthResponseDto success(String message) {
        return new AuthResponseDto(true, message, null, null);
    }
    
    // 에러 응답용 정적 메서드 (검증 에러 포함)
    public static AuthResponseDto error(String message, Map<String, String> errors) {
        return new AuthResponseDto(false, message, null, errors);
    }
    
    // 단순 에러 응답용 정적 메서드
    public static AuthResponseDto error(String message) {
        return new AuthResponseDto(false, message, null, null);
    }
} 