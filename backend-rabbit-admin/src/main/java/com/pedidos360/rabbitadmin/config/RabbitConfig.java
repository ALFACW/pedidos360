package com.pedidos360.rabbitadmin.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // Los errores de declaración se propagan al cliente en vez de quedar solo en el log
        admin.setIgnoreDeclarationExceptions(false);
        return admin;
    }
}
