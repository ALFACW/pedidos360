package com.pedidos360.recomendaciones.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/** Ítem de la tabla InteraccionesUsuario: partición = usuario, orden = fecha (epoch ms). */
@DynamoDbBean
@Data
public class Interaccion {
    private String usuario;
    private Long fecha;
    private Long productoId;
    private String nombreProducto;
    private String categoria;
    private String accion;
    private int peso;

    @DynamoDbPartitionKey
    public String getUsuario() {
        return usuario;
    }

    @DynamoDbSortKey
    public Long getFecha() {
        return fecha;
    }
}
