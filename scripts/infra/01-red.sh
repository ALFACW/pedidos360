#!/usr/bin/env bash
# Red: VPC 10.0.0.0/16 con 1 subred pública (EC2 frontend + bastión, NAT) y 2 privadas (EC2 backend, RDS, ALB interno)
# y los security groups que solo permiten el tráfico necesario entre capas.
# Uso: MI_IP=$(curl -s https://checkip.amazonaws.com) bash scripts/infra/01-red.sh
source "$(dirname "$0")/comun.sh"
MI_IP="${MI_IP:?Define MI_IP con tu IP pública (SSH al bastión)}"

VPC=$(vpc_id)
if [ "$VPC" = "None" ]; then
  VPC=$(aws ec2 create-vpc --cidr-block 10.0.0.0/16 --tag-specifications "$(etiqueta vpc pedidos360-vpc)" --query Vpc.VpcId --output text)
  aws ec2 modify-vpc-attribute --vpc-id "$VPC" --enable-dns-hostnames
fi
echo "VPC $VPC"

crear_subred() { # nombre cidr zona
  [ "$(subred_id "$1")" != "None" ] && return
  aws ec2 create-subnet --vpc-id "$VPC" --cidr-block "$2" --availability-zone "$3" \
    --tag-specifications "$(etiqueta subnet "$1")" --query Subnet.SubnetId --output text >/dev/null
}
crear_subred pedidos360-publica-a 10.0.1.0/24 us-east-1a
crear_subred pedidos360-privada-a 10.0.11.0/24 us-east-1a
crear_subred pedidos360-privada-b 10.0.12.0/24 us-east-1b
PUBLICA=$(subred_id pedidos360-publica-a)
aws ec2 modify-subnet-attribute --subnet-id "$PUBLICA" --map-public-ip-on-launch

IGW=$(aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=$VPC" --query "InternetGateways[0].InternetGatewayId" --output text)
if [ "$IGW" = "None" ]; then
  IGW=$(aws ec2 create-internet-gateway --tag-specifications "$(etiqueta internet-gateway pedidos360-igw)" --query InternetGateway.InternetGatewayId --output text)
  aws ec2 attach-internet-gateway --internet-gateway-id "$IGW" --vpc-id "$VPC"
fi

NAT=$(aws ec2 describe-nat-gateways --filter "Name=tag:Name,Values=pedidos360-nat" "Name=state,Values=pending,available" --query "NatGateways[0].NatGatewayId" --output text)
if [ "$NAT" = "None" ]; then
  EIP=$(aws ec2 allocate-address --domain vpc --tag-specifications "$(etiqueta elastic-ip pedidos360-nat-eip)" --query AllocationId --output text)
  NAT=$(aws ec2 create-nat-gateway --subnet-id "$PUBLICA" --allocation-id "$EIP" --tag-specifications "$(etiqueta natgateway pedidos360-nat)" --query NatGateway.NatGatewayId --output text)
fi
echo "Esperando NAT $NAT..."
aws ec2 wait nat-gateway-available --nat-gateway-ids "$NAT"

tabla_rutas() { # nombre destino(igw|nat) subredes...
  local nombre=$1 destino=$2; shift 2
  local RT=$(aws ec2 describe-route-tables --filters "Name=tag:Name,Values=$nombre" --query "RouteTables[0].RouteTableId" --output text)
  if [ "$RT" = "None" ]; then
    RT=$(aws ec2 create-route-table --vpc-id "$VPC" --tag-specifications "$(etiqueta route-table "$nombre")" --query RouteTable.RouteTableId --output text)
    if [[ $destino == igw-* ]]; then
      aws ec2 create-route --route-table-id "$RT" --destination-cidr-block 0.0.0.0/0 --gateway-id "$destino" >/dev/null
    else
      aws ec2 create-route --route-table-id "$RT" --destination-cidr-block 0.0.0.0/0 --nat-gateway-id "$destino" >/dev/null
    fi
    for s in "$@"; do aws ec2 associate-route-table --route-table-id "$RT" --subnet-id "$(subred_id "$s")" >/dev/null; done
  fi
}
tabla_rutas pedidos360-rt-publica "$IGW" pedidos360-publica-a
tabla_rutas pedidos360-rt-privada "$NAT" pedidos360-privada-a pedidos360-privada-b

crear_sg() { # nombre descripción
  [ "$(sg_id "$1")" != "None" ] && return
  aws ec2 create-security-group --vpc-id "$VPC" --group-name "$1" --description "$2" \
    --tag-specifications "$(etiqueta security-group "$1")" --query GroupId --output text >/dev/null
}
permitir() { # sg puertoDesde puertoHasta origen(cidr|sg)
  local origen=$([[ $4 == sg-* ]] && echo "UserIdGroupPairs=[{GroupId=$4}]" || echo "IpRanges=[{CidrIp=$4}]")
  aws ec2 authorize-security-group-ingress --group-id "$1" \
    --ip-permissions "IpProtocol=tcp,FromPort=$2,ToPort=$3,$origen" >/dev/null 2>&1 || true
}
crear_sg pedidos360-sg-frontend "Frontend y bastion"
crear_sg pedidos360-sg-vpclink  "VPC Link de API Gateway"
crear_sg pedidos360-sg-alb      "ALB interno"
crear_sg pedidos360-sg-backend  "Microservicios, RabbitMQ y Kafka"
crear_sg pedidos360-sg-rds      "PostgreSQL"
SG_FRONT=$(sg_id pedidos360-sg-frontend); SG_LINK=$(sg_id pedidos360-sg-vpclink); SG_ALB=$(sg_id pedidos360-sg-alb)
SG_BACK=$(sg_id pedidos360-sg-backend); SG_RDS=$(sg_id pedidos360-sg-rds)
permitir "$SG_FRONT" 80 80 0.0.0.0/0
permitir "$SG_FRONT" 22 22 "$MI_IP/32"
permitir "$SG_ALB" 80 80 "$SG_LINK"
permitir "$SG_BACK" 8080 8087 "$SG_ALB"
permitir "$SG_BACK" 22 22 "$SG_FRONT"
permitir "$SG_RDS" 5432 5432 "$SG_BACK"
echo "Red lista: publica=$PUBLICA privadas=$(subred_id pedidos360-privada-a),$(subred_id pedidos360-privada-b)"
