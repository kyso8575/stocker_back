package com.stocker.stocker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원가입 요청을 위한 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    
    private String username;
    private String email;
    private String password;
} 