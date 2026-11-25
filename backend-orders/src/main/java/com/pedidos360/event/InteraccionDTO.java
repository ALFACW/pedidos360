package com.pedidos360.event;

import java.time.Instant;

/** Evento del tópico pedidos360.interacciones-usuario. La clave del mensaje es el usuario. */
public record InteraccionDTO(
        String usuario, Long productoId, String nombreProducto, String categoria, String accion, Instant fecha) {
}
