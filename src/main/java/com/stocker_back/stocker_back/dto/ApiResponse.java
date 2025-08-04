package com.stocker_back.stocker_back.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Map<String, String> errors;

    public ApiResponse(boolean success, String message, T data, Map<String, String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    // 기본 생성자 (하위 호환성)
    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, null);
    }

    // 성공 응답 (데이터만)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "요청이 성공적으로 처리되었습니다", data, null);
    }

    // 성공 응답 (메시지 + 데이터)
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    // 단순 성공 응답 (메시지만)
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    // 에러 응답 (메시지만)
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    // 에러 응답 (메시지 + 검증 에러)
    public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

    // 하위 호환성을 위한 getter들
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
} 