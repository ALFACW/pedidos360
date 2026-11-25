#!/usr/bin/env bash
# Construye las imágenes de los microservicios y las sube a ECR. Uso: bash scripts/infra/05-imagenes.sh [carpeta:nombre ...]
source "$(dirname "$0")/comun.sh"
ECR="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com"
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR"

SERVICIOS=("$@")
[ ${#SERVICIOS[@]} -eq 0 ] && SERVICIOS=(backend-orders:ms-pedidos backend-products:ms-productos backend-users:ms-usuarios
  backend-cart:ms-carrito backend-payments:ms-pagos backend-notifications:ms-notificaciones backend-rabbit-admin:ms-rabbit-admin
  backend-recomendaciones:ms-recomendaciones backend-administrador-kafka:ms-administrador-kafka)
for par in "${SERVICIOS[@]}"; do
  IMAGEN="$ECR/pedidos360/${par##*:}:latest"
  docker build -q -t "$IMAGEN" "${par%%:*}" && docker push -q "$IMAGEN"
done
