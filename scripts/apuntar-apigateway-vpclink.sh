#!/usr/bin/env bash
# Cambia el API Gateway para que llegue al backend PRIVADO:
#   API Gateway --VPC Link--> ALB interno --/api/<servicio>--> EC2 privada (8080-8084)
# Crea las rutas /api/rabbit (ms-rabbit-admin) con el mismo autorizador JWT.
# Las integraciones antiguas (IP pública) no se borran: sirven para volver atrás.
#
# Uso: bash scripts/apuntar-apigateway-vpclink.sh
set -euo pipefail

REGION="us-east-1"
API_ID="${API_ID:-4dyx4unmg5}"
VPC_LINK_NAME="${VPC_LINK_NAME:-pedidos360-vpclink}"
ALB_NAME="${ALB_NAME:-pedidos360-alb-interno}"
R="--region $REGION"

VPC_LINK_ID=$(aws apigatewayv2 get-vpc-links $R --query "Items[?Name=='$VPC_LINK_NAME'].VpcLinkId | [0]" --output text)
ALB_ARN=$(aws elbv2 describe-load-balancers $R --names "$ALB_NAME" --query "LoadBalancers[0].LoadBalancerArn" --output text)
LISTENER_ARN=$(aws elbv2 describe-listeners $R --load-balancer-arn "$ALB_ARN" --query "Listeners[?Port==\`80\`].ListenerArn | [0]" --output text)
AUTHORIZER_ID=$(aws apigatewayv2 get-authorizers $R --api-id "$API_ID" --query "Items[?Name=='entra-id-jwt'].AuthorizerId | [0]" --output text)
echo "VPC Link: $VPC_LINK_ID | Autorizador JWT: $AUTHORIZER_ID"

echo "1) Integración privada (VPC Link -> ALB interno)..."
INTEGRATION_ID=$(aws apigatewayv2 get-integrations $R --api-id "$API_ID" \
  --query "Items[?ConnectionId=='$VPC_LINK_ID'].IntegrationId | [0]" --output text)
if [ "$INTEGRATION_ID" = "None" ]; then
  INTEGRATION_ID=$(aws apigatewayv2 create-integration $R --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method ANY \
    --connection-type VPC_LINK --connection-id "$VPC_LINK_ID" \
    --integration-uri "$LISTENER_ARN" --payload-format-version 1.0 \
    --query IntegrationId --output text)
fi
echo "   integración: $INTEGRATION_ID"

echo "2) Rutas del microservicio administrador de RabbitMQ..."
EXISTING=$(aws apigatewayv2 get-routes $R --api-id "$API_ID" --query "Items[].RouteKey" --output text)
for KEY in "ANY /api/rabbit" "ANY /api/rabbit/{proxy+}"; do
  grep -qF "$KEY" <<< "$EXISTING" || aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" \
    --authorization-type JWT --authorizer-id "$AUTHORIZER_ID" --query RouteKey --output text
done
for KEY in "OPTIONS /api/rabbit" "OPTIONS /api/rabbit/{proxy+}"; do
  grep -qF "$KEY" <<< "$EXISTING" || aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" \
    --authorization-type NONE --query RouteKey --output text
done

echo "3) Apuntando todas las rutas a la integración privada..."
aws apigatewayv2 get-routes $R --api-id "$API_ID" --query "Items[].RouteId" --output text | tr '\t' '\n' |
while read -r ROUTE_ID; do
  aws apigatewayv2 update-route $R --api-id "$API_ID" --route-id "$ROUTE_ID" \
    --target "integrations/$INTEGRATION_ID" --query "[RouteKey,AuthorizationType]" --output text
done

echo "Listo. Prueba: curl -i https://$API_ID.execute-api.$REGION.amazonaws.com/api/products  (debe dar 401 sin token)"
