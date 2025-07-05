package com.stocker_back.stocker_back.constant;

/**
 * API 응답 메시지 상수들을 정의하는 클래스
 */
public final class ResponseMessages {
    
    // ===== 기본 성공/실패 메시지 =====
    public static final String SUCCESS = "요청이 성공적으로 처리되었습니다";
    public static final String ERROR_SERVER = "서버 오류가 발생했습니다";
    public static final String ERROR_NOT_FOUND = "요청한 데이터를 찾을 수 없습니다";
    public static final String ERROR_INVALID_INPUT = "잘못된 입력입니다";
    public static final String ERROR_UNAUTHORIZED = "인증이 필요합니다";
    public static final String ERROR_FORBIDDEN = "접근 권한이 없습니다";
    public static final String ERROR_BAD_REQUEST = "잘못된 요청입니다";
    
    // ===== 인증 관련 메시지 =====
    public static final String SUCCESS_LOGIN = "로그인이 성공했습니다";
    public static final String SUCCESS_LOGOUT = "로그아웃이 성공했습니다";
    public static final String SUCCESS_REGISTER = "회원가입이 성공했습니다";
    public static final String ERROR_LOGIN_FAILED = "로그인에 실패했습니다";
    public static final String ERROR_USER_NOT_FOUND = "사용자를 찾을 수 없습니다";
    public static final String ERROR_USERNAME_EXISTS = "이미 존재하는 사용자명입니다";
    public static final String ERROR_EMAIL_EXISTS = "이미 존재하는 이메일입니다";
    
    // ===== 가상 계좌 관련 메시지 =====
    public static final String SUCCESS_VIRTUAL_ACCOUNT_INIT = "가상 계좌가 생성/초기화되었습니다";
    public static final String SUCCESS_VIRTUAL_BUY = "매수 완료";
    public static final String SUCCESS_VIRTUAL_SELL = "매도 완료";
    
    // ===== 관심 종목 관련 메시지 =====
    public static final String SUCCESS_WATCHLIST_ADDED = "관심 종목에 추가되었습니다";
    public static final String SUCCESS_WATCHLIST_REMOVED = "관심 종목에서 제거되었습니다";
    
    // ===== 동적 메시지 템플릿 =====
    public static final String TEMPLATE_PROCESSED_ITEMS = "%d개 항목이 처리되었습니다";
    public static final String TEMPLATE_RETRIEVED_FOR_SYMBOL = "%s에 대한 데이터를 조회했습니다";
    public static final String TEMPLATE_NOT_FOUND_FOR_SYMBOL = "%s에 대한 데이터를 찾을 수 없습니다";
    public static final String TEMPLATE_BATCH_PROCESSED = "%d개 %s 항목이 처리되었습니다";
    public static final String TEMPLATE_RETRIEVED_COUNT = "%d개 항목을 조회했습니다";
    public static final String TEMPLATE_DATE_RANGE = "%s부터 %s까지의 데이터를 조회했습니다";
    
    // ===== 유틸리티 메서드 =====
    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
    
    // 생성자 방지
    private ResponseMessages() {
        throw new UnsupportedOperationException("Utility class");
    }
} 