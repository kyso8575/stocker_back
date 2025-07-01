package com.stocker_back.stocker_back.repository;

import com.stocker_back.stocker_back.domain.User;
import com.stocker_back.stocker_back.domain.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {
    Optional<VirtualAccount> findByUser(User user);
} 