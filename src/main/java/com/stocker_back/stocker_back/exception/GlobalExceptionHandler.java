package com.stocker_back.stocker_back.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle parameter type conversion errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        String paramName = ex.getName();
        String paramType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String providedValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        
        errorResponse.put("success", false);
        errorResponse.put("error", String.format(
            "Parameter '%s' must be a valid %s, received: '%s'", 
            paramName, paramType.toLowerCase(), providedValue));
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
} 