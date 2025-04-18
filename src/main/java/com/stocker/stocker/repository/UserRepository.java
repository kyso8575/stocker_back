package com.stocker.stocker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stocker.stocker.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 사용자명으로 사용자 정보 조회
     * @param username 사용자명
     * @return 사용자 정보
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 이메일로 사용자 정보 조회
     * @param email 이메일
     * @return 사용자 정보
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 사용자명이 존재하는지 확인
     * @param username 사용자명
     * @return 존재 여부
     */
    boolean existsByUsername(String username);
    
    /**
     * 이메일이 존재하는지 확인
     * @param email 이메일
     * @return 존재 여부
     */
    boolean existsByEmail(String email);
} 