package com.pedidos360.event;

import com.pedidos360.config.RabbitAckHandler;
import com.pedidos360.service.OrderStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentResultListenerTest {

    private final OrderStore orderStore = mock(OrderStore.class);
    private final Channel channel = mock(Channel.class);
    private final PaymentResultListener listener =
            new PaymentResultListener(new ObjectMapper(), orderStore, new RabbitAckHandler(3, 0));

    @Test
    void appliesPaymentResultIdempotently() throws Exception {
        when(orderStore.updatePayment(42L, "APPROVED")).thenReturn(true);
        String approvedEvent = """
                {"eventType":"PAYMENT_APPROVED","payment":{"orderId":"42"}}
                """;

        listener.handle(approvedEvent);
        listener.handle(approvedEvent);

        verify(orderStore, times(2)).updatePayment(42L, "APPROVED");
    }

    @Test
    void rejectsUnknownOrderSoMessageCanBeDeadLettered() throws Exception {
        when(orderStore.updatePayment(404L, "REJECTED")).thenReturn(false);

        assertThatThrownBy(() -> listener.handle(
                "{\"eventType\":\"PAYMENT_REJECTED\",\"payment\":{\"orderId\":\"404\"}}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acksProcessedMessage() throws Exception {
        when(orderStore.updatePayment(42L, "APPROVED")).thenReturn(true);

        listener.onMessage("{\"eventType\":\"PAYMENT_APPROVED\",\"payment\":{\"orderId\":\"42\"}}",
                channel, 7L, "payment-42-approved");

        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, false);
    }

    @Test
    void nacksInvalidMessageToDeadLetterQueueWithoutRetrying() throws Exception {
        listener.onMessage("no es json", channel, 8L, null);

        verify(channel).basicNack(8L, false, false);
        verify(channel, never()).basicAck(8L, false);
    }

    @Test
    void retriesRecoverableErrorsThenDeadLetters() throws Exception {
        when(orderStore.updatePayment(404L, "REJECTED")).thenReturn(false);

        listener.onMessage("{\"eventType\":\"PAYMENT_REJECTED\",\"payment\":{\"orderId\":\"404\"}}",
                channel, 9L, "payment-404-rejected");

        verify(orderStore, times(3)).updatePayment(404L, "REJECTED");
        verify(channel).basicNack(9L, false, false);
    }
}
