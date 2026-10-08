package com.pedidos360.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.config.RabbitConfig;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishOrderCreated(Map<String, Object> order) {
        try {
            String eventId = "order-" + order.get("id");
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", "ORDER_CREATED",
                    "order", order,
                    "timestamp", System.currentTimeMillis()));
            CorrelationData correlationData = new CorrelationData(eventId);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.EVENTS_EXCHANGE,
                    RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                    payload,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(eventId);
                        return message;
                    },
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ no confirmó el evento de pedido: " + confirm.getReason());
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo publicar el evento de orden", e);
        }
    }
}
