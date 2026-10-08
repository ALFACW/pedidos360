package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeRequest;
import com.pedidos360.rabbitadmin.dto.OperationResponse;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.dto.ResourceNames;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * API REST del microservicio administrador de RabbitMQ.
 *
 * <pre>
 * POST   /api/rabbit/queues                     crea una cola (opcionalmente con DLQ y TTL)
 * GET    /api/rabbit/queues/{name}              consulta mensajes y consumidores de una cola
 * DELETE /api/rabbit/queues/{name}              elimina una cola
 * POST   /api/rabbit/exchanges                  crea un exchange direct | topic | fanout | headers
 * DELETE /api/rabbit/exchanges/{name}           elimina un exchange
 * POST   /api/rabbit/bindings                   enlaza exchange -> cola con una routing key
 * DELETE /api/rabbit/bindings?queue=&amp;exchange=&amp;routingKey=   elimina el enlace
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/rabbit")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/queues")
    public ResponseEntity<QueueResponse> createQueue(@Valid @RequestBody QueueRequest request) {
        QueueResponse queue = rabbitAdminService.createQueue(request);
        return ResponseEntity.created(URI.create("/api/v1/rabbit/queues/" + queue.name())).body(queue);
    }

    @GetMapping("/queues/{name}")
    public QueueResponse getQueue(
            @PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
            String name) {
        return rabbitAdminService.getQueue(name);
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(
            @PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
            String name) {
        rabbitAdminService.deleteQueue(name);
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationResponse createExchange(@Valid @RequestBody ExchangeRequest request) {
        rabbitAdminService.createExchange(request);
        return new OperationResponse("exchange", request.name(), "Exchange " + request.type() + " creado");
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(
            @PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
            String name) {
        rabbitAdminService.deleteExchange(name);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationResponse createBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdminService.createBinding(request);
        return new OperationResponse("binding", request.exchange() + " -> " + request.queue(),
                "Binding creado con routing key '" + request.routingKey() + "'");
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@Valid @ModelAttribute BindingRequest request) {
        rabbitAdminService.deleteBinding(request);
    }
}
