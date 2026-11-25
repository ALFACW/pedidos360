package com.pedidos360.dashboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.Map;

/** Lambda "Stream": por cada interacción nueva en DynamoDB la envía a todos los dashboards conectados. */
public class InteraccionStreamHandler implements RequestHandler<DynamodbEvent, Void> {

    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final ApiGatewayManagementApiClient websocket = ApiGatewayManagementApiClient.builder()
            .endpointOverride(URI.create(System.getenv("WEBSOCKET_URL"))).build();

    @Override
    public Void handleRequest(DynamodbEvent evento, Context context) {
        var conexiones = dynamo.scan(r -> r.tableName(ConexionHandler.TABLA)).items();
        for (DynamodbEvent.DynamodbStreamRecord registro : evento.getRecords()) {
            if (!"INSERT".equals(registro.getEventName())) continue;
            SdkBytes mensaje = SdkBytes.fromUtf8String(json(registro.getDynamodb().getNewImage()));
            for (var conexion : conexiones) {
                String id = conexion.get("connectionId").s();
                try {
                    websocket.postToConnection(r -> r.connectionId(id).data(mensaje));
                } catch (GoneException cerrada) {
                    dynamo.deleteItem(r -> r.tableName(ConexionHandler.TABLA).key(conexion));
                }
            }
        }
        return null;
    }

    private static String json(Map<String, AttributeValue> item) {
        return String.format("{\"usuario\":\"%s\",\"productoId\":%s,\"nombreProducto\":\"%s\",\"categoria\":\"%s\",\"accion\":\"%s\",\"fecha\":%s}",
                item.get("usuario").getS(), item.get("productoId").getN(), item.get("nombreProducto").getS().replace("\"", "'"),
                item.get("categoria").getS(), item.get("accion").getS(), item.get("fecha").getN());
    }
}
