# Pedidos360: guion de grabación (Evaluación 3)

Recorrido de 10 escenas, unos 12 minutos, sin mostrar código. Cada escena dice **qué abrir**, **qué hacer** y el **subtítulo** (el mismo texto está en `docs/subtitulos-demo.srt`).

## Antes de grabar

1. AWS Academy: **Start Lab** 20 min antes y renovar `C:\Users\Diego\.aws\credentials`.
2. Verificar que todo responde: `bash scripts/probar-publico.sh` (200).
3. Abrir el túnel a las consolas (dejar esta terminal abierta):
   ```bash
   cd ~/Downloads
   ssh -i pedidos360-private.pem -L 18672:localhost:15672 -L 18673:localhost:15673 -L 18090:localhost:8090 \
       -o "ProxyCommand=ssh -i pedidos360-publica.pem -W %h:%p ec2-user@34.237.227.232" ec2-user@10.0.11.221
   ```
4. Pestañas listas: tienda, consola AWS (us-east-1), portal de Azure, RabbitMQ (http://localhost:18672), Kafka UI (http://localhost:18090), Gmail, Postman.
5. Dos sesiones de la tienda: **normal** con el Admin (`di.carrillow@duocuc.cl`) e **incógnito** con el cliente (`diego.carrilo@josecamposar.onmicrosoft.com`).
6. Grabar con **Win + Shift + R** (Recortes) o OBS.

| | URL |
|---|---|
| Tienda | <https://ahdle7i9n6.execute-api.us-east-1.amazonaws.com/desarrollo/> |
| API | `https://t13j9mbqg7.execute-api.us-east-1.amazonaws.com/desarrollo/api/v1/...` |
| GitHub | <https://github.com/ALFACW/pedidos360> |

---

## Escena 1. La tienda funcionando (1 min)

**Mostrar:** la tienda en el navegador, sin sesión → Iniciar sesión → selector de cuentas de Microsoft → elegir el cliente.

**Subtítulos:**
- Pedidos360: tienda cloud native desplegada en AWS.
- El inicio de sesión usa Microsoft Entra ID con OAuth 2.0 y OpenID Connect.
- Elijo la cuenta del cliente; MSAL obtiene un token JWT para llamar al API.

## Escena 2. Arquitectura en AWS (2,5 min)

Consola AWS, región **N. Virginia (us-east-1)**:

| Servicio | Qué mostrar | Subtítulo |
|---|---|---|
| **VPC** → `pedidos360-vpc` | 1 subred pública y 2 privadas, Internet Gateway, NAT Gateway, tablas de rutas | Red propia: lo público en una subred y el backend aislado en subredes privadas. |
| **EC2** → Instancias | `pedidos360-frontend` (IP pública 34.237.227.232) y `pedidos360-backend` (**sin IP pública**) | Dos EC2: una por cada API Gateway. El backend no es accesible desde Internet. |
| **EC2** → Security Groups | `sg-backend` solo acepta 8080-8087 desde el ALB y SSH desde el bastión | Cada capa solo acepta tráfico de la capa anterior. |
| **EC2** → Load Balancers | `pedidos360-alb-interno` (interno), reglas por ruta `/api/v1/<servicio>` | Un ALB interno reparte cada ruta al puerto de su microservicio. |
| **EC2** → Target groups | Los 8 en estado **healthy** | Los 8 microservicios responden su health check. |
| **API Gateway** → `pedidos360-api-v1` → Routes | 47 rutas, una por método, **sin ANY**; candado JWT | API versionada /api/v1: una ruta por cada endpoint y método. |
| → Authorization | Autorizador `entra-id-jwt` (issuer del tenant, audience de la app) | API Gateway valida el JWT de Entra ID antes de llegar al backend. |
| → CORS | Solo `https://ahdle7i9n6.execute-api.us-east-1.amazonaws.com` | CORS permite solo el origen de nuestra página. |
| → Integrations | VPC Link `pedidos360-vpclink` | El API llega al backend privado por un VPC Link. |
| **API Gateway** → `pedidos360-frontend` | Solo `GET /` y `GET /assets/{archivo}` | El frontend tiene su propio API Gateway que le da HTTPS. |
| **API Gateway** → `pedidos360-dashboard` | API **WebSocket**, rutas `$connect` y `$disconnect` | API WebSocket para el dashboard en tiempo real. |
| **Lambda** | `pedidos360-mensajero` y `pedidos360-stream` (disparador DynamoDB) | Dos Lambdas: una guarda conexiones y otra envía cada evento nuevo. |
| **DynamoDB** | `InteraccionesUsuario` (Explore items, Stream activo) y `ConexionesWebSocket` | Las interacciones de los clientes se guardan en DynamoDB. |
| **RDS** | `pedidos360-db` PostgreSQL en subred privada | Pedidos, productos, usuarios y pagos en PostgreSQL privado. |
| **ECR** | 10 repositorios `pedidos360/...` | Cada microservicio se publica como imagen en ECR. |

## Escena 3. IDaaS: Entra ID (40 s)

**Mostrar:** Portal de Azure → Entra ID → Usuarios (cliente e invitado Admin) → Aplicaciones empresariales → `pedidos360-backend` → Usuarios y grupos (rol **Admin**) → Registro de aplicación → Authentication (Redirect URI de la tienda).

**Subtítulos:**
- Tenant de Entra ID con usuarios registrados.
- El rol Admin se asigna en Entra ID y viaja en el token (claim roles).
- La Redirect URI es la URL de la tienda en API Gateway.

## Escena 4. Backend en la nube con docker compose (1 min)

**Mostrar** en una terminal:
```bash
ssh -i pedidos360-publica.pem ec2-user@34.237.227.232
ssh -i ~/.ssh/pedidos360-private.pem ec2-user@10.0.11.221
cd ~/pedidos360
docker compose -f docker-compose.kafka.yml ps
docker compose -f docker-compose.yml -f docker-compose.aws.yml ps
docker compose -f docker-compose.kafka.yml stop kafka3
docker compose -f docker-compose.kafka.yml up -d kafka3
docker exec pedidos360-backend-rabbitmq1-1 rabbitmqctl cluster_status
```

**Subtítulos:**
- Entro al backend privado pasando por el bastión.
- Un docker compose levanta Kafka: 3 Zookeeper, 3 brokers y Kafka UI.
- Otro levanta 9 microservicios y RabbitMQ en clúster de 2 nodos.
- Bajo un broker y lo levanto en vivo con docker compose.
- RabbitMQ confirma los dos nodos en el clúster.

## Escena 5. RabbitMQ (1 min)

**Mostrar** http://localhost:18672: Overview (2 nodos) → Exchanges → Queues → Connections.

**Subtítulos:**
- Consola de RabbitMQ: los dos nodos del clúster.
- Exchange topic pedidos360.events y exchange direct pedidos360.dlx.
- Tres colas de trabajo, cada una con su cola de mensajes muertos (DLQ).
- Flujo: pedido creado, pago aprobado o rechazado, y notificación por correo.
- Cada microservicio aparece con su nombre en Connections.

## Escena 6. Kafka UI (1,5 min)

**Mostrar** http://localhost:18090: Brokers → Topics → `pedidos360.interacciones-usuario` (Overview: 3 particiones, 3 réplicas, ISR) → Settings (retención) → Messages → Consumers.

**Subtítulos:**
- Kafka UI administra el clúster: 3 brokers activos.
- Dos tópicos con 3 particiones y 3 réplicas cada uno, más sus tópicos de error.
- 3 particiones permiten procesar en paralelo; 3 réplicas toleran la caída de un broker.
- Retención: intentos de pago 1 día, interacciones 7 días.
- Cada mensaje tiene clave, partición y offset.
- Consumer groups deteccion-fraude y recomendaciones, con lag 0.

## Escena 7. Compra completa del cliente (2 min)

**Mostrar** (ventana incógnito, cliente):
1. Hero → clic en un producto: panel lateral (evento **VER**).
2. "Agregar al carrito" (evento **CARRITO**) → "Recomendado para ti".
3. Carrito → **Pagar con WebPay** → tarjeta `4051885600446623` → WebPay de prueba (CVV 123, RUT 11.111.111-1, clave 123) → Aceptar.
4. Vuelve a la tienda → **Mis pedidos**: **Pagado**.
5. Gmail: correo "Pedido PED-...: pago aprobado".
6. Kafka UI → Messages: aparecen VER, CARRITO y COMPRA del cliente.

**Subtítulos:**
- Ver un producto publica un evento VER en Kafka.
- Agregarlo al carrito publica CARRITO; las recomendaciones se recalculan.
- El pago se hace con WebPay en ambiente de integración de Transbank.
- Al aprobarse, RabbitMQ avisa a pedidos (descuenta stock) y a notificaciones.
- El cliente recibe el correo de confirmación.
- La compra también llega a Kafka como evento COMPRA.

## Escena 8. Dashboard del Admin en tiempo real (1,5 min)

**Mostrar** (ventana normal, Admin) → Dashboard. En la ventana del cliente hacer clic en productos y crear un pedido con "Pagar después" y luego "Cancelar pedido".

**Subtítulos:**
- Dashboard del Admin conectado por WebSocket.
- Ventas confirmadas, pedidos pagados y ticket promedio.
- Embudo de conversión: vistas, carrito y compras.
- Cada clic del cliente aparece al instante en Actividad en vivo.
- Alertas con detalle: pedidos pendientes, cancelados, stock bajo y fraude.
- Un pedido sin pagar se puede reanudar o cancelar; el Admin lo ve actualizado.

## Escena 9. Antifraude: misma tarjeta más de 3 veces en 10 segundos (1,5 min)

**Opción A (Kafka UI, recomendada):** Topics → `pedidos360.intentos-pago` → **Produce Message**, 4 veces, Key `****9999`, cambiando solo la fecha (`...:00Z`, `...:02Z`, `...:04Z`, `...:06Z`):
```json
{"tarjeta":"****9999","cliente":"demo@pedidos360","pedidoId":4,"monto":1000,"fecha":"2026-11-26T12:00:00Z"}
```
Luego: Dashboard → Alertas (tarjeta `****9999`, 4 intentos). En la tienda, pagar un pedido con `4000000000009999` → "Tarjeta bloqueada por sospecha de fraude".

**Opción B (Postman o script):** `bash scripts/probar-antifraude.sh <token> <id-pedido-pendiente>` → 200, 200, 200, 200, 429.

**Subtítulos:**
- Regla antifraude: la misma tarjeta más de 3 veces en 10 segundos se bloquea.
- Cada intento de pago es un evento en Kafka con la tarjeta como clave.
- Misma tarjeta, misma partición: los intentos llegan en orden.
- Al cuarto intento la tarjeta se bloquea 30 minutos y se registra la alerta.
- El siguiente pago con esa tarjeta se rechaza antes de llegar a WebPay.

## Escena 10. Errores, administradores y cierre (1,5 min)

**Mostrar:**
1. Kafka UI → `pedidos360.interacciones-usuario` → Produce Message con `{"accion":"BORRAR"}` → el mensaje aparece en `pedidos360.interacciones-usuario.DLT` con el motivo en los headers.
2. Postman (token Admin): `GET /api/v1/kafka/topicos`, `POST /api/v1/kafka/topicos` (crear `demo.topico`), `GET /api/v1/kafka/grupos/deteccion-fraude`, `DELETE /api/v1/kafka/topicos/demo.topico`.
3. Postman: `POST /api/v1/rabbit/queues` y `DELETE /api/v1/rabbit/queues/{nombre}`.
4. Postman sin token: `GET /api/v1/products` → 401.

**Subtítulos:**
- Un mensaje inválido no detiene al consumidor: va al tópico .DLT con su motivo.
- Los errores transitorios se reintentan 3 veces antes de la DLT.
- Microservicio administrador de Kafka: crea, modifica y elimina tópicos y ve el lag.
- Microservicio administrador de RabbitMQ: crea y elimina colas, exchanges y bindings.
- Sin token, API Gateway responde 401.
- RabbitMQ se mantiene para tareas; Kafka se suma para eventos y analítica.

---

## Para responder preguntas

**¿Por qué RabbitMQ y Kafka?** RabbitMQ para **tareas** (pedido → pago → correo): cada mensaje lo procesa un consumidor una vez, con ACK, reintentos y DLQ. Kafka para **eventos** (clics, carrito, compras, intentos de pago): alto volumen, se conservan para releerlos y varios servicios los consumen por separado.

| Tópico | Clave | Retención | Consumer group | Para qué |
|---|---|---|---|---|
| `pedidos360.intentos-pago` | tarjeta | 1 día | `deteccion-fraude` | Ventana de 10 s del antifraude; misma tarjeta en la misma partición |
| `pedidos360.interacciones-usuario` | usuario | 7 días | `recomendaciones` | Recomendaciones, DynamoDB y dashboard |

**Diseño de los eventos:**
- *Intento de pago:* `tarjeta` (solo últimos 4 dígitos, es la clave), `cliente`, `pedidoId`, `monto` y `fecha` (hora del evento, para que la ventana de 10 s no dependa del retraso del consumidor).
- *Interacción:* `usuario` (clave), `productoId`, `nombreProducto`, `categoria` (para recomendar por categoría), `accion` (VER, CARRITO o COMPRA, con pesos 1, 2 y 3) y `fecha`.

**Tolerancia a fallos:** 3 réplicas con `min.insync.replicas=2` y `acks=all`: un mensaje se confirma cuando lo tienen 2 brokers, así que se puede caer 1 sin perder datos. Los 3 Zookeeper mantienen los metadatos y eligen el controlador con mayoría de 2 de 3. Si un consumidor se cae, Kafka conserva los mensajes y el grupo retoma desde su último offset (el lag baja a 0).

**Estados de un pedido:** Pendiente de pago (creado, sin resultado de WebPay; anular en WebPay no lo cambia) → Pagado (descuenta stock y envía correo) o Pago rechazado; Cancelado si el cliente lo cancela antes de pagar.

**Correos:** se envían al correo del token. Las cuentas `@josecamposar.onmicrosoft.com` no tienen buzón, así que cada confirmación va también con copia oculta al Gmail de la tienda.

## Después de presentar

`bash scripts/infra/99-eliminar-costosos.sh` (NAT, ALB, VPC Link y RDS cobran aunque el laboratorio esté apagado).
