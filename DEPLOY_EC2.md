# Despliegue en EC2 separadas

AWS API Gateway es administrado por AWS. Se integra con el backend EC2 y no requiere una instancia propia.

## EC2 backend

Clona el repositorio y configura AWS RDS PostgreSQL. Ejecuta `database/schema.sql` en `pedidos360db` con un cliente PostgreSQL. En el Security Group de RDS permite conexiones al puerto 5432 solo desde el Security Group de la EC2 backend.

Copia `.env.example` a `.env`, reemplaza los valores DB/RabbitMQ/SMTP, configura `FRONTEND_ORIGIN` con el origen público del frontend y `JWT_AUDIENCE` con el Client ID de la API registrada en Entra ID. Para AWS configura el endpoint SMTP de SES, el puerto y credenciales SMTP generadas por SES, `MAIL_FROM` con una identidad verificada y TLS según el puerto. No uses las credenciales de acceso generales de AWS como credenciales SMTP ni las guardes en el repositorio. Luego ejecuta desde la raíz del repositorio:

```bash
docker compose --env-file .env up -d --build
```

Este Compose construye los microservicios desde sus Dockerfiles. Orders, products y users persisten en PostgreSQL mediante JPA y validan firma, issuer, vigencia y audience del JWT. Payments guarda los IDs procesados en `payment_inbox` y notifications persiste cada resultado usando su `event_id` como clave única para ignorar redeliveries de RabbitMQ. Orders incluye el correo del usuario autenticado en el evento del pedido y notifications envía el resultado por SMTP; para pruebas locales usa Mailpit mediante `docker-compose.mailpit.yml`, no en la EC2. Cart sigue siendo un ejemplo en memoria. Orders guarda pedido y outbox en una transacción PostgreSQL y reintenta publicar si RabbitMQ no está disponible. Payments, notifications y RabbitMQ permanecen en la red Docker; la consola de RabbitMQ solo escucha en localhost.

## EC2 frontend

Copia `frontend/.env.production.example` a `frontend/.env.production` y configura los IDs de Entra ID, `VITE_API_SCOPE` (un scope delegado expuesto por la app API) y la URL base del API Gateway. Desde la raíz del repositorio ejecuta:

```bash
docker compose -f docker-compose.frontend.yml --env-file frontend/.env.production up -d --build
```

Vite incorpora esas variables durante el build; cambiar variables del contenedor después no cambia la URL compilada. MSAL solicita un access token para el API de Pedidos360; no envía el ID token ni un token de Microsoft Graph. Nginx sirve solamente los archivos del frontend. Las llamadas del navegador van a API Gateway, no a nombres Docker del backend.

## AWS

Configura las integraciones y rutas en API Gateway para los endpoints del backend. En los Security Groups permite HTTP/HTTPS al frontend y limita los puertos backend a lo necesario para las integraciones de API Gateway. No abras los puertos RabbitMQ 5672 ni 15672 a Internet.

La audiencia configurada en API Gateway debe coincidir con `JWT_AUDIENCE` y con el `aud` del access token emitido para `VITE_API_SCOPE`. El token debe incluir uno de estos claims con el correo del cliente: `email`, `preferred_username` o `upn`; Orders usa ese valor como destinatario y no confía en una dirección enviada por el navegador. El origen configurado en CORS debe coincidir exactamente con el origen público del frontend. La app API de Entra debe exponer ese scope y autorizar al frontend a solicitarlo.
