package com.stocker_back.stocker_back.controller;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
public class DevController {
    
    private final UserRepository userRepository;
    
    /**
     * 개발용: 사용자에게 관리자 권한 부여
     */
    @PostMapping("/make-admin/{username}")
    public ResponseEntity<?> makeUserAdmin(@PathVariable String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "User not found: " + username
            ));
        }
        
        User user = userOptional.get();
        user.setRole(User.Role.ADMIN);
        userRepository.save(user);
        
        log.info("User {} has been granted ADMIN role", username);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", String.format("User %s is now an ADMIN", username),
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().name()
            )
        ));
    }
} 