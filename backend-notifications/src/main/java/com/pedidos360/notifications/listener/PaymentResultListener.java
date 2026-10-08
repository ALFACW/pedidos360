package com.pedidos360.notifications.listener;

import com.pedidos360.notifications.config.RabbitAckHandler;
import com.pedidos360.notifications.config.RabbitConfig;
import com.pedidos360.notifications.service.PaymentNotificationService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Dominio notificaciones: consume payment.approved / payment.rejected. ACK tras confirmar la transacción. */
@Component
public class PaymentResultListener {

    private final PaymentNotificationService notificationService;
    private final RabbitAckHandler ackHandler;

    public PaymentResultListener(PaymentNotificationService notificationService, RabbitAckHandler ackHandler) {
        this.notificationService = notificationService;
        this.ackHandler = ackHandler;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_RESULTS_QUEUE)
    public void onMessage(
            String payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        ackHandler.process(channel, deliveryTag, RabbitConfig.PAYMENT_RESULTS_QUEUE, messageId,
                () -> notificationService.handle(payload));
    }
}
