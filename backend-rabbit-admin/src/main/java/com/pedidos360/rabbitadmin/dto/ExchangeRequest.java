package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String name,
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "(?i)direct|topic|fanout|headers", message = "debe ser direct, topic, fanout o headers")
        String type,
        Boolean durable) {

    public boolean isDurable() {
        return durable == null || durable;
    }
}
