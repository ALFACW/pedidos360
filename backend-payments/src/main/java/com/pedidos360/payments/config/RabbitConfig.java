package com.pedidos360.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Nombre visible en la consola de RabbitMQ (pestaña Connections) en lugar de solo IP:puerto
    @Bean
    public ConnectionNameStrategy connectionNameStrategy(
            @Value("${app.rabbitmq.connection-name}") String connectionName) {
        return connectionFactory -> connectionName;
    }

    public static final String EVENTS_EXCHANGE = "pedidos360.events";
    public static final String ORDER_CREATED_QUEUE = "pedidos360.payments.order-created.queue";
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";
    public static final String ORDER_CREATED_DEAD_QUEUE = "pedidos360.payments.order-created.dead";
    public static final String ORDER_CREATED_DEAD_ROUTING_KEY = "payments.order-created.dead";

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE)
                .deadLetterExchange(EVENTS_EXCHANGE)
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
        return BindingBuilder.bind(orderCreatedQueue)
                .to(eventsExchange)
                .with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCreatedDeadBinding(
            @Qualifier("orderCreatedDeadQueue") Queue orderCreatedDeadQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedDeadQueue)
                .to(eventsExchange)
                .with(ORDER_CREATED_DEAD_ROUTING_KEY);
    }
}