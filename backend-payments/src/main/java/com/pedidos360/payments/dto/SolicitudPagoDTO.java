package com.pedidos360.payments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SolicitudPagoDTO(
        @NotNull Long pedidoId,
        @NotNull @Pattern(regexp = "\\d{16}", message = "debe tener 16 dígitos") String numeroTarjeta) {
}
