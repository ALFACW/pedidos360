Backend (Spring Boot) - flujo mínimo con JWT de Azure

Requisitos previos:

- Java 17+
- Maven 3+

1. Edita el tenant de Azure en:
   backend/src/main/resources/application.yml

   Cambia:
   spring.security.oauth2.resourceserver.jwt.tenant-id: YOUR_TENANT_ID

2. Ejecuta el backend:

   cd backend
   mvn spring-boot:run

3. Endpoints de prueba:
   - Público: http://localhost:8080/api/orders/public
   - Protegido: http://localhost:8080/api/orders

4. Importante:
   El endpoint protegido requiere un JWT válido emitido por Azure Entra ID.
   Si el token no es válido, la petición será rechazada.

5. En esta versión simple:
   - el backend valida el token usando `spring-boot-starter-oauth2-resource-server`
   - se usa `issuer-uri` y `tenant-id` como referencia del ejemplo del profesor
   - la arquitectura final sigue siendo frontend → API Gateway → microservicios privados
