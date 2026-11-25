package com.pedidos360.administradorkafka.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Tópico a crear. replicas no puede superar los 3 brokers del clúster. */
public record TopicoDTO(
        @NotNull @Pattern(regexp = "[a-zA-Z0-9._-]{3,100}", message = "solo letras, números, '.', '_' o '-' (3 a 100)") String nombre,
        @NotNull @Min(1) @Max(12) Integer particiones,
        @NotNull @Min(1) @Max(3) Short replicas,
        @NotNull @Positive Long retencionMs,
        @NotNull @Pattern(regexp = "delete|compact") String cleanupPolicy) {
}
