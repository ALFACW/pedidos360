#!/usr/bin/env bash
# Despliega en la EC2 backend (privada) pasando por el bastión: Kafka (docker-compose.kafka.yml) y
# microservicios + RabbitMQ (docker-compose.yml + docker-compose.aws.yml con imágenes de ECR).
# Uso: bash scripts/infra/06-desplegar-backend.sh   (desde la raíz del repo, con .env local)
source "$(dirname "$0")/comun.sh"
LLAVES="${LLAVES:-$HOME/Downloads}"
BASTION=$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=pedidos360-frontend-eip" --query "Addresses[0].PublicIp" --output text)
BACKEND=$(aws ec2 describe-instances --instance-ids "$(ec2_id pedidos360-backend)" --query "Reservations[0].Instances[0].PrivateIpAddress" --output text)
RDS=$(aws rds describe-db-instances --db-instance-identifier pedidos360-db --query "DBInstances[0].Endpoint.Address" --output text)
FRONT_API=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-frontend'].ApiId | [0]" --output text)
ECR="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-east-1.amazonaws.com"
SSH="ssh -o StrictHostKeyChecking=accept-new -i $LLAVES/pedidos360-private.pem -o ProxyCommand=\"ssh -i $LLAVES/pedidos360-publica.pem -W %h:%p ec2-user@$BASTION\" ec2-user@$BACKEND"

ENV_SERVIDOR=$(mktemp)
trap 'rm -f "$ENV_SERVIDOR"' EXIT
grep -E '^(RABBITMQ_|MAIL_|JWT_AUDIENCE|ADMIN_EMAILS|DB_PASSWORD)' .env > "$ENV_SERVIDOR"
cat >> "$ENV_SERVIDOR" <<EOF
DB_URL=jdbc:postgresql://$RDS:5432/pedidos360db?sslmode=require
DB_USERNAME=pedidos360
FRONTEND_ORIGIN=https://$FRONT_API.execute-api.us-east-1.amazonaws.com
FRONTEND_URL=https://$FRONT_API.execute-api.us-east-1.amazonaws.com/desarrollo/
KAFKA_HOST_IP=$BACKEND
KAFKA_BOOTSTRAP_SERVERS=$BACKEND:9092,$BACKEND:9093,$BACKEND:9094
EOF

tar -cz docker-compose.yml docker-compose.aws.yml docker-compose.kafka.yml docker/rabbitmq/cluster.conf database/schema.sql \
  | eval "$SSH" "'mkdir -p pedidos360 && tar -xz -C pedidos360'"
eval "$SSH" "'cat > pedidos360/.env && chmod 600 pedidos360/.env'" < "$ENV_SERVIDOR"

eval "$SSH" bash -s -- "$ECR" "$RDS" <<'REMOTO'
set -euo pipefail
cd ~/pedidos360
set -a; source .env; set +a
export PGPASSWORD="$DB_PASSWORD"
PSQL="docker run --rm -i -e PGPASSWORD postgres:16-alpine psql -h $2 -U pedidos360 -d pedidos360db -v ON_ERROR_STOP=1"
if [ "$($PSQL -tAc "SELECT to_regclass('public.products') IS NULL" </dev/null)" = "t" ]; then $PSQL < database/schema.sql; fi
docker compose -f docker-compose.kafka.yml up -d
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$1"
docker compose -f docker-compose.yml -f docker-compose.aws.yml pull -q
docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d
docker ps --format "table {{.Names}}\t{{.Status}}"
REMOTO
