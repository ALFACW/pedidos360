# Demo Pedidos360: guía para la presentación (Evaluación 3)

## Arquitectura (cuenta AWS 486702306352, us-east-1)

```
Navegador ─► API Gateway pedidos360-frontend (GET / y GET /assets/{archivo}) ─► EC2 frontend + bastión 34.237.227.232 (Nginx)
    │
    ├─► API Gateway pedidos360-api-v1 (44 rutas, una por método; JWT Entra ID; CORS solo la página)
    │      └─► VPC Link ─► ALB interno ─/api/v1/<servicio>─► EC2 backend privada 10.0.11.221
    │             docker-compose.yml:       9 microservicios + RabbitMQ (2 nodos)
    │             docker-compose.kafka.yml: 3 Zookeeper + 3 brokers + Kafka UI
    │             salida por NAT (ECR, Entra ID, Gmail, Transbank, DynamoDB) · RDS PostgreSQL privado
    │
    └─► API WebSocket pedidos360-dashboard ◄─ Lambda stream ◄─ DynamoDB Stream (InteraccionesUsuario)
```

| | URL |
|---|---|
| Frontend | <https://ahdle7i9n6.execute-api.us-east-1.amazonaws.com/desarrollo/> |
| API | `https://t13j9mbqg7.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/...` |
| WebSocket | `wss://hn5bznc2cc.execute-api.us-east-1.amazonaws.com/desarrollo` |
| GitHub | <https://github.com/ALFACW/pedidos360> |

Llaves en `Downloads`: `pedidos360-publica.pem` (bastión) y `pedidos360-private.pem` (backend). Toda la infraestructura se crea con `scripts/infra/01..08` (replicable).

---

## 1. Backend privado y docker compose en la nube

```bash
cd ~/Downloads
ssh -i pedidos360-publica.pem ec2-user@34.237.227.232               # bastión (subred pública)
ssh -i ~/.ssh/pedidos360-private.pem ec2-user@10.0.11.221           # backend (sin IP pública)
cd ~/pedidos360
docker compose -f docker-compose.kafka.yml ps                       # 3 zookeeper + 3 kafka + kafka-ui
docker compose -f docker-compose.yml -f docker-compose.aws.yml ps   # microservicios + rabbitmq1/2
```

Levantar en vivo (se reincorporan solos al clúster):

```bash
docker compose -f docker-compose.kafka.yml stop kafka3
docker compose -f docker-compose.kafka.yml up -d kafka3
docker exec pedidos360-backend-rabbitmq1-1 rabbitmqctl cluster_status   # rabbit@rabbitmq1 y rabbit@rabbitmq2
```

## 2. Consolas (túneles SSH, en otra terminal de tu PC)

```bash
cd ~/Downloads
ssh -i pedidos360-private.pem -L 18672:localhost:15672 -L 18090:localhost:8090 \
    -o "ProxyCommand=ssh -i pedidos360-publica.pem -W %h:%p ec2-user@34.237.227.232" ec2-user@10.0.11.221
```

- RabbitMQ <http://localhost:18672>: 2 nodos, exchanges `pedidos360.events` (topic) y `pedidos360.dlx` (direct), 3 colas con su DLQ.
- Kafka UI <http://localhost:18090>:
  - **Brokers**: 3 brokers.
  - **Topics**: `pedidos360.intentos-pago` y `pedidos360.interacciones-usuario`, cada uno con **3 particiones y 3 réplicas** (líder distinto por partición, ISR 3) y sus `.DLT`.
  - **Messages**: los eventos JSON con su clave, partición y offset.
  - **Consumers**: `deteccion-fraude` y `recomendaciones` con offsets y **lag**.

## 3. Compra con WebPay (+5)

1. Iniciar sesión con Entra ID en el frontend. Clic en el nombre de un producto (detalle) y "+ Añadir".
2. Carrito → "Confirmar y Pagar con WebPay" → tarjeta `4051885600446623` → WebPay de integración (CVV 123, RUT 11.111.111-1, clave 123, Aceptar).
3. WebPay vuelve a la página, `ms-pagos` confirma (commit) y publica `payment.approved` en RabbitMQ → pedido CONFIRMADO, stock descontado y correo enviado.

## 4. Antifraude: misma tarjeta más de 3 veces en 10 s

Con el token del botón "🔑 Ver Token JWT" y el id de un pedido pendiente:

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API/api/v1/pagos/iniciar" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"pedidoId": <id>, "numeroTarjeta": "4051885600446623"}'
  sleep 1
done          # 200 200 200 200 429
```

En `docker logs pedidos360-backend-ms-pagos-1`: los 4 intentos en la **misma partición** (clave = tarjeta) y `ALERTA FRAUDE ... Bloqueada hasta`. La alerta queda en la tabla `alertas_fraude`. Si el pago ya estaba en WebPay, la confirmación se rechaza.

## 5. Recomendaciones y dashboard en tiempo real (+2 DynamoDB, +3 WebSocket)

- Ver y agregar productos de una categoría → "✨ Recomendado para ti" muestra productos parecidos (misma categoría, más populares, no agregados aún).
- Consola DynamoDB → tabla `InteraccionesUsuario` (partición usuario, orden fecha).
- Pestaña "📊 Dashboard" (Admin), en otra ventana: cada vista o agregado actualiza al instante volumen de interacciones, productos más vistos y categorías más activas.

## 6. Manejo de errores en Kafka

Publicar un mensaje inválido en Kafka UI (Topics → Produce Message), por ejemplo `{"accion":"BORRAR"}`: el consumidor no se bloquea; tras validar (`@Valid`) se envía a `pedidos360.interacciones-usuario.DLT` con el motivo en los headers. Errores transitorios: 3 reintentos (1 s, 2 s, 4 s) antes de la DLT.

## 7. Microservicio administrador de Kafka (Postman, usuario Admin)

```http
GET    {{api}}/api/v1/kafka/topicos
POST   {{api}}/api/v1/kafka/topicos   {"nombre":"demo.topico","particiones":3,"replicas":3,"retencionMs":3600000,"cleanupPolicy":"delete"}
PUT    {{api}}/api/v1/kafka/topicos/demo.topico   {"particiones":6,"retencionMs":7200000}
GET    {{api}}/api/v1/kafka/topicos/demo.topico   (líder, réplicas, ISR, configuración)
GET    {{api}}/api/v1/kafka/grupos/deteccion-fraude   (offset confirmado, final y lag por partición)
DELETE {{api}}/api/v1/kafka/topicos/demo.topico
POST   {{api}}/api/v1/kafka/topicos   {"nombre":"","particiones":0}   -> 400
```

## 8. API Gateway, JWT e IDaaS (evaluaciones anteriores)

- Consola API Gateway: `pedidos360-api-v1` con 44 rutas (una por método, sin ANY), autorizador `entra-id-jwt`, CORS solo `https://ahdle7i9n6.execute-api.us-east-1.amazonaws.com`, integraciones por VPC Link. `pedidos360-frontend` con solo 2 rutas GET.
- `curl -i $API/api/v1/products` → 401; con token falso → 401; `/api/v1/products/health` → 200.
- Entra ID: tenant, usuarios, App roles Admin/Cliente.

---

## ¿Las colas RabbitMQ se mantienen o migran a Kafka?

**Se mantienen en RabbitMQ** pedido → pago → notificación: son *tareas* que procesa un solo consumidor una vez y se eliminan al confirmarse (ACK), con reintentos, DLQ y ruteo por routing key. Migrarlas a Kafka no aporta: no se necesita releerlas ni que varios servicios las consuman.

**Kafka se agrega** para los flujos de *eventos* de análisis:

| Tópico | Clave | Retención | Consumer group | Por qué |
|---|---|---|---|---|
| `pedidos360.intentos-pago` | tarjeta | 1 día | `deteccion-fraude` (ms-pagos) | Misma tarjeta → misma partición → orden garantizado para la ventana de 10 s. Solo sirve en tiempo real. |
| `pedidos360.interacciones-usuario` | usuario | 7 días | `recomendaciones` (ms-recomendaciones) | Alto volumen (clickstream); se puede releer para recalcular recomendaciones; otro servicio podría consumirlo con otro group sin afectar. |

- **3 particiones**: hasta 3 consumidores del mismo group en paralelo.
- **3 réplicas con `min.insync.replicas=2` y `acks=all`**: un mensaje se confirma cuando lo tienen 2 brokers, así que se tolera la caída de 1 broker sin perder datos.
- **3 Zookeeper**: mantienen metadatos y elección de controlador; con mayoría 2 de 3, el clúster sigue si cae uno.
- **Consumidor caído**: Kafka conserva los mensajes (retención) y el group guarda su offset; al volver procesa desde donde quedó (el lag baja a 0).

## Checklist EP6

| Requisito | Paso |
|---|---|
| API Manager funcionando y configurado (rutas, integración) | 8 |
| Frontend consume endpoints vía API Manager | 3, 5 |
| API Manager valida JWT | 8 |
| Tenant IDaaS y usuarios; login OAuth 2.0 / OIDC | 3, 8 |
| Backend y frontend desplegados e integrados | 1, 3 |
| 2 nodos RabbitMQ en clúster, 3 colas con DLQ, exchanges direct y topic | 1, 2 |
| docker-compose en la nube levantando RabbitMQ o Kafka | 1 |
| Kafka UI: 2 tópicos con 3 particiones y 3 réplicas | 2 |
| Arquitectura Kafka (3 Zookeeper, 3 brokers, tolerancia a fallos) | 1, explicación final |
| Flujo productor → tópico → consumidor | 3, 4, 5 |
| Reacción en tiempo real | 4, 5 |
| Offsets, lag, consumer groups, retención | 2, 7 |
