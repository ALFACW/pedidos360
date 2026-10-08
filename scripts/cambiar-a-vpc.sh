#!/usr/bin/env bash
# Corte final a la arquitectura VPC pública/privada:
#   1. Mueve la Elastic IP del frontend (44.221.145.83) a la EC2 pública nueva (frontend + bastión),
#      así la URL https://44-221-145-83.nip.io y la Redirect URI de Azure no cambian.
#   2. Espera a que Caddy obtenga el certificado HTTPS en la instancia nueva.
#   3. DETIENE (no elimina) las 2 EC2 antiguas de la VPC por defecto.
#
# Uso: bash scripts/cambiar-a-vpc.sh
set -euo pipefail

REGION="us-east-1"
R="--region $REGION"
FRONTEND_EIP_ALLOC="eipalloc-0036785dc1f680017"   # 44.221.145.83
NEW_FRONTEND="i-00244a8da791f5a1a"                # pedidos360-frontend-bastion (subred pública)
OLD_INSTANCES="i-060271eba7e4c69bf i-0085ceedef94debc5"
URL="https://44-221-145-83.nip.io/"

echo "1) Moviendo la Elastic IP 44.221.145.83 a la EC2 pública nueva..."
aws ec2 associate-address $R --allocation-id "$FRONTEND_EIP_ALLOC" --instance-id "$NEW_FRONTEND" \
  --allow-reassociation --query AssociationId --output text

echo "2) Esperando el certificado HTTPS de Caddy..."
for _ in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL" || true)
  [ "$code" = "200" ] && break
  sleep 10
done
echo "   $URL -> $code"
if [ "$code" != "200" ]; then
  echo "El frontend nuevo no responde; NO se detienen las instancias antiguas." >&2
  exit 1
fi

echo "3) Deteniendo las EC2 antiguas (se pueden volver a encender)..."
aws ec2 stop-instances $R --instance-ids $OLD_INSTANCES \
  --query "StoppingInstances[].[InstanceId,CurrentState.Name]" --output text

echo "Listo. Bastión/frontend: 44.221.145.83 | Backend privado: 10.0.132.193"
