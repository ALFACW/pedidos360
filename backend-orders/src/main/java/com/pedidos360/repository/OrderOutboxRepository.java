package com.pedidos360.repository;

import com.pedidos360.model.OrderOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, String> {
    List<OrderOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    Optional<OrderOutboxEntity> findByOrderIdAndPublishedAtIsNull(Integer orderId);
}