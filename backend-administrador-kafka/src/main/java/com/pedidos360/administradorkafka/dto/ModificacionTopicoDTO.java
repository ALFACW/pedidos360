package com.pedidos360.administradorkafka.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Cambios a un tópico existente; los campos nulos no se modifican. Kafka solo permite aumentar particiones. */
public record ModificacionTopicoDTO(
        @Positive @Max(12) Integer particiones,
        @Positive Long retencionMs,
        @Pattern(regexp = "delete|compact") String cleanupPolicy) {
}
