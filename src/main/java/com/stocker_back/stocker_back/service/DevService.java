package com.stocker_back.stocker_back.service;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevService {
    
    private final UserRepository userRepository;
    
    /**
     * 사용자에게 관리자 권한 부여
     * @param username 권한을 부여할 사용자명
     * @return 권한이 변경된 사용자 정보
     * @throws IllegalArgumentException 사용자를 찾을 수 없는 경우
     */
    public Map<String, Object> makeUserAdmin(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        
        user.setRole(User.Role.ADMIN);
        userRepository.save(user);
        
        log.info("User {} has been granted ADMIN role", username);
        
        return Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole().name()
        );
    }
} 