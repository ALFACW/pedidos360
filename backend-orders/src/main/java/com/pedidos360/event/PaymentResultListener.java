package com.pedidos360.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.config.RabbitConfig;
import com.pedidos360.service.OrderStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultListener {

    private final ObjectMapper objectMapper;
    private final OrderStore orderStore;

    public PaymentResultListener(ObjectMapper objectMapper, OrderStore orderStore) {
        this.objectMapper = objectMapper;
        this.orderStore = orderStore;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_RESULTS_QUEUE)
    public void handle(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String eventType = event.path("eventType").asText();
        if (!"PAYMENT_APPROVED".equals(eventType) && !"PAYMENT_REJECTED".equals(eventType)) {
            throw new IllegalArgumentException("Evento recibido no es un resultado de pago");
        }

        JsonNode payment = event.path("payment");
        long orderId = payment.path("orderId").asLong(-1);
        if (orderId < 0) {
            throw new IllegalArgumentException("El evento de pago no incluye orderId");
        }

        String paymentStatus = "PAYMENT_APPROVED".equals(eventType) ? "APPROVED" : "REJECTED";
        if (!orderStore.updatePayment(orderId, paymentStatus)) {
            throw new IllegalStateException("No existe el pedido " + orderId + " para actualizar su pago");
        }
    }
}