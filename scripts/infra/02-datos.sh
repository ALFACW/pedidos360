#!/usr/bin/env bash
# Datos: RDS PostgreSQL en las subredes privadas, tabla DynamoDB con Stream (dashboard) y repositorios ECR.
# Uso: DB_PASSWORD=... bash scripts/infra/02-datos.sh
source "$(dirname "$0")/comun.sh"
DB_PASSWORD="${DB_PASSWORD:?Define DB_PASSWORD}"

if ! aws rds describe-db-subnet-groups --db-subnet-group-name pedidos360-db-subnets >/dev/null 2>&1; then
  aws rds create-db-subnet-group --db-subnet-group-name pedidos360-db-subnets --db-subnet-group-description "Subredes privadas" \
    --subnet-ids "$(subred_id pedidos360-privada-a)" "$(subred_id pedidos360-privada-b)" >/dev/null
fi
if ! aws rds describe-db-instances --db-instance-identifier pedidos360-db >/dev/null 2>&1; then
  aws rds create-db-instance --db-instance-identifier pedidos360-db --engine postgres --db-instance-class db.t3.micro \
    --allocated-storage 20 --db-name pedidos360db --master-username pedidos360 --master-user-password "$DB_PASSWORD" \
    --db-subnet-group-name pedidos360-db-subnets --vpc-security-group-ids "$(sg_id pedidos360-sg-rds)" \
    --no-publicly-accessible --backup-retention-period 0 --tags Key=Proyecto,Value=pedidos360 >/dev/null
fi

if ! aws dynamodb describe-table --table-name InteraccionesUsuario >/dev/null 2>&1; then
  aws dynamodb create-table --table-name InteraccionesUsuario --billing-mode PAY_PER_REQUEST \
    --attribute-definitions AttributeName=usuario,AttributeType=S AttributeName=fecha,AttributeType=N \
    --key-schema AttributeName=usuario,KeyType=HASH AttributeName=fecha,KeyType=RANGE \
    --stream-specification StreamEnabled=true,StreamViewType=NEW_IMAGE >/dev/null
fi

for repo in ms-pedidos ms-productos ms-usuarios ms-carrito ms-pagos ms-notificaciones ms-rabbit-admin \
            ms-recomendaciones ms-administrador-kafka interfaz-web; do
  aws ecr describe-repositories --repository-names "pedidos360/$repo" >/dev/null 2>&1 \
    || aws ecr create-repository --repository-name "pedidos360/$repo" >/dev/null
done

echo "Esperando RDS (5-10 min)..."
aws rds wait db-instance-available --db-instance-identifier pedidos360-db
echo "RDS: $(aws rds describe-db-instances --db-instance-identifier pedidos360-db --query 'DBInstances[0].Endpoint.Address' --output text)"
