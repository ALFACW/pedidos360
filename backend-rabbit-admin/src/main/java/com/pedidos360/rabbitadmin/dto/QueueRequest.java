package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cola a crear. Si se indica deadLetterExchange, los mensajes rechazados (NACK) o expirados
 * se reenvían a ese exchange, que es como se arma una DLQ.
 */
public record QueueRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String name,
        Boolean durable,
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String deadLetterExchange,
        @Pattern(regexp = ResourceNames.ROUTING_KEY_REGEX, message = ResourceNames.ROUTING_KEY_MESSAGE)
        String deadLetterRoutingKey,
        @Positive(message = "debe ser mayor a 0")
        Integer messageTtlMs) {

    public boolean isDurable() {
        return durable == null || durable;
    }

    @AssertTrue(message = "deadLetterRoutingKey requiere deadLetterExchange")
    public boolean isDeadLetterConfigValid() {
        return deadLetterRoutingKey == null || deadLetterRoutingKey.isEmpty() || deadLetterExchange != null;
    }
}
