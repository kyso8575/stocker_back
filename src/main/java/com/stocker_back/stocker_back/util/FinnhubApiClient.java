package com.stocker_back.stocker_back.util;

import com.stocker_back.stocker_back.config.FinnhubApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Finnhub API와의 통신을 담당하는 유틸리티 클래스
 * Rate limit 처리 및 재시도 로직 포함 (60 requests/minute 제한)
 */
@Slf4j
@Component
public class FinnhubApiClient {

    private final RestTemplate restTemplate;
    private final FinnhubApiConfig finnhubApiConfig;
    
    // Rate limit 처리 설정
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 1000; // 1초
    private static final long MAX_DELAY_MS = 30000; // 30초
    
    // Rate limiting: 60 requests/minute = 1000ms between requests
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // 1000ms = 60 requests/minute
    private volatile long lastRequestTime = 0;
    private final Object rateLimitLock = new Object();
    
    public FinnhubApiClient(
        @Qualifier("customRestTemplate") RestTemplate restTemplate,
        FinnhubApiConfig finnhubApiConfig) {
        this.restTemplate = restTemplate;
        this.finnhubApiConfig = finnhubApiConfig;
    }
    
    /**
     * API 호출 결과를 나타내는 클래스
     */
    public static class ApiResult<T> {
        private final T data;
        private final ApiStatus status;
        private final String message;
        
        private ApiResult(T data, ApiStatus status, String message) {
            this.data = data;
            this.status = status;
            this.message = message;
        }
        
        public static <T> ApiResult<T> success(T data) {
            return new ApiResult<>(data, ApiStatus.SUCCESS, "Success");
        }
        
        public static <T> ApiResult<T> noData() {
            return new ApiResult<>(null, ApiStatus.NO_DATA, "No data available");
        }
        
        public static <T> ApiResult<T> rateLimitExceeded() {
            return new ApiResult<>(null, ApiStatus.RATE_LIMIT_EXCEEDED, "Rate limit exceeded after all retries");
        }
        
        public static <T> ApiResult<T> error(String message) {
            return new ApiResult<>(null, ApiStatus.ERROR, message);
        }
        
        public T getData() { return data; }
        public ApiStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return status == ApiStatus.SUCCESS; }
        public boolean isNoData() { return status == ApiStatus.NO_DATA; }
        public boolean isRateLimitExceeded() { return status == ApiStatus.RATE_LIMIT_EXCEEDED; }
        public boolean isError() { return status == ApiStatus.ERROR; }
    }
    
    /**
     * API 호출 상태를 나타내는 열거형
     */
    public enum ApiStatus {
        SUCCESS,
        NO_DATA,
        RATE_LIMIT_EXCEEDED,
        ERROR
    }
    
    /**
     * Finnhub API에 GET 요청을 보내고 상세한 결과를 반환합니다.
     * Rate limit 발생 시 자동으로 재시도합니다.
     * 60 requests/minute로 요청 빈도를 제한합니다.
     * 
     * @param <T> 응답 본문의 타입
     * @param path API 경로
     * @param responseType 응답 타입 클래스
     * @param queryParams 쿼리 파라미터 배열 (키1, 값1, 키2, 값2, ...)
     * @return API 결과 (성공/실패/rate limit 등 구분)
     */
    public <T> ApiResult<T> getWithResult(String path, Class<T> responseType, Object... queryParams) {
        return executeWithRetryAndResult(() -> {
            // Rate limit 체크 및 대기
            enforceRateLimit();
            
            UriComponentsBuilder builder = createBaseUriBuilder()
                    .path(path);
            
            // 쿼리 파라미터 추가
            for (int i = 0; i < queryParams.length; i += 2) {
                if (i + 1 < queryParams.length) {
                    builder.queryParam(queryParams[i].toString(), queryParams[i + 1]);
                }
            }
            
            // API 토큰 추가
            builder.queryParam("token", finnhubApiConfig.getApiKey());
            
            String url = builder.toUriString();
            log.debug("Calling Finnhub API: {}", url);
            
            ResponseEntity<T> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    responseType
            );
            
            T data = response.getBody();
            
            // 데이터가 null이거나 비어있으면 NO_DATA로 처리
            if (data == null || isEmptyData(data)) {
                return ApiResult.noData();
            }
            
            return ApiResult.success(data);
        }, path);
    }
    
    /**
     * 기존 호환성을 위한 메서드 (null 반환)
     */
    public <T> T get(String path, Class<T> responseType, Object... queryParams) {
        ApiResult<T> result = getWithResult(path, responseType, queryParams);
        return result.isSuccess() ? result.getData() : null;
    }
    
    /**
     * 기존 호환성을 위한 메서드 (null 반환)
     */
    public <T> T get(String path, ParameterizedTypeReference<T> responseType, Object... queryParams) {
        return executeWithRetry(() -> {
            // Rate limit 체크 및 대기
            enforceRateLimit();
            
            UriComponentsBuilder builder = createBaseUriBuilder()
                    .path(path);
            
            // 쿼리 파라미터 추가
            for (int i = 0; i < queryParams.length; i += 2) {
                if (i + 1 < queryParams.length) {
                    builder.queryParam(queryParams[i].toString(), queryParams[i + 1]);
                }
            }
            
            // API 토큰 추가
            builder.queryParam("token", finnhubApiConfig.getApiKey());
            
            String url = builder.toUriString();
            log.debug("Calling Finnhub API: {}", url);
            
            ResponseEntity<T> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    responseType
            );
            
            return response.getBody();
        }, path);
    }
    
    /**
     * 데이터가 비어있는지 확인하는 메서드
     */
    private boolean isEmptyData(Object data) {
        if (data == null) return true;
        
        // String인 경우 빈 문자열이나 "{}" 체크
        if (data instanceof String) {
            String str = (String) data;
            return str.isEmpty() || "{}".equals(str.trim()) || "null".equals(str.trim());
        }
        
        // Map이나 Collection인 경우
        if (data instanceof java.util.Collection) {
            return ((java.util.Collection<?>) data).isEmpty();
        }
        if (data instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) data).isEmpty();
        }
        
        return false;
    }
    
    /**
     * 60 requests/minute rate limit을 강제 적용합니다.
     * 마지막 요청으로부터 최소 1000ms 간격을 보장합니다.
     */
    private void enforceRateLimit() {
        synchronized (rateLimitLock) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastRequest = currentTime - lastRequestTime;
            
            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                long waitTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest;
                
                try {
                    log.trace("Rate limiting: waiting {}ms to maintain 60 requests/minute", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted during rate limiting wait");
                }
            }
            
            lastRequestTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 상세한 결과를 반환하는 재시도 로직
     */
    private <T> ApiResult<T> executeWithRetryAndResult(ApiCallSupplier<ApiResult<T>> apiCall, String path) {
        int attempt = 0;
        long delay = BASE_DELAY_MS;
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                return apiCall.call();
            } catch (HttpClientErrorException e) {
                attempt++;
                
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        log.warn("Rate limit exceeded for path {} (attempt {}/{}). Retrying in {}ms...", 
                               path, attempt, MAX_RETRY_ATTEMPTS, delay);
                        
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Thread interrupted during rate limit backoff");
                            return ApiResult.error("Interrupted during rate limit backoff");
                        }
                        
                        // Exponential backoff: 1초 -> 2초 -> 4초 (최대 30초)
                        delay = Math.min(delay * 2, MAX_DELAY_MS);
                    } else {
                        log.error("Rate limit exceeded for path {}. All {} retry attempts failed.", 
                                path, MAX_RETRY_ATTEMPTS);
                        return ApiResult.rateLimitExceeded();
                    }
                } else {
                    // Rate limit이 아닌 다른 HTTP 에러는 재시도하지 않음
                    log.error("HTTP error from Finnhub API path {}: {} - {}", 
                             path, e.getStatusCode(), e.getResponseBodyAsString());
                    return ApiResult.error("HTTP error: " + e.getStatusCode());
                }
            } catch (Exception e) {
                // 네트워크 에러 등은 재시도하지 않음
                log.error("Error making request to Finnhub API path {}: {}", path, e.getMessage(), e);
                return ApiResult.error("Network error: " + e.getMessage());
            }
        }
        
        return ApiResult.error("Max retry attempts exceeded");
    }
    
    /**
     * 재시도 로직을 포함한 API 실행 (기존 호환성용)
     */
    private <T> T executeWithRetry(ApiCallSupplier<T> apiCall, String path) {
        int attempt = 0;
        long delay = BASE_DELAY_MS;
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                return apiCall.call();
            } catch (HttpClientErrorException e) {
                attempt++;
                
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        log.warn("Rate limit exceeded for path {} (attempt {}/{}). Retrying in {}ms...", 
                               path, attempt, MAX_RETRY_ATTEMPTS, delay);
                        
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.error("Thread interrupted during rate limit backoff");
                            return null;
                        }
                        
                        // Exponential backoff: 1초 -> 2초 -> 4초 (최대 30초)
                        delay = Math.min(delay * 2, MAX_DELAY_MS);
                    } else {
                        log.error("Rate limit exceeded for path {}. All {} retry attempts failed.", 
                                path, MAX_RETRY_ATTEMPTS);
                        return null;
                    }
                } else {
                    // Rate limit이 아닌 다른 HTTP 에러는 재시도하지 않음
                    log.error("HTTP error from Finnhub API path {}: {} - {}", 
                             path, e.getStatusCode(), e.getResponseBodyAsString());
                    return null;
                }
            } catch (Exception e) {
                // 네트워크 에러 등은 재시도하지 않음
                log.error("Error making request to Finnhub API path {}: {}", path, e.getMessage(), e);
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * API 호출을 위한 함수형 인터페이스
     */
    @FunctionalInterface
    private interface ApiCallSupplier<T> {
        T call() throws Exception;
    }
    
    /**
     * Finnhub API 기본 URI 빌더 생성
     */
    private UriComponentsBuilder createBaseUriBuilder() {
        return UriComponentsBuilder.newInstance()
                .scheme("https")
                .host("finnhub.io")
                .path("/api/v1");
    }
    
    /**
     * 현재 설정된 rate limit 정보를 반환합니다.
     * @return rate limit 정보 문자열
     */
    public String getRateLimitInfo() {
        return String.format("Rate limit: 60 requests/minute (min interval: %dms)", MIN_REQUEST_INTERVAL_MS);
    }
} 