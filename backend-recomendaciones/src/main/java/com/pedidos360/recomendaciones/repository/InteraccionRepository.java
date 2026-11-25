package com.pedidos360.recomendaciones.repository;

import com.pedidos360.recomendaciones.model.Interaccion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
public class InteraccionRepository {

    private final DynamoDbTable<Interaccion> tabla;

    public InteraccionRepository(DynamoDbEnhancedClient cliente, @Value("${app.dynamodb.tabla}") String nombreTabla) {
        this.tabla = cliente.table(nombreTabla, TableSchema.fromBean(Interaccion.class));
    }

    public void guardar(Interaccion interaccion) {
        tabla.putItem(interaccion);
    }

    public List<Interaccion> buscarPorUsuario(String usuario) {
        return tabla.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(usuario).build())).items().stream().toList();
    }

    public List<Interaccion> buscarTodas() {
        return tabla.scan().items().stream().toList();
    }
}
