package com.pedidos360.repository;

import com.pedidos360.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {

    List<OrderEntity> findByOwnerId(String ownerId);
}