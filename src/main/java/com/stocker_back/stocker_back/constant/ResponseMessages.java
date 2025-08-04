package com.stocker_back.stocker_back.constant;

/**
 * API 응답 메시지 상수들을 정의하는 클래스
 */
public final class ResponseMessages {
    
    // ===== 기본 메시지 =====
    public static final String SUCCESS = "요청이 성공적으로 처리되었습니다";
    public static final String ERROR_SERVER = "서버 오류가 발생했습니다";
    public static final String ERROR_INVALID_INPUT = "잘못된 입력입니다";
    public static final String ERROR_UNAUTHORIZED = "인증이 필요합니다";
    public static final String ERROR_NOT_FOUND = "요청한 데이터를 찾을 수 없습니다";
    
    // ===== 유틸리티 메서드 =====
    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
    
    // 생성자 방지
    private ResponseMessages() {
        throw new UnsupportedOperationException("Utility class");
    }
} 