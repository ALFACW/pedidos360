package com.pedidos360.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.event.CompraPublisher;
import com.pedidos360.model.OrderEntity;
import com.pedidos360.repository.OrderOutboxRepository;
import com.pedidos360.repository.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderStoreStockTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CompraPublisher compras = mock(CompraPublisher.class);
    private final OrderStore store = new OrderStore(orders, mock(OrderOutboxRepository.class), new ObjectMapper(), jdbc, compras);

    private OrderEntity pending() {
        OrderEntity o = new OrderEntity();
        o.setItems(List.of(Map.of("id", 3, "qty", 2)));
        o.setPaymentStatus("PENDING");
        when(orders.findById(1)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void approvedPaymentDecrementsStockOnce() {
        pending();
        store.updatePayment(1L, "APPROVED");
        store.updatePayment(1L, "APPROVED");
        verify(jdbc, times(1)).update(anyString(), any(Object.class), any(Object.class));
        verify(compras, times(1)).publicar(any(), eq(3L), anyString(), any());
    }

    @Test
    void rejectedPaymentKeepsStock() {
        pending();
        store.updatePayment(1L, "REJECTED");
        verify(jdbc, never()).update(anyString(), any(Object.class), any(Object.class));
        verify(compras, never()).publicar(any(), anyLong(), any(), any());
    }
}
