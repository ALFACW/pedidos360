package com.pedidos360.users.repository;

import com.pedidos360.users.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    boolean existsByEmail(String email);
}