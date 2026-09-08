# Users Service

Microservicio `users` - ejemplo mínimo para EP1/EP2.

Run:

```bash
cd backend-users
mvn spring-boot:run
```

Este servicio representa el perfil de usuario. Para la evaluación, la autenticación se realiza en Azure AD; este servicio deberá recibir el token en las llamadas y validar claims (ejemplo de `application.yml` pendiente).
