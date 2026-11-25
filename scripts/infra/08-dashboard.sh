#!/usr/bin/env bash
# Dashboard en tiempo real (guía "Telemetría a tiempo real con frontend"):
#   InteraccionesUsuario (DynamoDB Stream) -> Lambda pedidos360-stream -> API WebSocket -> navegador
#   Lambda pedidos360-mensajero guarda/borra las conexiones ($connect/$disconnect) en ConexionesWebSocket.
# Uso: (cd lambda-dashboard && mvn -q package) && bash scripts/infra/08-dashboard.sh
source "$(dirname "$0")/comun.sh"
JAR=fileb://lambda-dashboard/target/lambda-dashboard.jar
CUENTA=$(aws sts get-caller-identity --query Account --output text)
ROL="arn:aws:iam::$CUENTA:role/LabRole"
STAGE=desarrollo

aws dynamodb describe-table --table-name ConexionesWebSocket >/dev/null 2>&1 || aws dynamodb create-table \
  --table-name ConexionesWebSocket --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=connectionId,AttributeType=S --key-schema AttributeName=connectionId,KeyType=HASH >/dev/null

crear_lambda() { # nombre handler [variables]
  if aws lambda get-function --function-name "$1" >/dev/null 2>&1; then
    aws lambda update-function-code --function-name "$1" --zip-file "$JAR" >/dev/null
  else
    aws lambda create-function --function-name "$1" --runtime java21 --role "$ROL" --handler "$2" \
      --memory-size 512 --timeout 30 --zip-file "$JAR" ${3:+--environment "Variables={$3}"} >/dev/null
    aws lambda wait function-active --function-name "$1"
  fi
}
crear_lambda pedidos360-mensajero com.pedidos360.dashboard.ConexionHandler::handleRequest
MENSAJERO=$(aws lambda get-function --function-name pedidos360-mensajero --query Configuration.FunctionArn --output text)

API_ID=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-dashboard'].ApiId | [0]" --output text)
if [ "$API_ID" = "None" ]; then
  API_ID=$(aws apigatewayv2 create-api --name pedidos360-dashboard --protocol-type WEBSOCKET \
    --route-selection-expression '$request.body.action' --query ApiId --output text)
  INTEGRACION=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type AWS_PROXY \
    --integration-uri "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/$MENSAJERO/invocations" --query IntegrationId --output text)
  for RUTA in '$connect' '$disconnect'; do
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$RUTA" --target "integrations/$INTEGRACION" >/dev/null
  done
  aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name "$STAGE" --auto-deploy >/dev/null
  aws apigatewayv2 create-deployment --api-id "$API_ID" --stage-name "$STAGE" >/dev/null
  aws lambda add-permission --function-name pedidos360-mensajero --statement-id websocket --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:$CUENTA:$API_ID/*" >/dev/null
fi

crear_lambda pedidos360-stream com.pedidos360.dashboard.InteraccionStreamHandler::handleRequest \
  "WEBSOCKET_URL=https://$API_ID.execute-api.us-east-1.amazonaws.com/$STAGE"
STREAM=$(aws dynamodb describe-table --table-name InteraccionesUsuario --query Table.LatestStreamArn --output text)
[ "$(aws lambda list-event-source-mappings --function-name pedidos360-stream --query 'length(EventSourceMappings)')" = "0" ] && \
  aws lambda create-event-source-mapping --function-name pedidos360-stream --event-source-arn "$STREAM" \
    --starting-position LATEST --batch-size 10 >/dev/null
echo "WebSocket del dashboard: wss://$API_ID.execute-api.us-east-1.amazonaws.com/$STAGE"
