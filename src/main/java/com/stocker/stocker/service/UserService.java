package com.stocker.stocker.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.stocker.stocker.domain.Role;
import com.stocker.stocker.domain.User;
import com.stocker.stocker.dto.SignupRequest;
import com.stocker.stocker.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class UserService implements UserDetailsService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * 사용자명으로 사용자 정보 조회
     * @param username 사용자명
     * @return UserDetails 구현체
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("사용자 인증 시도: {}", username);
        try {
            UserDetails user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
            logger.info("사용자 인증 성공: {}", username);
            return user;
        } catch (UsernameNotFoundException e) {
            logger.warn("사용자 인증 실패: {}", username);
            throw e;
        }
    }
    
    /**
     * 회원가입
     * @param signupRequest 회원가입 정보
     * @return 생성된 사용자 정보
     */
    @Transactional
    public User registerUser(SignupRequest signupRequest) {
        logger.info("회원가입 요청: {}", signupRequest.getUsername());
        
        // 사용자명과 이메일 중복 확인
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            logger.warn("회원가입 실패: 이미 사용 중인 사용자명 - {}", signupRequest.getUsername());
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다: " + signupRequest.getUsername());
        }
        
        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            logger.warn("회원가입 실패: 이미 사용 중인 이메일 - {}", signupRequest.getEmail());
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + signupRequest.getEmail());
        }
        
        // 새 사용자 생성
        User newUser = User.builder()
                .username(signupRequest.getUsername())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        
        User savedUser = userRepository.save(newUser);
        logger.info("회원가입 성공: {}", savedUser.getUsername());
        return savedUser;
    }
    
    /**
     * 마지막 로그인 시간 업데이트
     * @param username 사용자명
     */
    @Transactional
    public void updateLastLogin(String username) {
        logger.info("마지막 로그인 시간 업데이트: {}", username);
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
            
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            logger.info("로그인 시간 업데이트 성공: {}", username);
        } catch (UsernameNotFoundException e) {
            logger.error("로그인 시간 업데이트 실패: {}", username);
            throw e;
        }
    }
    
    /**
     * 사용자 정보 조회
     * @param username 사용자명
     * @return 사용자 정보
     */
    public User getUser(String username) {
        logger.info("사용자 정보 조회: {}", username);
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
            logger.info("사용자 정보 조회 성공: {}", username);
            return user;
        } catch (UsernameNotFoundException e) {
            logger.warn("사용자 정보 조회 실패: {}", username);
            throw e;
        }
    }
} 