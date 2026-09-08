Frontend React + MSAL - flujo mínimo

1. Instala dependencias:

   cd frontend
   npm install

2. Configura las variables de entorno:

   copy .env.example .env

   Luego reemplaza:
   - VITE_AZURE_CLIENT_ID
   - VITE_AZURE_TENANT_ID
   - VITE_API_BASE_URL=http://localhost:8080

3. Ejecuta la app:

   npm run dev

4. Flujo esperado:
   - inicia sesión con Azure
   - obtiene el JWT
   - llama a http://localhost:8080/api/orders
   - si el token es válido, el backend responde

5. Importante:
   Este proyecto es una base simple. No es el despliegue final en AWS ni la arquitectura completa con gateway y microservicios ya montados.
   Sirve para probar el patrón base del profesor: Azure login + JWT + backend protegido.
