#!/usr/bin/env bash
# API Gateway del FRONTEND (guía "Desplegando mi frontend en AWS"): HTTPS de AWS -> EC2 frontend (Nginx).
# Una ruta por recurso y solo GET (sin ANY ni {proxy+}):
#   GET /                 -> index.html (también recibe el retorno de WebPay con ?token_ws=...)
#   GET /assets/{archivo} -> JS y CSS generados por Vite
# Uso: bash scripts/crear-api-frontend.sh
source "$(dirname "$0")/infra/comun.sh"
API_NAME=pedidos360-frontend
STAGE=desarrollo
FRONTEND_IP=$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=pedidos360-frontend-eip" --query "Addresses[0].PublicIp" --output text)

API_ID=$(aws apigatewayv2 get-apis --query "Items[?Name=='$API_NAME'].ApiId | [0]" --output text)
if [ "$API_ID" = "None" ]; then
  API_ID=$(aws apigatewayv2 create-api --name "$API_NAME" --protocol-type HTTP --query ApiId --output text)
fi

EXISTENTES=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query "Items[].RouteKey" --output text | tr '\t' '\n')
for PAR in "GET /|http://$FRONTEND_IP/" "GET /assets/{archivo}|http://$FRONTEND_IP/assets/{archivo}"; do
  RUTA="${PAR%%|*}"; URI="${PAR#*|}"
  grep -qxF "$RUTA" <<< "$EXISTENTES" && continue
  INTEGRACION=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type HTTP_PROXY \
    --integration-method GET --integration-uri "$URI" --payload-format-version 1.0 --query IntegrationId --output text)
  aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$RUTA" --target "integrations/$INTEGRACION" >/dev/null
  echo "+ $RUTA -> $URI"
done

aws apigatewayv2 get-stage --api-id "$API_ID" --stage-name "$STAGE" >/dev/null 2>&1 \
  || aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name "$STAGE" >/dev/null
aws apigatewayv2 create-deployment --api-id "$API_ID" --stage-name "$STAGE" >/dev/null
echo "URL del frontend: https://$API_ID.execute-api.us-east-1.amazonaws.com/$STAGE/"
