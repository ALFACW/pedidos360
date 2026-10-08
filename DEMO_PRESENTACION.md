# Demo Pedidos360: guía para la presentación

## Arquitectura

```
Navegador ──► API Gateway pedidos360-frontend (etapa "desarrollo") ──► EC2 PÚBLICA  44.221.145.83  (Caddy: frontend + bastión SSH)
    │                     │ ssh con pedidos360-private.pem (chmod 400)
    │                     ▼
    └─► API Gateway pedidos360-api-v1 (etapa "desarrollo", JWT Entra ID, CORS solo frontend)
          │ 30 rutas, una integración por ruta/método (Método A de la guía)
          ▼ VPC Link
        ALB interno ──/api/v1/<servicio>──► EC2 PRIVADA 10.0.132.193 (sin IP pública)
                                              docker compose: 7 microservicios + RabbitMQ (2 nodos)
                                              salida a Internet por NAT Gateway (ECR, Gmail, Entra ID)
                                              RDS PostgreSQL por VPC peering
```

- Frontend (API Gateway del frontend, etapa desarrollo): <https://e3fc0mjbec.execute-api.us-east-1.amazonaws.com/desarrollo/>
- Frontend (acceso directo HTTPS): <https://44-221-145-83.nip.io>
- API: `https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/...`

Las llaves están en `Downloads`: `pedidos360-publica.pem` (bastión) y `pedidos360-private.pem` (backend). La primera vez tras el cambio de IP ejecuta `ssh-keygen -R 44.221.145.83` para olvidar la huella del servidor antiguo.

---

## 1. Entrar al backend privado a través del bastión

En Git Bash, en `Downloads`:

```bash
ssh -i pedidos360-publica.pem ec2-user@44.221.145.83          # 1) entrar a la EC2 pública
ls -l ~/.ssh/pedidos360-private.pem                             # la llave privada está copiada con permisos 400 (-r--------)
ssh -i ~/.ssh/pedidos360-private.pem ec2-user@10.0.132.193      # 2) saltar a la EC2 privada
```

Mostrar que el backend NO es accesible directamente (desde tu PC falla por timeout):

```bash
ssh -i pedidos360-private.pem ec2-user@10.0.132.193            # Connection timed out
```

## 2. Ver los contenedores encendidos (en la EC2 privada)

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
cd ~/pedidos360 && docker compose -f docker-compose.yml -f docker-compose.aws.yml ps
```

## 3. Abrir la consola de RabbitMQ (en tu PC, otra terminal)

```bash
cd ~/Downloads
ssh -i pedidos360-private.pem -L 18672:localhost:15672 \
    -o "ProxyCommand=ssh -i pedidos360-publica.pem -W %h:%p ec2-user@44.221.145.83" \
    ec2-user@10.0.132.193
```

Abrir <http://localhost:18672> (usuario y contraseña: `RABBITMQ_USER` / `RABBITMQ_PASSWORD` del `.env`). Para la consola del nodo 2 usar `-L 18673:localhost:15673`.

Qué mostrar:

- **Overview → Nodes**: `rabbit@rabbitmq1` y `rabbit@rabbitmq2` (clúster de 2 nodos).
- **Exchanges**: `pedidos360.events` (**topic**) y `pedidos360.dlx` (**direct**).
- **Queues**: 3 colas con su DLQ (`.queue` y `.dead` de orders, payments y notifications).
- **Connections**: `ms-pedidos`, `ms-pagos`, `ms-notificaciones` (y `ms-rabbit-admin` después de su primera petición), en lugar de solo IP:puerto.

## 4. Compra normal

1. Entrar a <https://e3fc0mjbec.execute-api.us-east-1.amazonaws.com/desarrollo/> e iniciar sesión con Entra ID.
2. Agregar productos al carrito y comprar.
3. En RabbitMQ → Queues se ve el movimiento de mensajes (gráficos *Message rates*).
4. Llega el correo "Pedido PED-…: pago aprobado" al correo del usuario.

## 5. Correo en cola con notificaciones detenido

En la EC2 privada:

```bash
docker stop pedidos360-backend-ms-notificaciones-1
docker ps -a --filter name=notificaciones          # Exited
```

1. Hacer otra compra en el frontend (el pedido y el pago funcionan igual: el sistema está desacoplado).
2. En RabbitMQ → Queues → `pedidos360.notifications.payment-results.queue`: **Ready = 1** y **Consumers = 0**. El mensaje espera en la cola (cola durable, mensaje persistente).
3. Volver a encender el consumidor:

```bash
docker start pedidos360-backend-ms-notificaciones-1
docker logs -f pedidos360-backend-ms-notificaciones-1   # "ACK cola=... " y "Payment notification email sent"
```

4. La cola vuelve a **Ready = 0** y llega el correo que estaba pendiente.

## 6. DLQ (mensaje inválido)

En RabbitMQ → Exchanges → `pedidos360.events` → *Publish message*, routing key `payment.approved`, payload `{"eventType":"PAYMENT_APPROVED"}` (sin `eventId`). El consumidor lo rechaza con NACK y aparece en `pedidos360.notifications.payment-results.dead` y `pedidos360.orders.payment-results.dead`. En los logs: `NACK -> DLQ (mensaje inválido)`.

## 7. API Gateway valida el JWT

```bash
curl -i https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products                      # 401
curl -i -H "Authorization: Bearer abc.def.ghi" https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products   # 401
curl -i https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products/health              # 200 (ruta pública)
```

Con el token del botón "Ver token" del frontend (Postman, `Authorization: Bearer <token>`) responde 200.

## 8. Microservicio administrador de RabbitMQ (Postman, usuario Admin)

```http
POST {{api}}/api/v1/rabbit/exchanges   {"name": "demo.direct", "type": "direct"}
POST {{api}}/api/v1/rabbit/queues      {"name": "demo.queue", "deadLetterExchange": "demo.direct", "deadLetterRoutingKey": "demo.dead"}
POST {{api}}/api/v1/rabbit/bindings    {"queue": "demo.queue", "exchange": "demo.direct", "routingKey": "demo"}
DELETE {{api}}/api/v1/rabbit/queues/demo.queue
POST {{api}}/api/v1/rabbit/queues      {"name": ""}                 -> 400 con el detalle del error
DELETE {{api}}/api/v1/rabbit/queues/pedidos360.orders.payment-results.queue -> 409 (protegida)
```
