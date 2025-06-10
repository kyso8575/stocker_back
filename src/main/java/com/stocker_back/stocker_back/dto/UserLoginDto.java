package com.stocker_back.stocker_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class UserLoginDto {
    
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    public UserLoginDto(String usernameOrEmail, String password) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }
} 