# Funciones compartidas por los scripts de infraestructura. Cada recurso se busca por su etiqueta Name.
set -euo pipefail
export MSYS_NO_PATHCONV=1
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=""

etiqueta() { echo "ResourceType=$1,Tags=[{Key=Name,Value=$2},{Key=Proyecto,Value=pedidos360}]"; }

vpc_id()    { aws ec2 describe-vpcs --filters "Name=tag:Name,Values=pedidos360-vpc" --query "Vpcs[0].VpcId" --output text; }
subred_id() { aws ec2 describe-subnets --filters "Name=tag:Name,Values=$1" --query "Subnets[0].SubnetId" --output text; }
sg_id()     { aws ec2 describe-security-groups --filters "Name=group-name,Values=$1" --query "SecurityGroups[0].GroupId" --output text; }
ec2_id()    { aws ec2 describe-instances --filters "Name=tag:Name,Values=$1" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query "Reservations[0].Instances[0].InstanceId" --output text; }
