package com.pedidos360.rabbitadmin.dto;

/** Reglas de nombres compartidas por los DTO (RabbitMQ reserva el prefijo "amq."). */
public final class ResourceNames {

    public static final String NAME_REGEX = "^(?!amq\\.)[A-Za-z0-9._:-]{1,255}$";
    public static final String NAME_MESSAGE =
            "solo letras, números y . _ : - (máx. 255), sin el prefijo reservado amq.";
    public static final String ROUTING_KEY_REGEX = "^[A-Za-z0-9._:#*-]{0,255}$";
    public static final String ROUTING_KEY_MESSAGE = "solo letras, números y . _ : - # * (máx. 255)";

    private ResourceNames() {
    }
}
