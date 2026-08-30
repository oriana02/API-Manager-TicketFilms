# API Manager — TicketFilms

Gateway central del proyecto **TicketFilms**, construido con **Spring Cloud Gateway** (WebFlux/reactivo). Expone únicamente las rutas necesarias hacia los tres microservicios del backend (`ms-cartelera`, `ms-asientos`, `ms-boletos`), valida JWT en las rutas protegidas y gestiona CORS para el frontend.

Forma parte de la arquitectura cloud del proyecto: Frontend (React + Vite, en Vercel) → **API Manager** (este servicio) → Microservicios Spring Boot (EC2) → Amazon RDS MySQL, con autenticación federada Google → Amazon Cognito.

## Arquitectura

```
Cliente (React + Vite)
        │  HTTPS + JWT
        ▼
   API Manager  (Spring Cloud Gateway)
   ├── JWT validation (OAuth2 Resource Server / Cognito)
   ├── CORS (solo origen del frontend)
   └── Routing declarativo
        │
        ├──► ms-cartelera   (público, solo GET)
        ├──► ms-asientos    (protegido)
        └──► ms-boletos     (protegido)
```

## Tecnologías

| Componente | Detalle |
|---|---|
| Spring Boot | 3.2.5 |
| Spring Cloud Gateway | 2023.0.1 (BOM `spring-cloud-dependencies`) |
| Seguridad | Spring Security (reactivo) + OAuth2 Resource Server |
| Emisor de JWT | Amazon Cognito User Pool (federado con Google Identity) |
| Java | 21 |
| Build | Maven |

## Estructura del proyecto

```
api-manager/
├── pom.xml
└── src/
    └── main/
        ├── java/com/ticketfilms/api_manager/
        │   ├── ApiManagerApplication.java
        │   └── config/
        │       └── SecurityConfig.java      # Reglas JWT, rutas públicas/protegidas, logging 401/403
        └── resources/
            └── application.yml              # Routing, CORS, issuer-uri de Cognito
```

> Este servicio no tiene controllers propios: el enrutamiento hacia los microservicios es 100% declarativo, definido en `application.yml`.

## Configuración (`application.yml`)

### Routing

Cada ruta mapea un prefijo público hacia el microservicio interno correspondiente, quitando el prefijo antes de reenviar (`StripPrefix=1`):

| Prefijo expuesto | Destino interno | Acceso |
|---|---|---|
| `/api/cartelera/**` | `ms-cartelera:8081` | Público (solo `GET`) |
| `/api/asientos/**` | `ms-asientos:8082` | Requiere JWT |
| `/api/boletos/**` | `ms-boletos:8083` | Requiere JWT |

### CORS

Restringido únicamente a los orígenes del frontend (producción y desarrollo local). Ajustar `allowedOrigins` en `application.yml` si cambia el dominio de despliegue.

### JWT / OAuth2 Resource Server

El Gateway actúa como **Resource Server**: no emite tokens, solo valida los que emite Cognito contra su JWKS público, usando:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://cognito-idp.<region>.amazonaws.com/<userPoolId>
```

### Actuator

Habilitado para exponer estado del servicio y las rutas configuradas (útil como evidencia de la configuración del API Manager):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,gateway,info
  endpoint:
    gateway:
      enabled: true
```

## Cómo ejecutar

Prerrequisitos: Java 21, Maven.

```bash
mvn clean compile
mvn spring-boot:run
```

El servicio queda escuchando en `http://localhost:8080`.

## Endpoints de verificación

**Estado del servicio:**
```bash
curl http://localhost:8080/actuator/health
```

**Rutas configuradas** (config del API Manager):
```bash
curl -s http://localhost:8080/actuator/gateway/routes | python -m json.tool
```

**Ruta pública sin token** (debe responder 200 si el backend destino está activo):
```bash
curl -i http://localhost:8080/api/cartelera/eventos
```

**Ruta protegida sin token** (debe responder 401):
```bash
curl -i http://localhost:8080/api/boletos/mis-boletos
```

**Ruta protegida con token válido de Cognito** (debe responder 200):
```bash
curl -i http://localhost:8080/api/boletos/mis-boletos \
  -H "Authorization: Bearer <ID_TOKEN_DE_COGNITO>"
```

Los intentos con token ausente o inválido quedan registrados en los logs de la aplicación (nivel `WARN`, con path y motivo), y los JWT válidos quedan registrados en nivel `INFO` con el `subject` e `issuer`.

## Requerimientos cubiertos (rúbrica)

| ID | Descripción | Dónde se implementa |
|---|---|---|
| RF-13 | Exponer solo las rutas necesarias hacia los 3 microservicios | `spring.cloud.gateway.routes` en `application.yml` |
| RF-14 | Validar JWT en rutas protegidas, rechazando inválidas | `SecurityConfig` — `oauth2ResourceServer` |
| RF-15 | CORS configurado solo para el origen del frontend | `spring.cloud.gateway.globalcors` |
| RF-16 | Acceso sin autenticación a rutas públicas de consulta | `SecurityConfig` — `permitAll()` en `/api/cartelera/**` (GET) |
| RF-17 | Exigir autenticación antes de seleccionar asientos o confirmar compra | `SecurityConfig` — `authenticated()` en `/api/asientos/**` y `/api/boletos/**` |
| RNF-01 | Comunicación vía HTTPS con JWT validado en el Gateway | Configuración de despliegue (HTTPS en EC2/Load Balancer) + Resource Server |
| RNF-07 | Registro de errores 401/403 en logs | `authenticationEntryPoint` / `accessDeniedHandler` en `SecurityConfig` |

## Pendiente / próximos pasos

- [ ] Probar round-trip completo con `ms-boletos`, `ms-asientos` y `ms-cartelera` levantados
- [ ] Reemplazar `uri` de cada ruta por las IPs/DNS internos reales una vez desplegados en EC2
- [ ] Confirmar `issuer-uri` real una vez creado el User Pool de Cognito
- [ ] Verificar el flujo completo: login Google → Cognito → JWT → Gateway → microservicio
