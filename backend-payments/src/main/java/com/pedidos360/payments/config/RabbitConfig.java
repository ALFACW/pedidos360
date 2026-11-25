package com.pedidos360.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensajería de ms-pagos.
 *
 * <pre>
 * Consume:  pedidos360.events (topic) --order.created | order.cancelled--> pedidos360.payments.order-created.queue
 * Publica:  pedidos360.events (topic) --payment.approved | payment.rejected--> (ms-pedidos, ms-notificaciones)
 * DLQ:      pedidos360.dlx (direct) --payments.order-created.dead--> pedidos360.payments.order-created.dead
 * </pre>
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "pedidos360.events";
    public static final String DEAD_LETTER_EXCHANGE = "pedidos360.dlx";
    public static final String ORDER_CREATED_QUEUE = "pedidos360.payments.order-created.queue";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";
    public static final String ORDER_CREATED_DEAD_QUEUE = "pedidos360.payments.order-created.dead";
    public static final String ORDER_CREATED_DEAD_ROUTING_KEY = "payments.order-created.dead";
    public static final String PAYMENT_APPROVED_ROUTING_KEY = "payment.approved";
    public static final String PAYMENT_REJECTED_ROUTING_KEY = "payment.rejected";

    // Nombre visible en la consola de RabbitMQ (pestaña Connections) en lugar de solo IP:puerto
    @Bean
    public ConnectionNameStrategy connectionNameStrategy(
            @Value("${app.rabbitmq.connection-name}") String connectionName) {
        return connectionFactory -> connectionName;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CREATED_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderCreatedDeadQueue() {
        return QueueBuilder.durable(ORDER_CREATED_DEAD_QUEUE).build();
    }

    @Bean
    public Binding orderCreatedBinding(
            @Qualifier("orderCreatedQueue") Queue orderCreatedQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(eventsExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelledBinding(
            @Qualifier("orderCreatedQueue") Queue orderCreatedQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(eventsExchange).with(ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCreatedDeadBinding(
            @Qualifier("orderCreatedDeadQueue") Queue orderCreatedDeadQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderCreatedDeadQueue)
                .to(deadLetterExchange)
                .with(ORDER_CREATED_DEAD_ROUTING_KEY);
    }
}
