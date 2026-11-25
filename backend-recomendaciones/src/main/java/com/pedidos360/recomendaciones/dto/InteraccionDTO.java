package com.pedidos360.recomendaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Evento del tópico pedidos360.interacciones-usuario (lo publican ms-productos, ms-carrito y ms-pedidos). */
public record InteraccionDTO(
        @NotBlank String usuario,
        @NotNull Long productoId,
        @NotBlank String nombreProducto,
        @NotBlank String categoria,
        @NotNull @Pattern(regexp = "VER|CARRITO|COMPRA") String accion,
        @NotNull Instant fecha) {
}
