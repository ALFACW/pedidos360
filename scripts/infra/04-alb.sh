#!/usr/bin/env bash
# ALB interno (subredes privadas) que enruta /api/v1/<servicio> al puerto de cada microservicio en la EC2 backend,
# y VPC Link para que API Gateway llegue al ALB sin exponer el backend a Internet.
source "$(dirname "$0")/comun.sh"
VPC=$(vpc_id); BACK=$(ec2_id pedidos360-backend)
PRIVADAS="$(subred_id pedidos360-privada-a) $(subred_id pedidos360-privada-b)"

ALB=$(aws elbv2 describe-load-balancers --names pedidos360-alb-interno --query "LoadBalancers[0].LoadBalancerArn" --output text 2>/dev/null || echo None)
if [ "$ALB" = "None" ]; then
  ALB=$(aws elbv2 create-load-balancer --name pedidos360-alb-interno --scheme internal --type application \
    --subnets $PRIVADAS --security-groups "$(sg_id pedidos360-sg-alb)" --query "LoadBalancers[0].LoadBalancerArn" --output text)
fi
LISTENER=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB" --query "Listeners[?Port==\`80\`].ListenerArn | [0]" --output text)
if [ "$LISTENER" = "None" ]; then
  LISTENER=$(aws elbv2 create-listener --load-balancer-arn "$ALB" --protocol HTTP --port 80 \
    --default-actions 'Type=fixed-response,FixedResponseConfig={StatusCode=404,ContentType=text/plain,MessageBody=ruta no encontrada}' \
    --query "Listeners[0].ListenerArn" --output text)
fi

PRIORIDAD=10
# ruta puerto: un target group por microservicio, con health check en su /health
for par in orders:8080 products:8081 users:8082 cart:8083 rabbit:8084 pagos:8085 recomendaciones:8086 kafka:8087; do
  RUTA=${par%%:*}; PUERTO=${par##*:}
  TG=$(aws elbv2 describe-target-groups --names "pedidos360-$RUTA" --query "TargetGroups[0].TargetGroupArn" --output text 2>/dev/null || echo None)
  if [ "$TG" = "None" ]; then
    TG=$(aws elbv2 create-target-group --name "pedidos360-$RUTA" --protocol HTTP --port "$PUERTO" --vpc-id "$VPC" \
      --health-check-path "/api/v1/$RUTA/health" --query "TargetGroups[0].TargetGroupArn" --output text)
    aws elbv2 register-targets --target-group-arn "$TG" --targets "Id=$BACK,Port=$PUERTO"
    aws elbv2 create-rule --listener-arn "$LISTENER" --priority "$PRIORIDAD" \
      --conditions "Field=path-pattern,Values=/api/v1/$RUTA,/api/v1/$RUTA/*" \
      --actions "Type=forward,TargetGroupArn=$TG" >/dev/null
  fi
  PRIORIDAD=$((PRIORIDAD + 10))
done

LINK=$(aws apigatewayv2 get-vpc-links --query "Items[?Name=='pedidos360-vpclink'].VpcLinkId | [0]" --output text)
if [ "$LINK" = "None" ]; then
  LINK=$(aws apigatewayv2 create-vpc-link --name pedidos360-vpclink --subnet-ids $PRIVADAS \
    --security-group-ids "$(sg_id pedidos360-sg-vpclink)" --query VpcLinkId --output text)
fi
echo "ALB listo y VPC Link $LINK"
