package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.dto.UserRegistrationDto;
import com.stocker_back.stocker_back.dto.UserLoginDto;
import com.stocker_back.stocker_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * User registration
     */
    public User registerUser(UserRegistrationDto registrationDto) {
        log.info("User registration attempt: username={}, email={}", registrationDto.getUsername(), registrationDto.getEmail());
        
        // Input validation
        validateRegistrationDto(registrationDto);
        
        // Check username duplication
        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new IllegalArgumentException("Username is already taken: " + registrationDto.getUsername());
        }
        
        // Check email duplication
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new IllegalArgumentException("Email is already in use: " + registrationDto.getEmail());
        }
        
        // Password confirmation check
        if (!registrationDto.getPassword().equals(registrationDto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        
        // Create new user
        User user = new User(
            registrationDto.getUsername(),
            registrationDto.getEmail(),
            passwordEncoder.encode(registrationDto.getPassword()),
            registrationDto.getFullName()
        );
        
        User savedUser = userRepository.save(user);
        
        log.info("User registration completed: id={}, username={}", savedUser.getId(), savedUser.getUsername());
        
        return savedUser;
    }
    
    /**
     * User login
     */
    @Transactional
    public User loginUser(UserLoginDto loginDto) {
        log.info("User login attempt: usernameOrEmail={}", loginDto.getUsernameOrEmail());
        
        // Input validation
        validateLoginDto(loginDto);
        
        // Find user by username or email
        User user = findUserByUsernameOrEmail(loginDto.getUsernameOrEmail());
        
        // Check password
        if (!passwordEncoder.matches(loginDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }
        
        // Check if account is enabled
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is disabled");
        }
        
        // Check if account is not locked
        if (!user.isAccountNonLocked()) {
            throw new IllegalArgumentException("Account is locked");
        }
        
        log.info("User login successful: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }
    
    /**
     * Find user by username or email
     */
    @Transactional(readOnly = true)
    public User findUserByUsernameOrEmail(String usernameOrEmail) {
        // Try to find by username first
        User user = userRepository.findByUsername(usernameOrEmail).orElse(null);
        if (user != null) {
            return user;
        }
        
        // If not found by username, try by email
        user = userRepository.findByEmail(usernameOrEmail).orElse(null);
        if (user != null) {
            return user;
        }
        
        throw new IllegalArgumentException("User not found with username or email: " + usernameOrEmail);
    }
    
    /**
     * Find user by username
     */
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
    
    /**
     * Find user by email
     */
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }
    
    /**
     * Check username existence
     */
    @Transactional(readOnly = true)
    public boolean isUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }
    
    /**
     * Check email existence
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }
    
    /**
     * Registration input validation
     */
    private void validateRegistrationDto(UserRegistrationDto dto) {
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (dto.getFullName() == null || dto.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (dto.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }
    
    /**
     * Login input validation
     */
    private void validateLoginDto(UserLoginDto dto) {
        if (dto.getUsernameOrEmail() == null || dto.getUsernameOrEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Username or email is required");
        }
        if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
} 