package com.pedidos360.payments.repository;

import com.pedidos360.payments.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByToken(String token);
}
