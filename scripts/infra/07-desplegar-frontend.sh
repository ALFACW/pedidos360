#!/usr/bin/env bash
# Construye el frontend (Nginx, guía "Desplegando mi frontend en AWS"), lo sube a ECR y lo ejecuta en la EC2 frontend.
source "$(dirname "$0")/comun.sh"
LLAVES="${LLAVES:-$HOME/Downloads}"
ECR="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com"
IMAGEN="$ECR/pedidos360/interfaz-web:latest"
FRONTEND=$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=pedidos360-frontend-eip" --query "Addresses[0].PublicIp" --output text)

aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR"
docker build -q -t "$IMAGEN" frontend && docker push -q "$IMAGEN"
ssh -i "$LLAVES/pedidos360-publica.pem" "ec2-user@$FRONTEND" bash -s -- "$ECR" "$IMAGEN" <<'REMOTO'
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$1"
docker pull -q "$2"
docker rm -f interfaz-web 2>/dev/null
docker run -d --name interfaz-web --restart unless-stopped -p 80:80 "$2"
REMOTO
