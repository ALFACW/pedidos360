#!/bin/bash
TOKEN="$1"

if [ -z "$TOKEN" ]; then
  echo "Debes pasar el token como argumento."
  echo "Ejemplo: ./probar-con-token.sh <access-token>"
  exit 1
fi

echo "=== PROBANDO ENDPOINT PROTEGIDO CON TOKEN (200 OK) ==="
curl -i -H "Authorization: Bearer $TOKEN" https://t13j9mbqg7.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products
echo ""
