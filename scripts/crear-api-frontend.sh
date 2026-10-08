#!/usr/bin/env bash
# API Gateway del FRONTEND (guía "Desplegando mi frontend en AWS"):
#   HTTPS de AWS -> ANY /{proxy+} -> http://<IP pública del frontend>/{proxy}
# Separado del API del backend (pedidos360-api-v1): frontend y backend no se mezclan.
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

EXISTING=$(aws apigatewayv2 get-routes $R --api-id "$API_ID" --query "Items[].RouteKey" --output text | tr '\t' '\n')
# "ANY /" sirve index.html en la raíz de la etapa; "ANY /{proxy+}" el resto (assets, rutas de la SPA)
for PAIR in "ANY /|http://$FRONTEND_IP/" "ANY /{proxy+}|http://$FRONTEND_IP/{proxy}"; do
  KEY="${PAIR%%|*}"; URI="${PAIR#*|}"
  grep -qxF "$KEY" <<< "$EXISTING" && { echo "= $KEY"; continue; }
  INTEGRATION_ID=$(aws apigatewayv2 create-integration $R --api-id "$API_ID" \
    --integration-type HTTP_PROXY --integration-method ANY --integration-uri "$URI" \
    --payload-format-version 1.0 --query IntegrationId --output text)
  aws apigatewayv2 create-route $R --api-id "$API_ID" --route-key "$KEY" \
    --target "integrations/$INTEGRATION_ID" --query RouteKey --output text >/dev/null
  echo "+ $KEY -> $URI"
done

aws apigatewayv2 get-stage $R --api-id "$API_ID" --stage-name "$STAGE" >/dev/null 2>&1 \
  || aws apigatewayv2 create-stage $R --api-id "$API_ID" --stage-name "$STAGE" --tags Proyecto=pedidos360 >/dev/null
aws apigatewayv2 create-deployment $R --api-id "$API_ID" --stage-name "$STAGE" \
  --description "Frontend" --query DeploymentId --output text >/dev/null

echo "URL del frontend: https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE/"
