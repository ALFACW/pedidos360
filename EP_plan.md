Plan de trabajo – EP1 y EP2 (Pedidos360)

Resumen objetivo:
Implementar una arquitectura mínima Cloud Native para la asignatura: Angular (frontend) + API Gateway + Backend (Spring Boot) + IDaaS (MSAL/Azure Entra) con autenticación basada en JWT.

Prioridades:

1. Cumplir criterios de `EP1_DSY1107_Estudiante_encargo.pdf` (endpoints, autenticación, pruebas básicas).
2. Preparar demo y slides para `EP2_DSY1107_Estudiante_presentación.pdf`.

Desglose de tareas (ordenadas, estimación y dueño):

1. Revisión de requisitos (EP1) — 1.5h
   - Extraer criterios de aceptación y endpoints obligatorios del PDF.
   - Resultado: checklist verificable.

2. Diseño arquitectónico ligero — 1h
   - Diagrama simple: Frontend ↔ API Gateway ↔ Backend (microservicio) + IDaaS.
   - Resultado: `architecture.png` (opcional) y `README` con componentes.

3. Scaffold backend (Spring Boot) — 2h
   - Crear proyecto Maven con `spring-boot-starter-web` y `spring-boot-starter-oauth2-resource-server`.
   - Implementar controlador mínimo `/api/orders` que devuelve datos de ejemplo.
   - Resultado: código que puede ejecutarse con `mvn spring-boot:run`.

4. Scaffold frontend (Angular) — 2h
   - Crear app Angular (o usar esqueleto) e integrar MSAL siguiendo `1.3.2`.
   - Implementar login MSAL y llamada a `/api/orders`.
   - Resultado: app que inicia sesión y consume el endpoint.

5. API Gateway (configuración mínima) — 1.5h
   - Usar el tutorial `1.1.2` para definir rutas y políticas (puede ser AWS API Gateway u otro simulado).
   - Habilitar CORS (`1.1.4`) y versionamiento (`1.1.3`).
   - Resultado: rutas públicas/protegidas funcionando.

6. Seguridad y prueba E2E — 2h
   - Configurar backend para validar JWT emitidos por IDaaS (MSAL/Azure Entra) usando `spring-boot-starter-oauth2-resource-server`.
   - Probar flujo login → llamada protegida.

7. Documentación y entregables — 1.5h
   - README con instrucciones exactas de ejecución (local y cloud), capturas y evidencia.
   - Slides/resumen para presentación (EP2).

Timeline sugerido (semana corta de trabajo):

- Día 1: Tareas 1–3 (requisitos + diseño + backend básico)
- Día 2: Tareas 4–5 (frontend + gateway)
- Día 3: Tareas 6–7 (seguridad, pruebas, documentación y presentación)

Criterios de entrega EP1 (mínimo viable):

- Endpoint `/api/orders` protegido por JWT.
- Frontend permite login y consulta del endpoint.
- Documentación con pasos para ejecutar y evidencia (logs/screenshots).

Criterios de entrega EP2 (presentación):

- Slides que muestren arquitectura, demo (o vídeo), breves lecciones aprendidas y cumplimiento de criterios del PDF.

Próximo: creo un scaffolding mínimo de backend (Spring Boot) y README con pasos para crear el frontend Angular.
