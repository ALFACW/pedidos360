package com.pedidos360.notifications.listener;

import com.pedidos360.notifications.config.RabbitAckHandler;
import com.pedidos360.notifications.service.PaymentNotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentResultListenerTest {

    private final PaymentNotificationService service = mock(PaymentNotificationService.class);
    private final Channel channel = mock(Channel.class);
    private final PaymentResultListener listener =
            new PaymentResultListener(service, new RabbitAckHandler(3, 0));

    @Test
    void acksAfterSuccessfulNotification() throws Exception {
        listener.onMessage("{}", channel, 1L, "payment-1");

        verify(channel).basicAck(1L, false);
    }

    @Test
    void nacksInvalidEventToDeadLetterQueueImmediately() throws Exception {
        doThrow(new IllegalArgumentException("sin correo")).when(service).handle("{}");

        listener.onMessage("{}", channel, 2L, "payment-2");

        verify(service, times(1)).handle("{}");
        verify(channel).basicNack(2L, false, false);
        verify(channel, never()).basicAck(2L, false);
    }

    @Test
    void retriesSmtpFailuresThenDeadLetters() throws Exception {
        doThrow(new MailSendException("SMTP caido")).when(service).handle("{}");

        listener.onMessage("{}", channel, 3L, "payment-3");

        verify(service, times(3)).handle("{}");
        verify(channel).basicNack(3L, false, false);
    }
}
