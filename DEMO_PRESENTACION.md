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

## 2. docker compose levantando RabbitMQ en la nube (en la EC2 privada)

```bash
cd ~/pedidos360
alias dc='docker compose -f docker-compose.yml -f docker-compose.aws.yml'
dc ps --format "table {{.Name}}\t{{.Status}}"      # 7 microservicios + rabbitmq1 + rabbitmq2 (healthy)
```

Ejecutar `docker compose` en vivo: se baja el nodo 2 y se vuelve a levantar con `up`. El nodo se reincorpora solo al clúster (`cluster.conf`, peer discovery `classic_config`):

```bash
dc stop rabbitmq2
docker exec pedidos360-backend-rabbitmq1-1 rabbitmqctl cluster_status   # Running Nodes: solo rabbit@rabbitmq1
dc up -d rabbitmq1 rabbitmq2                                            # "Started" para rabbitmq2
dc ps rabbitmq1 rabbitmq2                                               # esperar (healthy)
docker exec pedidos360-backend-rabbitmq1-1 rabbitmqctl cluster_status   # Running Nodes: rabbit@rabbitmq1, rabbit@rabbitmq2
```

Explicar: ambos nodos comparten la Erlang cookie (`RABBITMQ_ERLANG_COOKIE` del `.env`) y los microservicios se conectan con `RABBITMQ_ADDRESSES=rabbitmq1:5672,rabbitmq2:5672`; si un nodo cae, el cliente se conecta al otro.

## 3. Abrir la consola de RabbitMQ (en tu PC, otra terminal)

```bash
cd ~/Downloads
ssh -i pedidos360-private.pem -L 18672:localhost:15672 \
    -o "ProxyCommand=ssh -i pedidos360-publica.pem -W %h:%p ec2-user@44.221.145.83" \
    ec2-user@10.0.132.193
```

Abrir <http://localhost:18672> (usuario y contraseña: `RABBITMQ_USER` / `RABBITMQ_PASSWORD` del `.env`). Para la consola del nodo 2 usar `-L 18673:localhost:15673`.

Qué mostrar:

- **Overview → Nodes**: `rabbit@rabbitmq1` y `rabbit@rabbitmq2` en verde (clúster de 2 nodos). Arriba a la derecha aparece el nombre del clúster; las dos consolas (18672 y 18673) muestran los mismos nodos, colas y exchanges.
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

## 7. API Manager en la consola de AWS

API Gateway → APIs:

- **`pedidos360-api-v1`** (backend) y **`pedidos360-frontend`** (frontend): dos APIs HTTP separadas, etapa `desarrollo` desplegada.
- **Routes** de `pedidos360-api-v1`: **una ruta por método y endpoint**, sin `ANY` ni `{proxy+}` (30 rutas: `GET /api/v1/products`, `POST /api/v1/products`, `PUT /api/v1/products/{id}`, `DELETE /api/v1/products/{id}`, …). Coinciden con los `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping` de los controladores. Las rutas protegidas muestran el candado **JWT**; `/health` y `/public` no.
- **Integrations**: cada ruta tiene su integración propia → **VPC Link** `pedidos360-vpclink` → ALB interno (el backend no tiene IP pública).
- **Authorization**: autorizador `entra-id-jwt` (issuer `https://login.microsoftonline.com/<tenant>/v2.0`, audience = Application ID del API).
- **CORS**: origen permitido solo el frontend; métodos `GET, POST, PUT, DELETE, OPTIONS`.
- **Routes** de `pedidos360-frontend`: solo `GET /` y `GET /{proxy+}` (sitio estático; `{proxy+}` es necesario porque los archivos JS/CSS tienen un hash en el nombre que cambia en cada build).

Las rutas se crean con `scripts/crear-api-v1.sh` y `scripts/crear-api-frontend.sh`.

## 8. IDaaS: tenant y usuarios en Entra ID

Portal de Azure → Microsoft Entra ID:

- **Overview**: nombre del tenant y Tenant ID (el mismo del issuer del autorizador).
- **Users**: usuarios registrados (Admin y Cliente).
- **App registrations**: la app registrada (SPA con la Redirect URI del frontend; *Expose an API* → scope `read-wirte`) y sus **App roles**; el rol Admin se asigna en *Enterprise applications → Users and groups*.
- En el frontend, el botón **"🔑 Ver Token JWT"** muestra el access token y sus claims decodificados (`aud`, `iss`, correo). El login es OAuth 2.0 / OpenID Connect con MSAL (Authorization Code + PKCE).

## 9. API Gateway valida el JWT

```bash
curl -i https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products                      # 401
curl -i -H "Authorization: Bearer abc.def.ghi" https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products   # 401
curl -i https://fkiduebsb9.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/products/health              # 200 (ruta pública)
```

Con el token del botón "🔑 Ver Token JWT" del frontend (Postman, `Authorization: Bearer <token>`) responde 200.

## 10. Microservicio administrador de RabbitMQ (Postman, usuario Admin)

```http
POST {{api}}/api/v1/rabbit/exchanges   {"name": "demo.direct", "type": "direct"}
POST {{api}}/api/v1/rabbit/queues      {"name": "demo.queue", "deadLetterExchange": "demo.direct", "deadLetterRoutingKey": "demo.dead"}
POST {{api}}/api/v1/rabbit/bindings    {"queue": "demo.queue", "exchange": "demo.direct", "routingKey": "demo"}
DELETE {{api}}/api/v1/rabbit/queues/demo.queue
POST {{api}}/api/v1/rabbit/queues      {"name": ""}                 -> 400 con el detalle del error
DELETE {{api}}/api/v1/rabbit/queues/pedidos360.orders.payment-results.queue -> 409 (protegida)
```

---

## Checklist EP4 → paso de la demo

| Requisito de la pauta | Paso |
|---|---|
| Instancia del API Manager en funcionamiento | 7 |
| Configuración del API Manager que llama a los endpoints del backend | 7 |
| Frontend consume los endpoints a través del API Manager | 4 |
| API Manager valida JWT (rechaza inválidos, acepta válidos) | 9 |
| Tenant en IDaaS y usuarios registrados | 8 |
| Frontend usa OAuth 2.0 / OIDC para obtener un JWT válido | 4 y 8 |
| Backend y frontend desplegados, activos e integrados en la nube | 1, 2 y 4 |
| Dos nodos RabbitMQ en clúster | 2 y 3 |
| Tres colas con su DLQ funcionando | 3, 5 y 6 |
| Exchanges direct y topic | 3 |
| docker-compose levantando RabbitMQ en la nube | 2 |
