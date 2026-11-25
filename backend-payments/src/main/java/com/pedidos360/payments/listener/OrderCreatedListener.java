package com.pedidos360.payments.listener;

import com.pedidos360.payments.config.RabbitAckHandler;
import com.pedidos360.payments.config.RabbitConfig;
import com.pedidos360.payments.service.PaymentProcessingService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Dominio pagos: consume order.created y order.cancelled. El ACK se envía después de confirmar la transacción. */
@Component
public class OrderCreatedListener {

    private final PaymentProcessingService paymentProcessingService;
    private final RabbitAckHandler ackHandler;

    public OrderCreatedListener(PaymentProcessingService paymentProcessingService, RabbitAckHandler ackHandler) {
        this.paymentProcessingService = paymentProcessingService;
        this.ackHandler = ackHandler;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_CREATED_QUEUE)
    public void onMessage(
            String payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        ackHandler.process(channel, deliveryTag, RabbitConfig.ORDER_CREATED_QUEUE, messageId,
                () -> paymentProcessingService.processOrderEvent(payload));
    }
}
