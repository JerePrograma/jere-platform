# Seguridad, invariantes y convenciones

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Controles de seguridad existentes

### Autenticación y sesiones

- API stateless; sin sesión HTTP, form login ni HTTP Basic.
- JWT HMAC-SHA256 con issuer `jere-platform`.
- Claims de referencia: sesión, identidad, tenant, membership y credential version; no roles ni permisos.
- Cada bearer se valida contra estado vigente de PostgreSQL.
- contraseña con delegating adaptive password encoder.
- refresh token aleatorio, persistido solo como SHA-256, one-time y rotado bajo lock.
- replay compromete familia completa; logout revoca familia completa.
- cookie refresh `HttpOnly`, `SameSite=Strict`, path reducido y `Secure` configurable.
- bootstrap único, transaccional y protegido por secreto comparado en tiempo constante.
- errores de credenciales no revelan si la identidad existe.

### Tenancy y autorización

- contexto tenant derivado de sesión/membership activa, no de body.
- `X-Tenant-Code` no puede cambiar el tenant de un bearer.
- constraints PostgreSQL compuestas por tenant como segunda defensa.
- roles tenant-scoped; permisos explícitos; branch scope validado.
- entitlement, permission, feature flag y configuración son conceptos separados.
- seguridad backend autoritativa; ocultar UI no concede seguridad.

### Confiabilidad y datos sensibles

- idempotency key persistida solo como hash.
- audit metadata sanitizada; mensajes de excepción y payload completo no se persisten.
- auditoría append-only por trigger.
- outbox evita side effects de red dentro de la transacción de negocio.
- HMAC se verifica sobre bytes exactos antes de parsear JSON.
- secretos current/previous permiten rotación y deben tener al menos 32 bytes.
- progreso de snapshots no persiste payload, firma ni nombres.
- CI usa Gitleaks con historial completo.

## Controles parciales o ausentes

| Severidad | Hallazgo | Estado y evidencia | Recomendación |
|---|---|---|---|
| Crítico | Repositorio público puede exponer lógica/datos sensibles | Control documental ADR 0002/SECURITY; no es una barrera técnica | Cambiar visibilidad o separar código antes de lógica vertical propietaria |
| Alto | No hay backup/restore ni despliegue productivo | Ausente | Diseñar antes de producción y probar restauración |
| Alto | No hay rate limiting, MFA ni account recovery | Diferido explícitamente | Añadir con threat model y pruebas antes de exposición comercial |
| Alto | Secret manager productivo no está configurado | Variables existen; operación ausente | Integrar gestor externo y rotación auditable |
| Medio | CSRF deshabilitado globalmente | Refresh mitigado por SameSite Strict + intent; otras cookies futuras no están cubiertas | Reevaluar al incorporar nuevos flujos cookie-authenticated |
| Medio | CORS efectivo no está explícito en el repo revisado | `cors(cors -> {})`; **No verificado** fuera de la clase | Definir allowlist por entorno y probar preflight |
| Medio | Cookie Secure default false | Adecuado solo local | Forzar true en perfiles no locales |
| Medio | JWT secret vacío genera clave efímera | Warning de runtime | Fallar startup en perfil productivo si falta |
| Medio | No hay SAST, SBOM ni policy gate | CI solo build/test/Gitleaks | Añadir gates según riesgo y licencias |
| Medio | Retención de snapshot progress pendiente | ADR 0010 | Diseñar política sin borrar checkpoints activos |
| Bajo | `/actuator/info` está expuesto | Configuración actual | Revisar contenido real antes de producción |
| Informativo | Otros paths no `/api/**` están `permitAll` | Frontend/recursos actuales | Revisar al añadir endpoints fuera de `/api` |

No se emite certificación de seguridad. Los controles se describen únicamente según código y tests revisados.

## Invariantes de compatibilidad

| Invariante | Evidencia | Componentes afectados | Riesgo | Prueba relacionada |
|---|---|---|---|---|
| `kernel` no depende de negocio | ArchUnit y POM | todos los módulos backend | Alto | `ModuleArchitectureTest` |
| `commercial-core` no depende de verticales | ArchUnit | commercial/verticals | Alto | `ModuleArchitectureTest` |
| paquetes `internal` no son API | ArchUnit | kernel/commercial consumidores | Alto | `ModuleArchitectureTest` |
| tenant context es confiable y obligatorio | ADR 0003, filtro, constraints | request/persistencia | Crítico | `TenantBoundaryIT` |
| identity no posee autoridad global | modelo/ADR | tenancy, authz, sesiones | Crítico | `AuthorizationIntegrationIT` |
| roles no forman jerarquía ordinal | ADR 0005 y matriz explícita | autorización | Alto | `AuthorizationIntegrationIT` |
| entitlements y flags no conceden permisos | ADR 0005 | autorización/frontend | Alto | `AuthorizationIntegrationIT` |
| access JWT no contiene autorización mutable | `JwtTokenService`, ADR 0004 | sesiones/autorización | Alto | `AuthenticationLifecycleIT` |
| refresh es one-time y replay revoca familia | modelo/servicio | autenticación | Crítico | `AuthenticationLifecycleIT` |
| idempotencia: misma clave + distinto request = 409 | executor/error handler | mutaciones confiables | Alto | `ReliabilityIntegrationIT`, `PartyReferenceIntegrationIT` |
| business + audit + outbox + response son atómicos | ADR 0006 | mutaciones confiables | Crítico | `ReliabilityIntegrationIT` |
| audit es append-only | trigger V5 | auditoría | Alto | `ReliabilityIntegrationIT` |
| dispatcher externo tolera at-least-once | outbox | futuros consumidores | Alto | contrato ADR 0006 |
| source type está cerrado y versionado | JSON/Java/TS/SQL | integración parties | Alto | contract tests |
| Party Reference no contiene perfil vertical | V7/ADR 0008 | shared schema/API | Alto | `PartyReferenceIntegrationIT` |
| nombres históricos son snapshots | `PartyRef`/service | futuros docs/finanzas | Alto | `PartyReferenceIntegrationIT` |
| import unchanged no publica evento | service/ADR 0008 | outbox | Medio | `PartyReferenceIntegrationIT` |
| HMAC usa bytes exactos antes de parsear | reader/ADR 0009 | integración | Crítico | export tests |
| páginas nuevas son ordinales y completas | ADR 0010/V8 | snapshots | Alto | `PartyReferenceIntegrationIT` |
| artefactos v1 legacy siguen aceptados | schema/ADR 0010 | compatibilidad fuente | Alto | contract tests |
| migraciones V1-V8 son inmutables | política Flyway | persistencia | Crítico | `PlatformApplicationIT` + disciplina |
| frontend no es frontera de autorización | ADR 0005 | UI/API | Alto | integración backend |
| repo público no recibe datos/lógica sensible | ADR 0002/SECURITY | todo el repo | Crítico | Gitleaks parcial; revisión humana |

## Convenciones de implementación extraídas

### Backend

- paquetes por capacidad: `api`, `application`, `internal`; `domain`/`infrastructure` solo cuando exista necesidad real;
- constructor injection; campos `final`; records para DTO/IDs/value-like contracts;
- servicios de aplicación concentran orquestación y transacciones;
- persistencia interna no se expone como entidad compartida;
- `TenantContext` se pasa explícitamente en operaciones tenant-owned;
- errores de dominio específicos se traducen a códigos HTTP estables en advice;
- argumentos públicos se validan temprano y con límites explícitos;
- tiempo inyectado mediante `Clock` donde afecta lifecycle/concurrencia;
- side effects externos se representan con outbox;
- métricas usan tags cerrados, nunca payloads o secretos.

### SQL

- schema explícito `platform`;
- UUID como identificador principal;
- timestamps con zona;
- `version` para concurrencia cuando corresponde;
- checks para estados y coherencia temporal;
- unicidad tenant-aware;
- FKs compuestas cuando preservan ownership;
- migraciones forward-only, pequeñas y dueñas de una capacidad.

### Frontend

- ESM y workspaces npm;
- TypeScript estricto según tsconfig del repo;
- componentes funcionales y props tipadas;
- contratos de autorización/parties sincronizados con artefactos canónicos;
- UI accesible básica (`aria-labelledby`), sin asumir que un control oculto autoriza.

### Pruebas

- integración real con PostgreSQL 16/Testcontainers para persistencia, concurrencia y HTTP;
- tests de arquitectura y contratos separados;
- reloj ajustable en lifecycle;
- fixtures sintéticas y anonimizadas;
- asserts de 401 frente a 403 y no filtración cross-tenant;
- no reemplazar pruebas PostgreSQL por mocks cuando el invariante depende de locks/constraints.

### Commits

El historial usa prefijos como `ARCH:`, `DOCS:` y merges descriptivos. Para cambios nuevos sin convención inequívoca, usar Conventional Commits en minúscula, por ejemplo `docs: add assistant reference`, manteniendo commits focalizados.

## Reglas que no deben relajarse sin ADR

- monolito modular antes que microservicios;
- base PostgreSQL como source of truth actual;
- ninguna importación wholesale desde repos fuente;
- ninguna entidad JPA compartida entre módulos;
- ninguna abstracción genérica sin al menos dos usos reales actuales;
- datos financieros futuros con idempotencia e historia compensatoria;
- lógica propietaria fuera del repo público;
- contratos públicos sincronizados y testeados.

Volver al [índice](README.md).
