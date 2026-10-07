package com.pedidos360.controller;

import com.pedidos360.service.OrderStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerNotificationTest {

    private final OrderStore orderStore = mock(OrderStore.class);
    private final OrderController controller = new OrderController(orderStore);

    @Test
    void storesAuthenticatedEmailWithOrderForNotificationEvent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("name", "Test User")
                .claim("preferred_username", "test@example.com")
                .build();
        when(orderStore.saveAndQueueOrderEvent(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createOrder(new HashMap<>(Map.of("total", 25.0)), jwt);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(orderStore).saveAndQueueOrderEvent(org.mockito.ArgumentMatchers.argThat(
                order -> "test@example.com".equals(order.get("customerEmail"))));
    }

    @Test
    void rejectsOrderWhenAccessTokenHasNoEmailClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .build();

        var response = controller.createOrder(Map.of("total", 25.0), jwt);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(orderStore, never()).saveAndQueueOrderEvent(any());
    }
}
