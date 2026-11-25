#!/usr/bin/env bash
# Crea el API Gateway versionado según la guía "API VERSIONADA V1.0.0" (Método A, ruta a ruta):
#   - una ruta por cada método/endpoint (sin ANY ni {proxy+})
#   - una integración por cada ruta/método (VPC Link -> ALB interno -> EC2 privada),
#     con overwrite:path = $request.path para que el backend no reciba el prefijo de la etapa
#   - autorizador JWT de Entra ID en las rutas protegidas
#   - CORS solo para el origen del frontend (API Gateway responde el preflight OPTIONS)
#   - etapa "desarrollo" con su implementación (deployment)
#
# Uso: bash scripts/crear-api-v1.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash: no convertir rutas "/api/..." en rutas de Windows

REGION="us-east-1"
API_NAME="${API_NAME:-pedidos360-api-v1}"
STAGE="${STAGE:-desarrollo}"
FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-https://44-221-145-83.nip.io}"
TENANT_ID="e5372bf0-c5e3-4286-887c-79069f209c1f"
AUDIENCE="96aa35e3-1c70-4e36-8a41-0e1089f78df1"
R="--region $REGION"

# MÉTODO RUTA AUTORIZACIÓN(JWT|NONE)
ROUTES="
GET    /api/v1/orders/health             NONE
GET    /api/v1/orders/public             NONE
GET    /api/v1/orders                    JWT
POST   /api/v1/orders                    JWT
GET    /api/v1/orders/{id}               JWT
GET    /api/v1/products/health           NONE
GET    /api/v1/products/public           NONE
GET    /api/v1/products                  JWT
POST   /api/v1/products                  JWT
GET    /api/v1/products/{id}             JWT
PUT    /api/v1/products/{id}             JWT
DELETE /api/v1/products/{id}             JWT
GET    /api/v1/users/health              NONE
GET    /api/v1/users/public              NONE
GET    /api/v1/users                     JWT
POST   /api/v1/users                     JWT
GET    /api/v1/users/me                  JWT
GET    /api/v1/users/{id}                JWT
PUT    /api/v1/users/{id}                JWT
DELETE /api/v1/users/{id}                JWT
GET    /api/v1/cart/health               NONE
GET    /api/v1/cart                      JWT
GET    /api/v1/rabbit/health             NONE
POST   /api/v1/rabbit/queues             JWT
GET    /api/v1/rabbit/queues/{name}      JWT
DELETE /api/v1/rabbit/queues/{name}      JWT
POST   /api/v1/rabbit/exchanges          JWT
DELETE /api/v1/rabbit/exchanges/{name}   JWT
POST   /api/v1/rabbit/bindings           JWT
DELETE /api/v1/rabbit/bindings           JWT
"

VPC_LINK_ID=$(aws apigatewayv2 get-vpc-links $R --query "Items[?Name=='pedidos360-vpclink'].VpcLinkId | [0]" --output text)
ALB_ARN=$(aws elbv2 describe-load-balancers $R --names pedidos360-alb-interno --query "LoadBalancers[0].LoadBalancerArn" --output text)
LISTENER_ARN=$(aws elbv2 describe-listeners $R --load-balancer-arn "$ALB_ARN" --query "Listeners[?Port==\`80\`].ListenerArn | [0]" --output text)

echo "1) API HTTP $API_NAME..."
API_ID=$(aws apigatewayv2 get-apis $R --query "Items[?Name=='$API_NAME'].ApiId | [0]" --output text)
if [ "$API_ID" = "None" ]; then
  API_ID=$(aws apigatewayv2 create-api $R --name "$API_NAME" --protocol-type HTTP \
    --cors-configuration "AllowOrigins=$FRONTEND_ORIGIN,AllowMethods=GET,POST,PUT,DELETE,OPTIONS,AllowHeaders=authorization,content-type,MaxAge=3600" \
    --tags Proyecto=pedidos360 --query ApiId --output text)
fi
echo "   ApiId: $API_ID"

echo "2) Autorizador JWT (Entra ID)..."
AUTHORIZER_ID=$(aws apigatewayv2 get-authorizers $R --api-id "$API_ID" --query "Items[?Name=='entra-id-jwt'].AuthorizerId | [0]" --output text)
if [ "$AUTHORIZER_ID" = "None" ]; then
  AUTHORIZER_ID=$(aws apigatewayv2 create-authorizer $R --api-id "$API_ID" --name entra-id-jwt \
    --authorizer-type JWT --identity-source '$request.header.Authorization' \
    --jwt-configuration "Audience=$AUDIENCE,Issuer=https://login.microsoftonline.com/$TENANT_ID/v2.0" \
    --query AuthorizerId --output text)
fi

echo "3) Rutas e integraciones (una por método)..."
EXISTING=$(aws apigatewayv2 get-routes $R --api-id "$API_ID" --query "Items[].RouteKey" --output text | tr '\t' '\n')
echo "$ROUTES" | while read -r METHOD ROUTE_PATH AUTH; do
  [ -z "$METHOD" ] && continue
  KEY="$METHOD $ROUTE_PATH"
  if grep -qxF "$KEY" <<< "$EXISTING"; then echo "   = $KEY"; continue; fi
  INTEGRATION_ID=$(aws apigatewayv2 create-integration $R --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method "$METHOD" \
    --connection-type VPC_LINK --connection-id "$VPC_LINK_ID" \
    --integration-uri "$LISTENER_ARN" --payload-format-version 1.0 \
    --request-parameters '{"overwrite:path":"$request.path"}' \
    --description "$KEY" --query IntegrationId --output text)
  if [ "$AUTH" = "JWT" ]; then
    aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" --target "integrations/$INTEGRATION_ID" \
      --authorization-type JWT --authorizer-id "$AUTHORIZER_ID" --query RouteKey --output text >/dev/null
  else
    aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" --target "integrations/$INTEGRATION_ID" \
      --authorization-type NONE --query RouteKey --output text >/dev/null
  fi
  echo "   + $KEY ($AUTH)"
done

echo "4) Verificando que no queden rutas ANY ni {proxy+}..."
aws apigatewayv2 get-routes $R --api-id "$API_ID" \
  --query "Items[?starts_with(RouteKey, 'ANY ') || contains(RouteKey, '{proxy+}')].[RouteId,RouteKey,Target]" --output text |
while IFS=$'\t' read -r ROUTE_ID KEY TARGET; do
  [ -z "$ROUTE_ID" ] && continue
  aws apigatewayv2 delete-route $R --api-id "$API_ID" --route-id "$ROUTE_ID"
  echo "   - $KEY (eliminada)"
  if [ -n "$TARGET" ] && [ "$TARGET" != "None" ]; then
    aws apigatewayv2 delete-integration $R --api-id "$API_ID" --integration-id "${TARGET#integrations/}" || true
  fi
done

echo "5) Etapa '$STAGE' e implementación..."
aws apigatewayv2 get-stage $R --api-id "$API_ID" --stage-name "$STAGE" >/dev/null 2>&1 \
  || aws apigatewayv2 create-stage $R --api-id "$API_ID" --stage-name "$STAGE" --tags Proyecto=pedidos360 >/dev/null
DEPLOYMENT_ID=$(aws apigatewayv2 create-deployment $R --api-id "$API_ID" --stage-name "$STAGE" \
  --description "Rutas v1 ruta a ruta" --query DeploymentId --output text)
echo "   deployment: $DEPLOYMENT_ID"

echo
echo "URL base: https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE"
echo "Prueba:   curl -i https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE/api/v1/products   (401 sin token)"
