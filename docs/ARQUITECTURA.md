# Arquitectura de TaskFlow

Este documento orienta a un desarrollador nuevo sobre la estructura, el flujo de creación de tareas, dónde están las reglas de negocio, seguridad JWT y la organización de tests.

## Capas y paquetes

- Capa HTTP / controllers
  - `ProjectController` — `src/main/java/com/taskflow/controller/ProjectController.java`
  - `TaskController` — `src/main/java/com/taskflow/controller/TaskController.java`
  - `AuthController` — `src/main/java/com/taskflow/controller/AuthController.java`

- Capa de servicio (casos de uso / orquestación)
  - `ProjectService` — `src/main/java/com/taskflow/service/ProjectService.java`
  - `TaskService` — `src/main/java/com/taskflow/service/TaskService.java`
  - `AuthService` — `src/main/java/com/taskflow/service/AuthService.java`

- Repositorios (persistencia)
  - `TaskRepository` — `src/main/java/com/taskflow/repository/TaskRepository.java`
  - (Otros repositorios siguen la misma convención en `src/main/java/com/taskflow/repository/`)

- Dominio / modelos
  - `Task` — `src/main/java/com/taskflow/model/Task.java`
  - `Project` — `src/main/java/com/taskflow/model/Project.java`
  - enums y modelos auxiliares en `src/main/java/com/taskflow/model/`

- DTOs y mappers (contratos HTTP)
  - DTOs en `src/main/java/com/taskflow/dto/` (ej. `TaskRequest`, `TaskResponse`, `ProjectRequest`)
  - `TaskMapper` — `src/main/java/com/taskflow/mapper/TaskMapper.java`
  - `ProjectMapper` — `src/main/java/com/taskflow/mapper/ProjectMapper.java`

- Configuración y cross-cutting
  - `SecurityConfig` — `src/main/java/com/taskflow/config/SecurityConfig.java`
  - `DataSeeder` — `src/main/java/com/taskflow/config/DataSeeder.java`
  - `OpenApiConfig` — `src/main/java/com/taskflow/config/OpenApiConfig.java`
  - `GlobalExceptionHandler` — `src/main/java/com/taskflow/advice/GlobalExceptionHandler.java`

- Seguridad JWT
  - `JwtAuthenticationFilter` — `src/main/java/com/taskflow/security/JwtAuthenticationFilter.java`
  - `JwtService` — `src/main/java/com/taskflow/security/JwtService.java`
  - `ProjectSecurity` (bean para checks data-driven) — `src/main/java/com/taskflow/security/ProjectSecurity.java`

## Recorrido: POST /projects/{projectId}/tasks

1. Cliente envía POST /projects/{projectId}/tasks con cuerpo JSON (DTO `TaskRequest`).
2. Llega a `TaskController.createTask` — `src/main/java/com/taskflow/controller/TaskController.java`.
   - El `@Valid` sobre `@RequestBody TaskRequest` ejecuta Bean Validation; fallos → 400 via `GlobalExceptionHandler`.
   - El controller verifica existencia del proyecto: `projectService.buscarPorId(projectId)` → si no existe lanza `ProjectNotFoundException` → 404.
3. El controller llama a `taskService.crear(request, projectId)` (`TaskService` — `src/main/java/com/taskflow/service/TaskService.java`).
4. Dentro de `TaskService.crear`:
   - Se invoca `TaskMapper.aEntidadNueva(request, projectId)` (`src/main/java/com/taskflow/mapper/TaskMapper.java`).
   - `TaskMapper.aEntidadNueva` llama a la factory de dominio `Task.crear(...)` (`src/main/java/com/taskflow/model/Task.java`).
     - Aquí viven reglas de negocio de creación: por ejemplo `dueDate` no puede estar en el pasado; título debe tener longitud válida; `projectId` obligatorio.
     - Si alguna regla falla, `TaskValidationException` (checked) se lanza y sube hasta el `GlobalExceptionHandler` que devuelve 400.
   - Si la entidad se crea correctamente, `TaskService` usa `TaskRepository.save(nueva)` (`src/main/java/com/taskflow/repository/TaskRepository.java`) para persistirla en la BD; JPA asigna el id.
5. `TaskController` recibe la entidad creada, usa `TaskMapper.aResponse(creada)` para devolver `TaskResponse`, y responde `201 Created` con cabecera `Location: /tasks/{id}`.

Notas importantes del flujo:
- Distinción crear vs rehidratar: `TaskMapper.aEntidadNueva` usa la factory `Task.crear` (aplica regla temporal). El constructor público de `Task` es para rehidratación (no aplica la regla de fecha pasada).
- Reglas de negocio primarias residen en la entidad `Task` (p. ej. `setStatus` valida transición a `DONE`) y se exponen en firmas checked. `TaskService` orquesta y traduce excepciones a las respuestas HTTP apropiadas.

## Dónde viven las reglas de negocio

- Reglas de integridad e invariantes (título requerido, rango 3–120, `projectId` no nulo, `dueDate` no en pasado al crear) → en `Task` (`src/main/java/com/taskflow/model/Task.java`).
- Reglas de orquestación y políticas transaccionales (buscar, validar existencia de recursos externos, traducción de excepciones a errores de nivel de API) → en `TaskService` y `ProjectService` (`src/main/java/com/taskflow/service/`).
- Autorización data-driven (por ejemplo: solo owner o ADMIN puede borrar proyecto) → en `ProjectSecurity` (bean) combinado con `@PreAuthorize` en `ProjectController` y configurado en `SecurityConfig`.

Regla práctica: el dominio (entidades) contiene las reglas que deben mantenerse invariantes en cualquier camino; los servicios coordinan llamadas y traducen excepciones a semánticas HTTP.

## Seguridad (JWT)

- Flujo de autenticación:
  - Login (`/auth/login`) validado por `AuthService` que usa `AuthenticationManager` y `PasswordEncoder` (`BCrypt`).
  - `JwtService` crea/verifica tokens; `JwtAuthenticationFilter` extrae el token `Authorization: Bearer <token>`, valida firma/expiración y carga un `Authentication` en el `SecurityContext` si es válido.
- Configuración clave: `SecurityConfig` (`src/main/java/com/taskflow/config/SecurityConfig.java`)
  - Rutas públicas: `/auth/**`, Swagger (`/swagger-ui/**`), H2 console (`/h2-console/**`) y la UI estática (`/`, `/*.html`, `/css/**`, `/js/**`).
  - El endpoint de información está expuesto explícitamente por `InfoController` en `src/main/java/com/taskflow/controller/InfoController.java` (ruta `GET /info`) y su acceso también está permitido en la configuración de seguridad.
  - `sessionCreationPolicy(SessionCreationPolicy.STATELESS)` → JWT stateless.
  - Chain añade `JwtAuthenticationFilter` ANTES de `UsernamePasswordAuthenticationFilter`.
  - Errores en el filtro JWT se devuelven como 401 JSON desde el filtro (el `GlobalExceptionHandler` no ve excepciones lanzadas antes del DispatcherServlet).
- Puntos a recordar:
  - Sin token válido en un endpoint protegido → 401.
  - Usuario autenticado sin permiso (p. ej. no owner) → 403 (manejador de access denied en `SecurityConfig`).
  - Owner checks implementados con `@PreAuthorize` y el bean `@projectSecurity`.

## Organización de tests

- Unit tests (rápidos, sin Spring) en `src/test/java/com/taskflow/unit/` — p. ej. `TaskServiceTest`.
- Slice tests (`@WebMvcTest`, `@DataJpaTest`) en `src/test/java/com/taskflow/slice/` — prueban controladores y repositorios aislados con soporte de Spring test.
- Integration tests en `src/test/java/com/taskflow/integration/` — usan `@SpringBootTest` y perfil `test`. Los *IT* que requieren Testcontainers están marcados y NO corren con la ejecución normal (`mvn test`) salvo que se active `-Ddocker.tests=true`.
- Tests relevantes:
  - `src/test/java/com/taskflow/unit/TaskServiceTest.java`
  - `src/test/java/com/taskflow/slice/TaskControllerTest.java`
  - `src/test/java/com/taskflow/integration/SecurityRulesTest.java`

Comandos útiles (Windows / PowerShell):
- `mvn -q test` — correr la suite normal (no incluye los IT con Testcontainers salvo que se habiliten).
- `mvn -q test "-Dtest=TaskServiceTest"` — ejecutar una clase de test específica.
- `mvn spring-boot:run "-Dspring-boot.run.profiles=h2"` — arrancar la app con H2 y datos de ejemplo (`DataSeeder`).

## Consejos rápidos para contribuir

- No devolver entidades desde controladores: siempre DTOs (`TaskResponse`, `ProjectResponse`) mediante `TaskMapper`/`ProjectMapper`.
- Validaciones de frontera: usar `@Valid` en los `@RequestBody` y dejar que `GlobalExceptionHandler` convierta errores a respuestas uniformes.
- Reglas que deben cambiar: modificar la entidad `Task` y actualizar tests unitarios y de integración; no parchear tests para que pasen.
- Documentación y seguridad: endpoints públicos y excepciones bien definidas para facilitar debugging y frontends.

---

Archivo referenciado (ejemplos):
- `src/main/java/com/taskflow/controller/TaskController.java` (`TaskController`)
- `src/main/java/com/taskflow/service/TaskService.java` (`TaskService`)
- `src/main/java/com/taskflow/mapper/TaskMapper.java` (`TaskMapper`)
- `src/main/java/com/taskflow/model/Task.java` (`Task`)
- `src/main/java/com/taskflow/repository/TaskRepository.java` (`TaskRepository`)
- `src/main/java/com/taskflow/advice/GlobalExceptionHandler.java` (`GlobalExceptionHandler`)
- `src/main/java/com/taskflow/config/SecurityConfig.java` (`SecurityConfig`)
- `src/main/java/com/taskflow/security/JwtAuthenticationFilter.java` (`JwtAuthenticationFilter`)
- `src/main/java/com/taskflow/config/DataSeeder.java` (`DataSeeder`)

Si se desea, puedo añadir un diagrama de flujo simplificado o una versión en español/inglés para la wiki del repo.
