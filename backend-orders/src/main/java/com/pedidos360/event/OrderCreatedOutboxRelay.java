package com.pedidos360.event;

import com.pedidos360.service.OrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderCreatedOutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreatedOutboxRelay.class);

    private final OrderStore orderStore;
    private final OrderEventPublisher orderEventPublisher;

    public OrderCreatedOutboxRelay(OrderStore orderStore, OrderEventPublisher orderEventPublisher) {
        this.orderStore = orderStore;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void publishPendingEvents() {
        List<Map<String, Object>> pendingEvents = orderStore.findPendingOrderEvents();
        for (Map<String, Object> order : pendingEvents) {
            long orderId = ((Number) order.get("id")).longValue();
            try {
                orderEventPublisher.publishOrderCreated(order);
                orderStore.markOrderEventPublished(orderId);
            } catch (RuntimeException exception) {
                logger.warn("Order-created event for order {} remains pending: {}",
                        orderId, exception.getMessage());
            }
        }
    }
}