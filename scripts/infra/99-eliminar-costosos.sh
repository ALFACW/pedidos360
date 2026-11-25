#!/usr/bin/env bash
# Elimina lo que cobra aunque el laboratorio esté apagado (NAT Gateway, ALB, VPC Link y RDS) y detiene las EC2.
# Ejecutar SOLO después de la presentación. Se recrea con 01-red.sh, 02-datos.sh y 04-alb.sh.
source "$(dirname "$0")/comun.sh"
read -r -p "Esto elimina NAT, ALB, VPC Link y RDS (con sus datos). Escribe 'eliminar' para continuar: " OK
[ "$OK" = "eliminar" ] || exit 1

aws ec2 stop-instances --instance-ids "$(ec2_id pedidos360-frontend)" "$(ec2_id pedidos360-backend)" >/dev/null
LINK=$(aws apigatewayv2 get-vpc-links --query "Items[?Name=='pedidos360-vpclink'].VpcLinkId | [0]" --output text)
[ "$LINK" != "None" ] && aws apigatewayv2 delete-vpc-link --vpc-link-id "$LINK"
ALB=$(aws elbv2 describe-load-balancers --names pedidos360-alb-interno --query "LoadBalancers[0].LoadBalancerArn" --output text 2>/dev/null || echo None)
[ "$ALB" != "None" ] && aws elbv2 delete-load-balancer --load-balancer-arn "$ALB"
aws rds delete-db-instance --db-instance-identifier pedidos360-db --skip-final-snapshot >/dev/null 2>&1 || true
NAT=$(aws ec2 describe-nat-gateways --filter "Name=tag:Name,Values=pedidos360-nat" "Name=state,Values=available" --query "NatGateways[0].NatGatewayId" --output text)
if [ "$NAT" != "None" ]; then
  aws ec2 delete-nat-gateway --nat-gateway-id "$NAT" >/dev/null
  aws ec2 wait nat-gateway-deleted --nat-gateway-ids "$NAT"
  aws ec2 release-address --allocation-id "$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=pedidos360-nat-eip" --query "Addresses[0].AllocationId" --output text)"
fi
echo "Listo: quedan la VPC, las EC2 detenidas, ECR, DynamoDB y API Gateway (costo casi cero)."
