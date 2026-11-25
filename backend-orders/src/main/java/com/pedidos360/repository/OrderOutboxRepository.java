package com.pedidos360.repository;

import com.pedidos360.model.OrderOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, String> {
    List<OrderOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}