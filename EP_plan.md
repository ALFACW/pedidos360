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

Próximo: renovar credenciales AWS válidas; confirmar las instancias EC2 backend/frontend, el RDS y la API Gateway (región `us-east-1`, API `3hvdy2quhe` según los scripts); después desplegar las dos composiciones, revisar rutas/CORS/authorizer y ejecutar las pruebas pública y autenticada.


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
- [ ] B2. JWT_AUDIENCE y FRONTEND_ORIGIN de produccion confirmados; scope access_as_user autorizado.

Fase C - Correo de produccion
- [ ] C1. Elegir proveedor (SES con remitente/dominio verificado y salida de sandbox, o Resend con dominio verificado).
- [ ] C2. Definir MAIL_* reales como secretos (nunca en el repo).

Fase D - Repositorio
- [ ] D1. Commit de los cambios actuales y push; luego eliminar stash@{0}.

Fase E - Infraestructura AWS (requiere credenciales validas)
- [ ] E1. Renovar credenciales AWS (hoy: InvalidClientTokenId) y confirmar region us-east-1.
- [ ] E2. Definir EC2 backend/frontend (tamano), RDS con sslmode=require, API Gateway (API 3hvdy2quhe) y alojamiento del frontend.
- [ ] E3. Security groups: RabbitMQ y puertos de servicios no expuestos; HTTPS en el borde.
- [ ] E4. Secretos en SSM Parameter Store o Secrets Manager, no .env en el servidor.
- [ ] E5. Alertas de presupuesto/costos.
- [ ] E6. Desplegar ambos Compose, revisar rutas/CORS/authorizer y ejecutar pruebas publica y autenticada.

Fase F - Entregables EP2
- [ ] F1. Evidencia (capturas/logs), README y slides con arquitectura y demo.
