Plan de trabajo – EP1 y EP2 (Pedidos360)

Resumen objetivo:
Implementar la arquitectura Cloud Native solicitada: frontend + AWS API Gateway + microservicios Spring Boot en EC2 + IDaaS (MSAL/Azure Entra) con autenticación basada en JWT. Para el despliegue del proyecto, mantener frontend y backend en instancias EC2 separadas.

Notas de alcance y rúbrica:

- AWS API Gateway es un servicio administrado de AWS; no requiere una EC2 propia.
- EP1 solicita backend Spring Boot desplegado en EC2 y protegido por API Gateway. EP2 solicita mostrar el API Manager activo e integrado; ninguna de esas instrucciones exige una EC2 exclusiva para API Gateway.
- Para el frontend se eligió React, opción permitida junto con Angular para este proyecto.

Prioridades:

1. Cumplir criterios de `EP1_DSY1107_Estudiante_encargo.pdf` (endpoints, autenticación, pruebas básicas).
2. Preparar demo y slides para `EP2_DSY1107_Estudiante_presentación.pdf`.

Desglose de tareas (ordenadas, estimación y dueño):

1. Revisión de requisitos (EP1) — 1.5h
   - Extraer criterios de aceptación y endpoints obligatorios del PDF.
   - Resultado: checklist verificable.

2. Diseño arquitectónico ligero — 1h
   - Diagrama simple: EC2 frontend → AWS API Gateway (administrado) → EC2 backend (microservicios) + IDaaS.
   - API Gateway enruta al backend; no se instala ni ejecuta en una EC2 del proyecto.
   - Resultado: `architecture.png` (opcional) y `README` con componentes.

3. Scaffold backend (Spring Boot) — 2h
   - Crear proyecto Maven con `spring-boot-starter-web` y `spring-boot-starter-oauth2-resource-server`.
   - Implementar controlador mínimo `/api/orders` que devuelve datos de ejemplo.
   - Orders, products y users usan Spring Data JPA/repositorios con PostgreSQL; orders escribe pedido y outbox en la misma transacción.
   - Validado localmente: products/users arrancan contra PostgreSQL; queda repetir la validación runtime de orders tras corregir tipos de ID.
   - Cart sigue siendo un ejemplo en memoria y no forma parte todavía de la persistencia cloud.
   - Resultado: los servicios de negocio requeridos persisten en PostgreSQL/RDS y compilan con `mvn spring-boot:run`.

4. Frontend React + MSAL — 2h
   - Mantener la app React existente e integrar MSAL siguiendo `1.3.2`.
   - Implementar login MSAL y llamada a `/api/orders`.
   - Resultado: app que inicia sesión y consume el endpoint.

5. API Gateway (configuración mínima) — 1.5h
   - Configurar AWS API Gateway real según la pauta; crear rutas y políticas para los endpoints del backend.
   - Habilitar CORS (`1.1.4`) y versionamiento (`1.1.3`).
   - Resultado: rutas públicas/protegidas funcionando.

6. Seguridad y prueba E2E — 2h
   - React solicita un access token para `VITE_API_SCOPE`; no enviar ID tokens ni tokens de Microsoft Graph al backend.
   - La aplicación API de Entra debe exponer el scope delegado `access_as_user` y autorizar a la aplicación frontend.
   - Backend valida firma, issuer, vigencia y audience (`JWT_AUDIENCE`).
   - Pendiente: validar el access token real de Entra contra API Gateway y backend una vez configurado el scope.

7. Documentación y entregables — 1.5h
   - README con instrucciones exactas de ejecución (local y cloud), capturas y evidencia.
   - Slides/resumen para presentación (EP2).

8. Despliegue separado en EC2 — configuración local lista; despliegue AWS pendiente
   - EC2 frontend: `docker-compose.frontend.yml` construye React/Nginx con `VITE_API_BASE_URL` apuntando a API Gateway durante el build.
   - EC2 backend: `docker-compose.yml` construye orders, products, users, cart, payments y notifications desde sus Dockerfiles, junto con RabbitMQ; no incluye frontend.
   - AWS API Gateway: enrutar sus paths a los puertos publicados del backend EC2. No crear una EC2 para API Gateway.
   - `FRONTEND_ORIGIN` configura CORS para permitir solo el origen real del frontend.
   - Outbox de orders conserva los eventos con el pedido y los reintenta cuando RabbitMQ vuelve; las pruebas cubren caída, recuperación y reinicio del store.
   - Validación local: ambos Compose y las imágenes se construyen; los cuatro health endpoints responden `200`, una API protegida responde `401` sin token y los workers conectan a RabbitMQ.
   - Implementado localmente: pagos registra `event_id` de pedidos en `payment_inbox`; notifications registra los resultados en PostgreSQL con `event_id` único. Se añadieron tablas al esquema, configuración JDBC para ambos servicios y pruebas de redelivery duplicada; ambas suites pasan.
   - Implementado localmente: `App.jsx` delega el login, la cabecera, las vistas del dashboard y los modales de producto/usuario/comprobante en componentes separados. MSAL y la lógica de negocio permanecen en `App.jsx`; el build de producción de Vite pasa.
   - Implementado localmente: notifications envía por SMTP los resultados de pago al correo del usuario autenticado. El correo viaja por el evento desde orders a payments; Mailpit se ejecuta con un Compose de desarrollo separado. Se conserva la deduplicación PostgreSQL y el reintento/dead-letter de RabbitMQ.
   - Revisión npm completada: `npm audit` reporta 3 vulnerabilidades (1 moderada y 2 altas) en dependencias transitivas (`esbuild` vía Vite y `source-map-js`). No se aplicaron cambios de dependencias; npm indica que corregir `esbuild` requiere actualizar Vite a una versión mayor.
   - Pendiente: remediar las alertas npm y desplegar las dos composiciones en EC2 con rutas reales de API Gateway.

Timeline sugerido (semana corta de trabajo):

- Día 1: Tareas 1–3 (requisitos + diseño + backend básico)
- Día 2: Tareas 4–5 (frontend + gateway)
- Día 3: Tareas 6–7 (seguridad, pruebas, documentación y presentación)

Criterios de entrega EP1 (mínimo viable):

- Endpoint `/api/orders` protegido por JWT.
- Persistencia cloud configurada con entidades, repositorios y propiedades de conexión.
- Frontend permite login y consulta del endpoint.
- Documentación con pasos para ejecutar y evidencia (logs/screenshots).

Criterios de entrega EP2 (presentación):

- Slides que muestren arquitectura, demo (o vídeo), breves lecciones aprendidas y cumplimiento de criterios del PDF.

Revisión de continuación (2026-10-06): ambos archivos Compose pasan la validación de configuración. Se corrigió `scripts/probar-con-token.sh`, que antes enviaba una cadena de máscara en vez del token Bearer recibido. No se ejecutó ningún cambio en AWS: AWS CLI está instalado, pero las credenciales actuales fallan con `InvalidClientTokenId`. Antes del despliegue remoto hay que renovar/autenticar credenciales y confirmar las EC2 y el API Gateway destino.

Nota (2026-11-25): las credenciales se renovaron y el despliegue en AWS se completó entre el 2026-10-07 y el 2026-10-08 (ver Fase E). El API `3hvdy2quhe` y el `4dyx4unmg5` quedaron obsoletos; el API vigente del backend es `pedidos360-api-v1` (`fkiduebsb9`).


Plan de cierre antes de AWS (2026-10-06)

Fase A - Verificacion local (sin AWS)
- [x] RabbitMQ local levantado con .env propio (panel http://localhost:15672).
- [x] A1. PostgreSQL local en Docker con database/schema.sql aplicado; DB_URL/DB_USERNAME/DB_PASSWORD locales en .env.
- [x] A2. Reconstruir imagenes y levantar docker-compose.yml + docker-compose.mailpit.yml.
- [x] A3. Flujo E2E: crear pedido -> ORDER_CREATED -> pago -> correo visible en Mailpit (http://localhost:8025).
- [x] A4. Reintentos: SMTP caido -> reintento y cola pedidos360.notifications.payment-results.dead.
- [x] A5. Frontend: npm run build, npm test y login con Entra ID contra backend local.

Fase B - Identidad
- [x] B1. Access token con claim email/upn (optional claims en Entra); si falta, orders responde 400.
- [x] B2. JWT_AUDIENCE y FRONTEND_ORIGIN de produccion confirmados (origen https://44-221-145-83.nip.io); el scope usado es `read-wirte` (VITE_API_SCOPE), no access_as_user.
- [x] B3. Roles Admin/Cliente: el rol Admin se asigna como App role en Entra ID (ADMIN_EMAILS vacio por defecto).

Fase C - Correo de produccion
- [x] C1. Proveedor elegido: SMTP de Gmail (MAIL_* tomados del .env local por scripts/desplegar-backend-privado.sh).
- [x] C2. MAIL_* reales fuera del repo (solo en el .env del servidor, chmod 600). Ver E4.

Fase D - Repositorio
- [x] D1a. Commit de los cambios (2026-11-25).
- [ ] D1b. Configurar el remoto de GitHub y hacer push (hoy el repo NO tiene remoto; EP3 se entrega como enlace de GitHub).
- [ ] D1c. Revisar y eliminar stash@{0} ("Auto-stash ... before migration", sobre main).

Fase E - Infraestructura AWS (requiere credenciales validas)
- [x] E1. Credenciales renovadas, region us-east-1. (Las credenciales de AWS Academy expiran: renovarlas antes de la presentacion.)
- [x] E2. VPC con subred publica (EC2 frontend + bastion, Caddy HTTPS, EIP 44.221.145.83) y privada (EC2 backend 10.0.132.193, salida por NAT); RDS por VPC peering; imagenes en ECR; API Gateway pedidos360-api-v1 (fkiduebsb9) -> VPC Link -> ALB interno; API pedidos360-frontend (e3fc0mjbec).
- [x] E3. Backend y RabbitMQ sin IP publica (solo accesibles via bastion/VPC Link); HTTPS en API Gateway y Caddy.
- [ ] E4. Secretos en SSM Parameter Store o Secrets Manager, no .env en el servidor.
- [ ] E5. Alertas de presupuesto/costos.
- [x] E6. Ambos Compose desplegados (scripts/desplegar-backend-privado.sh); rutas, CORS y autorizador JWT creados con scripts/crear-api-v1.sh.
- [x] E7. Rutas del API Gateway: una por metodo y endpoint (30 rutas, sin ANY ni {proxy+}), segun lo pedido por el docente. El API del frontend solo expone GET / y GET /{proxy+}. Se eliminaron los scripts antiguos que creaban rutas ANY (apuntar-apigateway-vpclink.sh, configurar-jwt-apigateway.ps1, update-ip.ps1).
- [ ] E8. Volver a ejecutar scripts/crear-api-v1.sh y scripts/crear-api-frontend.sh con credenciales vigentes para borrar en AWS cualquier ruta ANY que quede, y eliminar (o dejar de mostrar) los APIs antiguos 3hvdy2quhe / 4dyx4unmg5 si siguen existiendo.

Fase F - Entregables EP2
- [ ] F1. Evidencia (capturas/logs), README y slides con arquitectura y demo.


Evaluacion 2 - EP3 (encargo, RabbitMQ) y EP4 (presentacion)

Alcance: incorporar RabbitMQ con colas, exchanges y DLQ para tareas asincronas, sin afectar la logica existente. Frontend: el docente acepto React (Angular o React).

Fase G - Mensajeria (EP3)
- [x] G1. Flujo asincrono: ms-pedidos publica order.created (outbox transaccional) -> ms-pagos procesa y publica payment.approved/rejected -> ms-pedidos actualiza el pedido y descuenta stock; ms-notificaciones envia el correo.
- [x] G2. Exchanges: pedidos360.events (topic) y pedidos360.dlx (direct).
- [x] G3. Tres colas con su DLQ: pedidos360.payments.order-created, pedidos360.orders.payment-results y pedidos360.notifications.payment-results (.queue / .dead).
- [x] G4. Nombres centralizados en una clase RabbitConfig (@Configuration) por microservicio, con beans Queue/Exchange/Binding; sin nombres sueltos en el codigo de negocio.
- [x] G5. Consumidores con @RabbitListener agrupados por dominio; ACK manual (RabbitAckHandler): error no recuperable -> NACK a DLQ; error recuperable -> reintentos con backoff y luego DLQ; todo registrado en logs.
- [x] G6. Idempotencia: payment_inbox (pagos) y event_id unico (notificaciones).
- [x] G7. Microservicio administrador (ms-rabbit-admin): POST/DELETE de colas, exchanges y bindings en /api/v1/rabbit, logica en RabbitAdminService, validacion con DTO + Bean Validation, recursos pedidos360.* protegidos (409).
- [x] G8. Pruebas: mvn test pasa en orders, payments, notifications, rabbit-admin, products y users (2026-11-25).
- [ ] G9. (Opcional, no se hara) Colas quorum para alta disponibilidad real en el cluster; hoy son colas clasicas durables.

Fase H - Infraestructura RabbitMQ
- [x] H1. Cluster de 2 nodos (rabbitmq1, rabbitmq2) en docker-compose.yml, peer discovery classic_config, Erlang cookie compartida, autoheal.
- [x] H2. Microservicios conectados a ambos nodos (RABBITMQ_ADDRESSES) con nombre de conexion visible (ms-pedidos, ms-pagos, ms-notificaciones, ms-rabbit-admin).
- [x] H3. docker-compose.aws.yml (imagenes de ECR) desplegado en la EC2 privada.

Fase I - Presentacion EP4
- [x] I1. Guion de demo con checklist de la pauta: DEMO_PRESENTACION.md.
- [ ] I2. Renovar credenciales AWS Academy y encender EC2/RDS/NAT antes de la presentacion.
- [ ] I3. Ensayar la demo completa (5-10 minutos) y tomar capturas de respaldo.


Evaluacion 3 - EP5 (encargo, Kafka) y EP6 (presentacion) - 2026-11-25

Cambio de cuenta AWS: el laboratorio anterior (468107274970) agoto su saldo; todo se recreo en la cuenta 486702306352 con scripts/infra/01..08. Despues de presentar: scripts/infra/99-eliminar-costosos.sh (NAT, ALB, VPC Link y RDS cobran aunque el laboratorio este apagado).

- [x] Kafka: 3 Zookeeper + 3 brokers + Kafka UI (docker-compose.kafka.yml) en la EC2 backend; 2 topicos de 3 particiones y 3 replicas (+ .DLT).
- [x] ms-pagos: WebPay integracion (+5) y antifraude (misma tarjeta mas de 3 veces en 10 s -> bloqueo 30 min + alerta) via topico pedidos360.intentos-pago.
- [x] ms-productos (VER) y ms-carrito (CARRITO) publican en pedidos360.interacciones-usuario; ms-recomendaciones consume y guarda en DynamoDB (+2).
- [x] ms-administrador-kafka: topicos (crear, listar, describir, modificar, eliminar) y consumer groups (offsets y lag).
- [x] Errores: DefaultErrorHandler (3 reintentos exponenciales) + DeadLetterPublishingRecoverer (.DLT) + ErrorHandlingDeserializer + @Valid.
- [x] Dashboard WebSocket (+3): DynamoDB Stream -> Lambda -> API WebSocket -> pestana Dashboard (3 metricas).
- [x] 2 EC2 (una por API Gateway); rutas por metodo (44 backend, 2 GET frontend); CORS solo la pagina del frontend.
- [x] Repositorio publico: https://github.com/ALFACW/pedidos360
- [ ] Agregar en Entra ID la Redirect URI https://ahdle7i9n6.execute-api.us-east-1.amazonaws.com/desarrollo/
- [ ] Prueba completa con login: compra + WebPay, antifraude, recomendaciones y dashboard.
- [ ] Ensayar la demo (DEMO_PRESENTACION.md) y explicar RabbitMQ vs Kafka.
