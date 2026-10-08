#!/usr/bin/env bash
# Despliega el backend en la EC2 PRIVADA pasando por el bastión (EC2 pública).
#
# Uso (Git Bash, desde la raíz del repo):
#   bash scripts/desplegar-backend-privado.sh
#
# Equivalente manual (para la presentación):
#   scp -i pedidos360-publica.pem pedidos360-private.pem ec2-user@BASTION:~/.ssh/
#   ssh -i pedidos360-publica.pem ec2-user@BASTION
#   chmod 400 ~/.ssh/pedidos360-private.pem
#   ssh -i ~/.ssh/pedidos360-private.pem ec2-user@IP_PRIVADA
#   cd pedidos360 && docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d
set -euo pipefail

BASTION_IP="${BASTION_IP:-18.207.148.103}"
BACKEND_IP="${BACKEND_IP:-10.0.132.193}"
OLD_BACKEND_IP="${OLD_BACKEND_IP:-34.224.188.8}"
KEY_PUBLICA="${KEY_PUBLICA:-$HOME/Downloads/pedidos360-publica.pem}"
KEY_PRIVADA="${KEY_PRIVADA:-$HOME/Downloads/pedidos360-private.pem}"
KEY_OLD="${KEY_OLD:-$HOME/.ssh/llave-pedidos360.pem}"
ECR="468107274970.dkr.ecr.us-east-1.amazonaws.com"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "1) Tomando el .env de producción (RDS, Entra ID, CORS) del backend actual..."
scp -q -i "$KEY_OLD" "ec2-user@$OLD_BACKEND_IP:pedidos360/.env" "$WORK/.env"

echo "2) Agregando Gmail (desde el .env local) y la Erlang cookie del clúster RabbitMQ..."
while IFS= read -r line; do
  key="${line%%=*}"
  grep -v "^$key=" "$WORK/.env" > "$WORK/.env.tmp" || true
  echo "$line" >> "$WORK/.env.tmp"
  mv "$WORK/.env.tmp" "$WORK/.env"
done < <(grep -E '^MAIL_(HOST|PORT|USERNAME|PASSWORD|FROM|SMTP_AUTH|SMTP_STARTTLS)=' .env)
grep -q '^RABBITMQ_ERLANG_COOKIE=' "$WORK/.env" \
  || echo "RABBITMQ_ERLANG_COOKIE=$(openssl rand -hex 24)" >> "$WORK/.env"

echo "3) Copiando archivos y la llave privada al bastión ($BASTION_IP)..."
scp -q -i "$KEY_PUBLICA" "$KEY_PRIVADA" "ec2-user@$BASTION_IP:.ssh/pedidos360-private.pem"
scp -q -i "$KEY_PUBLICA" docker-compose.yml docker-compose.aws.yml docker/rabbitmq/cluster.conf "$WORK/.env" \
  "ec2-user@$BASTION_IP:"

echo "4) Desde el bastión: chmod 400 a la llave y salto al backend privado ($BACKEND_IP)..."
ssh -i "$KEY_PUBLICA" "ec2-user@$BASTION_IP" bash -s -- "$BACKEND_IP" "$ECR" <<'REMOTE'
set -euo pipefail
BACKEND_IP="$1"; ECR="$2"
chmod 400 ~/.ssh/pedidos360-private.pem
SSH_PRIV="ssh -i $HOME/.ssh/pedidos360-private.pem -o StrictHostKeyChecking=accept-new ec2-user@$BACKEND_IP"
scp -q -i ~/.ssh/pedidos360-private.pem -o StrictHostKeyChecking=accept-new \
  docker-compose.yml docker-compose.aws.yml .env "ec2-user@$BACKEND_IP:pedidos360/"
scp -q -i ~/.ssh/pedidos360-private.pem cluster.conf "ec2-user@$BACKEND_IP:pedidos360/docker/rabbitmq/"
rm -f .env   # el bastión no guarda secretos
$SSH_PRIV bash -s -- "$ECR" <<'BACKEND'
set -euo pipefail
cd ~/pedidos360 && chmod 600 .env
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$1"
docker compose -f docker-compose.yml -f docker-compose.aws.yml pull
docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d
docker compose -f docker-compose.yml -f docker-compose.aws.yml ps --format "table {{.Name}}\t{{.Status}}"
BACKEND
REMOTE

echo "Listo. Backend desplegado en la red privada."
