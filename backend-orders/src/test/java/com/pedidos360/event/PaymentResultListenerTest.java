package com.pedidos360.event;

import com.pedidos360.service.OrderStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentResultListenerTest {

    @Test
    void appliesPaymentResultIdempotently() throws Exception {
        OrderStore orderStore = mock(OrderStore.class);
        when(orderStore.updatePayment(42L, "APPROVED")).thenReturn(true);
        PaymentResultListener listener = new PaymentResultListener(new ObjectMapper(), orderStore);
        String approvedEvent = """
                {"eventType":"PAYMENT_APPROVED","payment":{"orderId":"42"}}
                """;

        listener.handle(approvedEvent);
        listener.handle(approvedEvent);

        verify(orderStore, times(2)).updatePayment(42L, "APPROVED");
    }

    @Test
    void rejectsUnknownOrderSoMessageCanBeDeadLettered() throws Exception {
        OrderStore orderStore = mock(OrderStore.class);
        when(orderStore.updatePayment(404L, "REJECTED")).thenReturn(false);
        PaymentResultListener listener = new PaymentResultListener(new ObjectMapper(), orderStore);

        assertThatThrownBy(() -> listener.handle(
                "{\"eventType\":\"PAYMENT_REJECTED\",\"payment\":{\"orderId\":\"404\"}}"))
                .isInstanceOf(IllegalStateException.class);
    }

}