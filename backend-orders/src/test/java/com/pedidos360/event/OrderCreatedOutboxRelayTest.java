package com.pedidos360.event;

import com.pedidos360.model.OrderOutboxEntity;
import com.pedidos360.service.OrderStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedOutboxRelayTest {

    @Test
    void retriesPendingOrderEventAfterPublisherRecovers() {
        OrderOutboxEntity event = new OrderOutboxEntity();
        event.setEventId("order-42");
        event.setEventType("ORDER_CREATED");
        event.setPayload(Map.of("id", 42L, "orderNumber", "PED-42", "total", 19.99));
        OrderStore orderStore = mock(OrderStore.class);
        when(orderStore.findPendingOrderEvents()).thenReturn(List.of(event), List.of(event));
        OrderEventPublisher publisher = mock(OrderEventPublisher.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .doNothing()
                .when(publisher).publish(eq("order-42"), eq("ORDER_CREATED"), anyMap());
        OrderCreatedOutboxRelay relay = new OrderCreatedOutboxRelay(orderStore, publisher);

        relay.publishPendingEvents();
        verify(orderStore, never()).markOrderEventPublished("order-42");

        relay.publishPendingEvents();
        verify(orderStore).markOrderEventPublished("order-42");
    }
}
