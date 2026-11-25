package com.pedidos360.payments.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmacionPagoDTO(@NotBlank String token) {
}
