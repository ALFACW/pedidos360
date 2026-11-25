package com.pedidos360.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** Evento del tópico pedidos360.intentos-pago. La clave del mensaje es la tarjeta. */
public record IntentoPagoDTO(
        @NotBlank String tarjeta,
        @NotBlank String cliente,
        @NotNull Long pedidoId,
        @NotNull @Positive BigDecimal monto,
        @NotNull Instant fecha) {
}
