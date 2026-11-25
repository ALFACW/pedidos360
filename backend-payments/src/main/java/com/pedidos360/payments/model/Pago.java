package com.pedidos360.payments.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

/** Pago de un pedido: se crea PENDIENTE al llegar order.created y se cierra con WebPay. */
@Entity
@Table(name = "pagos")
@Data
public class Pago {
    @Id
    private Long pedidoId;
    private String numeroPedido;
    private String cliente;
    private String correo;
    private BigDecimal monto;
    private String estado;
    private String tarjeta;
    private String token;
}
