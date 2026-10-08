package com.pedidos360.payments.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.config.RabbitConfig;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishResult(Map<String, Object> payment) {
        String eventType = "APPROVED".equals(payment.get("status"))
                ? "PAYMENT_APPROVED"
                : "PAYMENT_REJECTED";
        String routingKey = "APPROVED".equals(payment.get("status"))
                ? RabbitConfig.PAYMENT_APPROVED_ROUTING_KEY
                : RabbitConfig.PAYMENT_REJECTED_ROUTING_KEY;
        String eventId = payment.get("eventId").toString();

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", eventType,
                    "timestamp", System.currentTimeMillis(),
                    "payment", payment));
            CorrelationData correlationData = new CorrelationData(eventId);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE,
                    routingKey,
                    payload,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(eventId);
                        return message;
                    },
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ no confirmó el evento: " + confirm.getReason());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo publicar el resultado del pago", exception);
        }
    }
}