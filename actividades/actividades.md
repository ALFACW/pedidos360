# Registro de Actividades

Resumen de acciones realizadas para preparar EP1 y EP2 (Pedidos360):

- 2026-09-01: Ejecutado `extract_text.py` para extraer texto de los PDFs y generar `extracted_texts/summary.json`.
- 2026-09-01: Revisados y analizados los contenidos extraídos (guías de API Manager, OAuth2/OIDC, MSAL, JWT, integración Front/Back).
- 2026-09-01: Creado `EP_plan.md` con plan de trabajo, tareas y cronograma sugerido para EP1/EP2.
- 2026-09-01: Scaffold inicial del servicio de pedidos creado (Spring Boot): `backend-orders/pom.xml`, `Application.java`, `OrderController.java` y `backend-orders/README.md`.
- 2026-09-01: Añadido `.gitignore` y guías básicas de entrega.
- 2026-09-01: Definidos los 3 microservicios recomendados: `orders`, `products`, `users` (endpoints y payloads mínimos documentados en `EP_plan.md`).
- 2026-09-01: Confirmado flujo de autenticación: Azure AD (MSAL) en frontend; JWT validado por API Manager (AWS) y por los backends (Spring Security/OAuth2 Resource Server).

Pendientes (priorizados):

- Crear scaffolding Spring Boot para `products` y `users`.
- Cambiar frontend propuesto a React con ejemplo MSAL (`@azure/msal-react`).
- Añadir `application.yml` de ejemplo para validar JWT de Azure en cada microservicio.
- Documentar la configuración de AWS API Gateway JWT Authorizer apuntando al issuer/audience de Azure.

Archivos relevantes:

- `../EP_plan.md` — plan de trabajo y definición de microservicios.
- `../extracted_texts/summary.json` — resumen de extracción de textos.
- `../backend-orders/` — servicio de pedidos (orders).
