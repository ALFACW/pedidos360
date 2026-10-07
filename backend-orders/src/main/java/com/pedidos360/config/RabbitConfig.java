package com.pedidos360.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "pedidos360.events";
    public static final String PAYMENT_RESULTS_QUEUE = "pedidos360.orders.payment-results.queue";
    public static final String PAYMENT_RESULTS_DEAD_QUEUE = "pedidos360.orders.payment-results.dead";
    public static final String PAYMENT_RESULTS_DEAD_ROUTING_KEY = "orders.payment-results.dead";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentResultsQueue() {
        return QueueBuilder.durable(PAYMENT_RESULTS_QUEUE)
                .deadLetterExchange(EVENTS_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_RESULTS_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentResultsDeadQueue() {
        return QueueBuilder.durable(PAYMENT_RESULTS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding approvedPaymentBinding(
            @Qualifier("paymentResultsQueue") Queue paymentResultsQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentResultsQueue).to(eventsExchange).with("payment.#");
    }

    @Bean
    public Binding paymentResultsDeadBinding(
            @Qualifier("paymentResultsDeadQueue") Queue paymentResultsDeadQueue,
            TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentResultsDeadQueue)
                .to(eventsExchange)
                .with(PAYMENT_RESULTS_DEAD_ROUTING_KEY);
    }
}