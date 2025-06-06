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
 */
@Slf4j
@Component
public class FinnhubApiClient {

    private final RestTemplate restTemplate;
    private final FinnhubApiConfig finnhubApiConfig;
    
    public FinnhubApiClient(
        @Qualifier("customRestTemplate") RestTemplate restTemplate,
        FinnhubApiConfig finnhubApiConfig) {
        this.restTemplate = restTemplate;
        this.finnhubApiConfig = finnhubApiConfig;
    }
    
    /**
     * Finnhub API에 GET 요청을 보내고 응답을 반환합니다.
     * 
     * @param <T> 응답 본문의 타입
     * @param path API 경로
     * @param responseType 응답 타입 클래스
     * @param queryParams 쿼리 파라미터 배열 (키1, 값1, 키2, 값2, ...)
     * @return API 응답 또는 에러 발생시 null
     */
    public <T> T get(String path, Class<T> responseType, Object... queryParams) {
        try {
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
        } catch (HttpClientErrorException e) {
            handleHttpError(path, e);
            return null;
        } catch (Exception e) {
            log.error("Error making request to Finnhub API path {}: {}", path, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Finnhub API에 GET 요청을 보내고 제네릭 타입의 응답을 반환합니다.
     * 
     * @param <T> 응답 본문의 타입
     * @param path API 경로
     * @param responseType 응답 타입 레퍼런스
     * @param queryParams 쿼리 파라미터 배열 (키1, 값1, 키2, 값2, ...)
     * @return API 응답 또는 에러 발생시 null
     */
    public <T> T get(String path, ParameterizedTypeReference<T> responseType, Object... queryParams) {
        try {
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
        } catch (HttpClientErrorException e) {
            handleHttpError(path, e);
            return null;
        } catch (Exception e) {
            log.error("Error making request to Finnhub API path {}: {}", path, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * HTTP 에러 처리
     */
    private void handleHttpError(String path, HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Rate limit exceeded for Finnhub API path {}", path);
        } else {
            log.error("HTTP error from Finnhub API path {}: {} - {}", 
                     path, e.getStatusCode(), e.getResponseBodyAsString(), e);
        }
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
} 