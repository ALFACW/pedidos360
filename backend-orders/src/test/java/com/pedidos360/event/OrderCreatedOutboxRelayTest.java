package com.pedidos360.event;

import com.pedidos360.service.OrderStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class OrderCreatedOutboxRelayTest {

    @Test
    void retriesPendingOrderEventAfterPublisherRecovers() {
        OrderStore orderStore = mock(OrderStore.class);
        Map<String, Object> order = Map.of("id", 42L, "orderNumber", "PED-42", "total", 19.99);
        when(orderStore.findPendingOrderEvents()).thenReturn(List.of(order), List.of(order));
        OrderEventPublisher publisher = mock(OrderEventPublisher.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .doNothing()
                .when(publisher).publishOrderCreated(anyMap());
        OrderCreatedOutboxRelay relay = new OrderCreatedOutboxRelay(orderStore, publisher);

        relay.publishPendingEvents();
        verify(orderStore, never()).markOrderEventPublished(42L);

        relay.publishPendingEvents();
        verify(orderStore).markOrderEventPublished(42L);
    }
}