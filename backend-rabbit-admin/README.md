# ms-rabbit-admin

Microservicio administrador de RabbitMQ (puerto 8084). Crea y elimina colas, exchanges y bindings mediante una API REST. Toda la lógica de RabbitMQ está en `RabbitAdminService`; el controlador solo valida la entrada y delega.

Todas las rutas, salvo `/api/rabbit/health`, exigen un JWT de Entra ID con rol **Admin** (rol `Admin` en el token o correo incluido en `ADMIN_EMAILS`).

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/rabbit/health` | Estado del servicio (público) |
| POST | `/api/rabbit/queues` | Crea una cola, opcionalmente con DLQ y TTL |
| GET | `/api/rabbit/queues/{name}` | Mensajes y consumidores de la cola |
| DELETE | `/api/rabbit/queues/{name}` | Elimina una cola |
| POST | `/api/rabbit/exchanges` | Crea un exchange `direct`, `topic`, `fanout` o `headers` |
| DELETE | `/api/rabbit/exchanges/{name}` | Elimina un exchange |
| POST | `/api/rabbit/bindings` | Enlaza exchange → cola con una routing key |
| DELETE | `/api/rabbit/bindings?queue=&exchange=&routingKey=` | Elimina el enlace |

## Ejemplo: cola con su DLQ sobre un exchange direct

```http
POST /api/rabbit/exchanges
{"name": "demo.direct", "type": "direct"}

POST /api/rabbit/queues
{"name": "demo.dead"}

POST /api/rabbit/queues
{"name": "demo.queue", "deadLetterExchange": "demo.direct", "deadLetterRoutingKey": "demo.dead", "messageTtlMs": 60000}

POST /api/rabbit/bindings
{"queue": "demo.dead", "exchange": "demo.direct", "routingKey": "demo.dead"}
```

## Validaciones y errores

- Nombres: solo letras, números y `. _ : -`, máximo 255 caracteres, sin el prefijo reservado `amq.` → **400** con el detalle por campo.
- Tipo de exchange distinto de `direct|topic|fanout|headers` → **400**.
- `deadLetterRoutingKey` sin `deadLetterExchange` → **400**.
- Recursos con prefijo `pedidos360.` (los de los microservicios) no se pueden crear ni eliminar → **409**.
- Cola inexistente → **404**. RabbitMQ rechaza la operación (p. ej. redeclarar una cola con otros argumentos) → **409**.

## Ejecución local

```bash
mvn spring-boot:run   # requiere RABBITMQ_PASSWORD y RabbitMQ en localhost:5672
mvn test
```
