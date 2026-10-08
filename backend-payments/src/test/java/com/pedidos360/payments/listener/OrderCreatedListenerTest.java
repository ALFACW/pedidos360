package com.pedidos360.payments.listener;

import com.pedidos360.payments.config.RabbitAckHandler;
import com.pedidos360.payments.service.PaymentProcessingService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderCreatedListenerTest {

    private final PaymentProcessingService service = mock(PaymentProcessingService.class);
    private final Channel channel = mock(Channel.class);
    private final OrderCreatedListener listener = new OrderCreatedListener(service, new RabbitAckHandler(3, 0));

    @Test
    void acksAfterProcessingOrder() throws Exception {
        listener.onMessage("{}", channel, 1L, "order-1");

        verify(channel).basicAck(1L, false);
    }

    @Test
    void nacksMalformedOrderToDeadLetterQueue() throws Exception {
        doThrow(new IllegalArgumentException("sin eventId")).when(service).processOrderCreated("{}");

        listener.onMessage("{}", channel, 2L, "order-2");

        verify(service, times(1)).processOrderCreated("{}");
        verify(channel).basicNack(2L, false, false);
    }

    @Test
    void retriesDatabaseFailuresThenDeadLetters() throws Exception {
        doThrow(new DataAccessResourceFailureException("BD caida")).when(service).processOrderCreated("{}");

        listener.onMessage("{}", channel, 3L, "order-3");

        verify(service, times(3)).processOrderCreated("{}");
        verify(channel).basicNack(3L, false, false);
    }
}
