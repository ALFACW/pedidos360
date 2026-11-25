package com.pedidos360.dashboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/** Lambda "Mensajero": guarda ($connect) o borra ($disconnect) la conexión WebSocket del dashboard. */
public class ConexionHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    static final String TABLA = "ConexionesWebSocket";
    private final DynamoDbClient dynamo = DynamoDbClient.create();

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent evento, Context context) {
        Map<String, AttributeValue> clave = Map.of("connectionId", AttributeValue.fromS(evento.getRequestContext().getConnectionId()));
        if ("$connect".equals(evento.getRequestContext().getRouteKey())) {
            dynamo.putItem(r -> r.tableName(TABLA).item(clave));
        } else {
            dynamo.deleteItem(r -> r.tableName(TABLA).key(clave));
        }
        APIGatewayV2WebSocketResponse respuesta = new APIGatewayV2WebSocketResponse();
        respuesta.setStatusCode(200);
        return respuesta;
    }
}
