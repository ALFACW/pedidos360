#!/usr/bin/env bash
# API Gateway del FRONTEND (guía "Desplegando mi frontend en AWS"):
#   HTTPS de AWS -> GET /{proxy+} -> http://<IP pública del frontend>/{proxy}
# Separado del API del backend (pedidos360-api-v1): frontend y backend no se mezclan.
#
# Sin rutas ANY: el frontend es un sitio estático (React compilado servido por Nginx),
# así que solo necesita GET. "GET /" entrega index.html y "GET /{proxy+}" los assets
# (sus nombres llevan un hash que cambia en cada build, por eso la ruta es genérica).
# Si quedaron rutas ANY de una versión anterior, el script las elimina después de crear las GET
# (así el frontend no queda sin ruta mientras se ejecuta).
#
# Uso: bash scripts/crear-api-frontend.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1

REGION="us-east-1"
API_NAME="${API_NAME:-pedidos360-frontend}"
STAGE="${STAGE:-desarrollo}"
FRONTEND_IP="${FRONTEND_IP:-44.221.145.83}"
R="--region $REGION"

API_ID=$(aws apigatewayv2 get-apis $R --query "Items[?Name=='$API_NAME'].ApiId | [0]" --output text)
if [ "$API_ID" = "None" ]; then
  API_ID=$(aws apigatewayv2 create-api $R --name "$API_NAME" --protocol-type HTTP \
    --tags Proyecto=pedidos360 --query ApiId --output text)
fi
echo "ApiId: $API_ID"

echo "1) Rutas GET..."
EXISTING=$(aws apigatewayv2 get-routes $R --api-id "$API_ID" --query "Items[].RouteKey" --output text | tr '\t' '\n')
for PAIR in "GET /|http://$FRONTEND_IP/" "GET /{proxy+}|http://$FRONTEND_IP/{proxy}"; do
  KEY="${PAIR%%|*}"; URI="${PAIR#*|}"
  grep -qxF "$KEY" <<< "$EXISTING" && { echo "   = $KEY"; continue; }
  INTEGRATION_ID=$(aws apigatewayv2 create-integration $R --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method GET --integration-uri "$URI" \
    --payload-format-version 1.0 --description "$KEY" --query IntegrationId --output text)
  aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" \
    --target "integrations/$INTEGRATION_ID" --query RouteKey --output text >/dev/null
  echo "   + $KEY -> $URI"
done

echo "2) Eliminando rutas ANY antiguas (si existen)..."
aws apigatewayv2 get-routes $R --api-id "$API_ID" \
  --query "Items[?starts_with(RouteKey, 'ANY ')].[RouteId,RouteKey,Target]" --output text |
while IFS=$'\t' read -r ROUTE_ID KEY TARGET; do
  [ -z "$ROUTE_ID" ] && continue
  aws apigatewayv2 delete-route $R --api-id "$API_ID" --route-id "$ROUTE_ID"
  echo "   - $KEY"
  if [ -n "$TARGET" ] && [ "$TARGET" != "None" ]; then
    aws apigatewayv2 delete-integration $R --api-id "$API_ID" --integration-id "${TARGET#integrations/}" || true
  fi
done

aws apigatewayv2 get-stage $R --api-id "$API_ID" --stage-name "$STAGE" >/dev/null 2>&1 \
  || aws apigatewayv2 create-stage $R --api-id "$API_ID" --stage-name "$STAGE" --tags Proyecto=pedidos360 >/dev/null
aws apigatewayv2 create-deployment $R --api-id "$API_ID" --stage-name "$STAGE" \
  --description "Frontend (solo GET)" --query DeploymentId --output text >/dev/null

echo "URL del frontend: https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE/"
