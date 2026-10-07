package com.pedidos360.payments.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.event.PaymentEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;

class OrderCreatedListenerTest {

    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OrderCreatedListener listener =
            new OrderCreatedListener(new ObjectMapper(), publisher, jdbcTemplate);

    @Test
    void approvesPositiveOrderTotal() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        listener.handle("""
                {"eventId":"order-42","eventType":"ORDER_CREATED","order":{"id":42,"orderNumber":"PED-42","user":"Test User","customerEmail":"test@example.com","total":19.99}}
                """);

        verify(publisher).publishResult(argThat(
                payment -> "APPROVED".equals(payment.get("status"))
                        && "42".equals(payment.get("orderId"))
                        && "test@example.com".equals(payment.get("customerEmail"))));
    }

    @Test
    void rejectsZeroOrderTotal() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        listener.handle("""
                {"eventId":"order-43","eventType":"ORDER_CREATED","order":{"id":43,"total":0}}
                """);

        verify(publisher).publishResult(argThat(
                payment -> "REJECTED".equals(payment.get("status"))));
    }

    @Test
    void ignoresPreviouslyProcessedOrderEvent() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        listener.handle("""
                {"eventId":"order-42","eventType":"ORDER_CREATED","order":{"id":42,"total":19.99}}
                """);

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsUnexpectedEventForRetryAndDeadLetterHandling() {
        assertThrows(IllegalArgumentException.class,
                () -> listener.handle("{\"eventType\":\"UNKNOWN\"}"));
        verifyNoInteractions(publisher, jdbcTemplate);
    }
}