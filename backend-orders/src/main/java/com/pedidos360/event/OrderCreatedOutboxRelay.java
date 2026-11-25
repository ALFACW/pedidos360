package com.pedidos360.event;

import com.pedidos360.model.OrderOutboxEntity;
import com.pedidos360.service.OrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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
        List<OrderOutboxEntity> pendingEvents = orderStore.findPendingOrderEvents();
        for (OrderOutboxEntity event : pendingEvents) {
            try {
                orderEventPublisher.publish(event.getEventId(), event.getEventType(), event.getPayload());
                orderStore.markOrderEventPublished(event.getEventId());
            } catch (RuntimeException exception) {
                logger.warn("Event {} ({}) remains pending: {}",
                        event.getEventId(), event.getEventType(), exception.getMessage());
            }
        }
    }
}