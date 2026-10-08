package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeRequest;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.exception.ProtectedResourceException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Toda la administración de RabbitMQ (colas, exchanges y bindings) pasa por este servicio.
 * Los controladores solo llaman a estos métodos y no conocen la API de AMQP.
 */
@Service
public class RabbitAdminService {

    private static final Logger logger = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final String protectedPrefix;

    public RabbitAdminService(
            AmqpAdmin amqpAdmin,
            @Value("${app.rabbit-admin.protected-prefix}") String protectedPrefix) {
        this.amqpAdmin = amqpAdmin;
        this.protectedPrefix = protectedPrefix;
    }

    public QueueResponse createQueue(QueueRequest request) {
        ensureNotProtected("cola", request.name());
        QueueBuilder builder = request.isDurable()
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());
        if (request.deadLetterExchange() != null) {
            builder.deadLetterExchange(request.deadLetterExchange());
            if (request.deadLetterRoutingKey() != null) {
                builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
            }
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs());
        }
        Queue queue = builder.build();
        amqpAdmin.declareQueue(queue);
        logger.info("Cola creada: {} (durable={}, dlx={})", queue.getName(), queue.isDurable(),
                request.deadLetterExchange());
        return getQueue(queue.getName());
    }

    public QueueResponse getQueue(String name) {
        QueueInformation info = amqpAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("No existe la cola " + name);
        }
        return new QueueResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void deleteQueue(String name) {
        ensureNotProtected("cola", name);
        if (amqpAdmin.getQueueInfo(name) == null) {
            throw new ResourceNotFoundException("No existe la cola " + name);
        }
        amqpAdmin.deleteQueue(name);
        logger.info("Cola eliminada: {}", name);
    }

    public void createExchange(ExchangeRequest request) {
        ensureNotProtected("exchange", request.name());
        String type = request.type().toLowerCase(Locale.ROOT);
        Exchange exchange = new ExchangeBuilder(request.name(), type)
                .durable(request.isDurable())
                .build();
        amqpAdmin.declareExchange(exchange);
        logger.info("Exchange creado: {} (tipo={}, durable={})", request.name(), type, request.isDurable());
    }

    public void deleteExchange(String name) {
        ensureNotProtected("exchange", name);
        amqpAdmin.deleteExchange(name);
        logger.info("Exchange eliminado: {}", name);
    }

    public void createBinding(BindingRequest request) {
        ensureNotProtected("cola", request.queue());
        amqpAdmin.declareBinding(toBinding(request));
        logger.info("Binding creado: {} --{}--> {}", request.exchange(), request.routingKey(), request.queue());
    }

    public void deleteBinding(BindingRequest request) {
        ensureNotProtected("cola", request.queue());
        amqpAdmin.removeBinding(toBinding(request));
        logger.info("Binding eliminado: {} --{}--> {}", request.exchange(), request.routingKey(), request.queue());
    }

    private static Binding toBinding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE, request.exchange(),
                request.routingKey(), null);
    }

    // Las colas/exchanges de los microservicios (pedidos360.*) se declaran desde su propio código
    private void ensureNotProtected(String resource, String name) {
        if (!protectedPrefix.isBlank() && name.startsWith(protectedPrefix)) {
            throw new ProtectedResourceException("No se puede modificar " + resource + " " + name
                    + ": pertenece a los microservicios (prefijo " + protectedPrefix + ")");
        }
    }
}
