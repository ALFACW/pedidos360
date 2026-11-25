package com.pedidos360.payments.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** Ventana antifraude de una tarjeta: intentos desde inicioVentana y bloqueo vigente. */
@Entity
@Table(name = "estado_tarjetas")
@Data
public class EstadoTarjeta {
    @Id
    private String tarjeta;
    private int intentos;
    private Instant inicioVentana;
    private Instant bloqueadoHasta;
}
