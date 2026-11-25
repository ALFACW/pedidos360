#!/usr/bin/env bash
# 2 EC2 (una por API Gateway): frontend + bastión en la subred pública (Elastic IP) y backend en la privada
# (microservicios, RabbitMQ y Kafka). Ambas con LabInstanceProfile (ECR y DynamoDB sin llaves en el código).
source "$(dirname "$0")/comun.sh"
AMI=$(aws ec2 describe-images --owners amazon --filters "Name=name,Values=al2023-ami-2023.*-x86_64" "Name=state,Values=available" \
  --query "sort_by(Images,&CreationDate)[-1].ImageId" --output text)

# User data de la guía "levantando kafka aws": Docker + Docker Compose V2
USER_DATA='#!/bin/bash
dnf update -y
dnf install -y git docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins/
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
'
SWAP='fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo "/swapfile swap swap defaults 0 0" >> /etc/fstab
'

crear_ec2() { # nombre tipo subred sg llave discoGB userdata
  [ "$(ec2_id "$1")" != "None" ] && return
  aws ec2 run-instances --image-id "$AMI" --instance-type "$2" --subnet-id "$(subred_id "$3")" \
    --security-group-ids "$(sg_id "$4")" --key-name "$5" --iam-instance-profile Name=LabInstanceProfile \
    --block-device-mappings "DeviceName=/dev/xvda,Ebs={VolumeSize=$6,VolumeType=gp3}" \
    --user-data "$7" --tag-specifications "$(etiqueta instance "$1")" --query "Instances[0].InstanceId" --output text >/dev/null
}
crear_ec2 pedidos360-frontend t3.small pedidos360-publica-a pedidos360-sg-frontend pedidos360-publica 20 "$USER_DATA"
crear_ec2 pedidos360-backend  t3.large pedidos360-privada-a pedidos360-sg-backend  pedidos360-private 30 "$USER_DATA$SWAP"
FRONT=$(ec2_id pedidos360-frontend); BACK=$(ec2_id pedidos360-backend)
aws ec2 wait instance-running --instance-ids "$FRONT" "$BACK"

EIP=$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=pedidos360-frontend-eip" --query "Addresses[0].AllocationId" --output text)
if [ "$EIP" = "None" ]; then
  EIP=$(aws ec2 allocate-address --domain vpc --tag-specifications "$(etiqueta elastic-ip pedidos360-frontend-eip)" --query AllocationId --output text)
fi
aws ec2 associate-address --allocation-id "$EIP" --instance-id "$FRONT" >/dev/null
echo "Frontend/bastión: $(aws ec2 describe-addresses --allocation-ids "$EIP" --query 'Addresses[0].PublicIp' --output text)"
echo "Backend privado:  $(aws ec2 describe-instances --instance-ids "$BACK" --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"
