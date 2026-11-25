#!/usr/bin/env bash
# Demo antifraude: la misma tarjeta 5 veces seguidas (1 por segundo) sobre un pedido pendiente.
# Esperado: 200 200 200 200 429 -> al 4.º intento en 10 s la tarjeta queda bloqueada 30 min.
# Uso: bash scripts/probar-antifraude.sh <token> <id-pedido-pendiente> [tarjeta]
# Usa una tarjeta inventada (por defecto 4111111111111111) para no bloquear la de prueba de Transbank.
TOKEN="${1:?Pasa el access token (botón Ver token JWT)}"
PEDIDO="${2:?Pasa el id de un pedido pendiente de pago}"
TARJETA="${3:-4111111111111111}"
API=https://t13j9mbqg7.execute-api.us-east-1.amazonaws.com/desarrollo

for i in 1 2 3 4 5; do
  codigo=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/v1/pagos/iniciar" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"pedidoId\": $PEDIDO, \"numeroTarjeta\": \"$TARJETA\"}")
  echo "Intento $i: HTTP $codigo $([ "$codigo" = 429 ] && echo '-> tarjeta bloqueada por fraude')"
  sleep 1
done
