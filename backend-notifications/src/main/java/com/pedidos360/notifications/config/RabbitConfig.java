package com.pedidos360.notifications.config;

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
 * Mensajería de ms-notificaciones.
 *
 * <pre>
 * Consume:  pedidos360.events (topic) --payment.#--> pedidos360.notifications.payment-results.queue
 * DLQ:      pedidos360.dlx (direct) --notifications.payment-results.dead--> pedidos360.notifications.payment-results.dead
 * </pre>
 */
@Configuration
public class RabbitConfig {

    // Nombre visible en la consola de RabbitMQ (pestaña Connections) en lugar de solo IP:puerto
    @Bean
    public ConnectionNameStrategy connectionNameStrategy(
            @Value("${app.rabbitmq.connection-name}") String connectionName) {
        return connectionFactory -> connectionName;
    }

    public static final String EVENTS_EXCHANGE = "pedidos360.events";
    public static final String DEAD_LETTER_EXCHANGE = "pedidos360.dlx";
    public static final String PAYMENT_RESULTS_QUEUE = "pedidos360.notifications.payment-results.queue";
    public static final String PAYMENT_RESULTS_ROUTING_KEY = "payment.#";
    public static final String PAYMENT_RESULTS_DEAD_QUEUE = "pedidos360.notifications.payment-results.dead";
    public static final String PAYMENT_RESULTS_DEAD_ROUTING_KEY = "notifications.payment-results.dead";

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
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
    public Queue paymentResultsQueue() {
        return QueueBuilder.durable(PAYMENT_RESULTS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_RESULTS_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentResultsDeadQueue() {
        return QueueBuilder.durable(PAYMENT_RESULTS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding paymentResultsBinding(
            @Qualifier("paymentResultsQueue") Queue paymentResultsQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentResultsQueue)
                .to(eventsExchange)
                .with(PAYMENT_RESULTS_ROUTING_KEY);
    }

    @Bean
    public Binding paymentResultsDeadBinding(
            @Qualifier("paymentResultsDeadQueue") Queue paymentResultsDeadQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(paymentResultsDeadQueue)
                .to(deadLetterExchange)
                .with(PAYMENT_RESULTS_DEAD_ROUTING_KEY);
    }
}