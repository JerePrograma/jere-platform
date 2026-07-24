# Dominio, datos, APIs, contratos e integraciones

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Contextos actuales

| Contexto | Módulo dueño | Estado | Datos propios |
|---|---|---|---|
| Tenancy y organizaciones | `kernel.tenancy` | Implementado | tenant, identity, organization, branch, membership, membership_branch |
| Identidad y sesiones | `kernel.identity` | Implementado | credential, session_family, refresh_token, bootstrap marker |
| Autorización | `kernel.authorization` | Implementado | catálogo, templates, roles, asignaciones, entitlements, flags |
| Auditoría y confiabilidad | `kernel.reliability` | Implementado | audit_event, idempotency_record, outbox_event |
| Party References | `commercial.parties` | Implementado | party_reference y progreso de snapshots |
| Platform API | `platform-api` | Composición | No debe poseer dominio ni tablas propias adicionales salvo bootstrap técnico |
| Verticales | `verticals` | Placeholder | Ninguno |

## Modelo y persistencia

### Tenancy

- `identity` es global y no contiene roles.
- `membership` enlaza una identidad con un tenant.
- organización y sucursal pertenecen al tenant.
- claves foráneas compuestas por `tenant_id` rechazan relaciones cruzadas.
- códigos de tenant e identidad externa tienen unicidad case-insensitive.
- estados de lifecycle están restringidos por constraints SQL.

### Sesiones

- credenciales separadas de identidad; password persistido únicamente como hash adaptativo;
- `invalidation_version` invalida sesiones tras reemplazo de contraseña;
- familia de sesión ligada a identidad, tenant y membership;
- refresh token persistido solo por SHA-256, con secuencia y estado;
- replay de token consumido compromete toda la familia.

### Autorización

- permisos son códigos estables y pertenecen a un módulo;
- roles son tenant-scoped y no forman una jerarquía ordinal;
- asignaciones pueden ser tenant-wide o branch-scoped;
- entitlement comercial y feature flag técnico son dimensiones independientes;
- roles base gestionados se reconocen por `system_key`; roles personalizados no se sobrescriben.

### Confiabilidad

- `audit_event` es append-only por trigger PostgreSQL;
- idempotencia se identifica por tenant + operación + hash de clave;
- request hash distinto con la misma clave produce conflicto determinista;
- outbox usa estados `PENDING`, `CLAIMED`, `RETRY`, `DISPATCHED`, `DEAD` y lease recuperable;
- cleanup genérico no elimina auditoría ni dead letters.

### Party References

- clave de mapping: `(tenant, sourceType, sourceId)`;
- campos compartidos: ID plataforma, tenant, source type/ID, nombre actual, estado y metadatos técnicos;
- perfiles, documentos, fiscalidad, guardianes, direcciones y roles comerciales no pertenecen al shared schema;
- nuevas operaciones requieren referencia activa y deben almacenar un snapshot del nombre;
- renombrar/desactivar no reescribe snapshots históricos.

## Migraciones

| Migración | Dueño lógico | Contrato principal |
|---|---|---|
| `V1` | bootstrap | esquema `platform` y marker singleton |
| `V2` | tenancy | tenant, identity, org, branch y membership |
| `V3` | identity | credenciales y lifecycle de sesiones |
| `V4` | authorization | permisos, roles, asignaciones, entitlements y flags |
| `V5-V6` | reliability | auditoría append-only, idempotencia, outbox y actor tenant-safe |
| `V7` | commercial parties | Party Reference Directory y permisos |
| `V8` | commercial parties | progreso multipágina y source IDs mínimos |

**Invariante:** Flyway es forward-only. Una corrección de schema se implementa con una migración nueva; no se reescribe V1-V8.

## Contratos HTTP

Todas las rutas `/api/**`, salvo auth y bootstrap, requieren bearer válido. La autorización efectiva se aplica en backend mediante `@PreAuthorize`.

| Método y ruta | Seguridad | Entrada relevante | Resultado principal |
|---|---|---|---|
| `POST /api/bootstrap/initialize` | `X-Bootstrap-Token`; único | tenant/org/branch, subject, password >= 12 | `201` con IDs; repetición `409` |
| `POST /api/auth/login` | Pública | `externalSubject`, `password`, `tenantCode` | access token + cookie refresh |
| `POST /api/auth/refresh` | Cookie + `X-Refresh-Intent: rotate` | sin token en JSON | token rotado o `401` |
| `POST /api/auth/logout` | Cookie + intent | — | `204`, revoca familia y limpia cookie |
| `GET /api/session` | autenticado | — | subject, tenant, identity, membership, branches, correlation |
| `GET /api/authorization/current` | `platform.session.read` | — | snapshot efectivo actual |
| `GET /api/audit` | `platform.audit.read` | `limit`, default 50 | eventos tenant-scoped y count |
| `GET /api/reliability/summary` | `platform.reliability.read` | — | resumen tenant-scoped |
| `GET /api/reliability/outbox/dead` | `platform.reliability.read` | `limit`, default 50 | dead letters |
| `POST /api/reliability/outbox/dead/{eventId}/requeue` | `platform.reliability.manage` | UUID path | `202`, boolean requeued |
| `GET /api/party-references` | `commercial.parties.read` | `q`, `includeInactive=false`, `limit=25`; rango 1..100 | resultados y count |
| `GET /api/party-references/{partyId}` | `commercial.parties.read` | UUID | referencia o `404` |
| `POST /api/party-references/imports` | `commercial.parties.manage` | `Idempotency-Key`; source/name/status | mutation y replay |
| `POST /api/party-references/reconciliation` | `commercial.parties.manage` | 1..10.000 candidatos | dry-run, no escribe |
| `POST /api/party-source-exports/reconciliation` | bearer + manage + HMAC | raw JSON y headers fuente/firma | dry-run firmado |
| `POST /api/party-source-exports/imports` | bearer + manage + HMAC | raw JSON y headers fuente/firma | `200`, `409`, `422` o error de auth/config |
| `GET /actuator/health` | Pública | — | health; probes activas |
| `GET /actuator/info` | exposición Actuator | — | información disponible de runtime |

### Headers y cookies relevantes

- `Authorization: Bearer ...`
- `X-Tenant-Code`: selector; no puede cambiar el tenant de un bearer validado.
- `X-Correlation-Id`: UUID opcional; el servidor genera y devuelve uno si falta.
- `Idempotency-Key`: obligatorio en importación individual, máximo 500 caracteres.
- `X-Bootstrap-Token`: secreto de bootstrap temporal.
- `X-Refresh-Intent: rotate`: obligatorio para refresh y logout.
- `X-Party-Source-Type`: source type canónico.
- `X-Party-Export-Signature`: `sha256=` + HMAC-SHA256 hexadecimal de los bytes exactos.
- cookie `jp_refresh`: `HttpOnly`, `SameSite=Strict`, path `/api/auth`, `Secure` configurable.

### Envelope de errores

Formato general: `{"code":"codigo_estable"}`.

Códigos verificados:

- `authentication_required`, `authentication_failed`;
- `authorization_denied`;
- `invalid_request`, `invalid_correlation_id`;
- `refresh_intent_required`;
- `bootstrap_access_denied`, `not_found` cuando bootstrap está deshabilitado;
- `idempotency_key_conflict`, `idempotency_request_in_progress`;
- `operation_conflict`;
- `party_reference_not_found`, `party_reference_inactive`;
- `party_source_authentication_failed`, `party_source_not_configured`, `invalid_party_source_export`, `party_source_tenant_mismatch`;
- `party_source_snapshot_not_found`, `party_source_snapshot_not_complete`, `party_source_page_out_of_order`, `party_source_page_conflict`.

## Contratos versionados

### Permisos

Fuente canónica: `contracts/authorization/permissions.json`. Contiene 19 códigos y se verifica contra Java y TypeScript. Cambiar un código requiere actualizar, como mínimo:

- JSON canónico;
- `PlatformPermission`;
- `frontend/apps/platform-shell/src/authorization/permissions.ts`;
- migración de catálogo/roles cuando corresponda;
- tests de contrato y autorización.

### Source types

Fuente canónica: `contracts/parties/source-types.json`:

- `GESTUDIO_STUDENT`
- `SCALARIS_THIRD_PARTY`

No existe fallback genérico. Un source type nuevo requiere cambio coordinado en JSON, Java, TypeScript, schema, configuración, migración y tests.

### Party Source Export v1

Fuente canónica: `contracts/parties/source-export-v1.schema.json`.

Invariantes:

- `contractVersion` es `1`;
- `additionalProperties: false`;
- tenant UUID y source type canónico;
- body máximo 1.000.000 bytes;
- máximo 1.000 records por artefacto;
- checkpoint máximo 160; cursor máximo 500;
- `pageNumber` y `pageCount` aparecen juntos, rango 1..1.000;
- página final equivale simultáneamente a `pageNumber == pageCount`, `fullSnapshot=true`, `nextCursor=null`;
- legacy v1 sin metadata de páginas continúa admitido, pero no prueba completitud cross-page;
- bytes firmados deben ser exactamente los enviados;
- retry exacto replay; bytes distintos para identidad aceptada producen `409`;
- una página final incompleta no escribe;
- snapshot completo reporta ausencias, pero nunca borra ni desactiva automáticamente.

## Integraciones externas

| Integración | Punto | Configuración | Semántica y riesgos |
|---|---|---|---|
| PostgreSQL 16 | datasource/JPA/JDBC/Flyway | `DATABASE_*`, Compose local | Sistema de registro actual; integración local requiere Docker. |
| Gestudio source export | `PartySourceExportController` | secretos current/previous | Emisor coordinado; operación productiva pendiente. No conexión directa a DB fuente. |
| Scalaris source export | mismo receptor | secretos current/previous | Contrato preparado; tenant mapping y emisor no verificados. |
| GitHub Actions | `.github/workflows/ci.yml` | runner/JDK/Node/GitHub token interno | CI, no despliegue. |
| Dependabot | `.github/dependabot.yml` | semanal | Solo minor/patch agrupados; majors ignorados. |

No hay cliente de email, storage, pagos, broker, webhook o servicio de observabilidad externo implementado.

## Variables de entorno

| Variable | Consumidor | Obligatoria | Default versionado | Sensible | Falta/impacto |
|---|---|---:|---|---:|---|
| `POSTGRES_DB` | Compose | local | sí, local | no | cambia DB creada |
| `POSTGRES_USER` | Compose | local | sí, local | no | cambia usuario local |
| `POSTGRES_PASSWORD` | Compose | local | placeholder | sí | no usar placeholder fuera de local |
| `POSTGRES_PORT` | Compose | no | 5432 | no | puerto local |
| `DATABASE_URL` | Spring datasource | no local; sí real | JDBC local | posible | conexión fallará si incorrecta |
| `DATABASE_USERNAME` | Spring datasource | no local; sí real | usuario local | no | autenticación DB |
| `DATABASE_PASSWORD` | Spring datasource | no local; sí real | placeholder | sí | autenticación DB |
| `AUTH_JWT_SECRET` | `AuthenticationRuntimeConfiguration` | producción | vacío | sí | vacío genera clave efímera; invalida tokens al reiniciar |
| `AUTH_ACCESS_TOKEN_LIFETIME` | política de sesión | no | `PT15M` | no | duración inválida impide binding/startup |
| `AUTH_REFRESH_TOKEN_LIFETIME` | política de sesión | no | `P30D` | no | idem |
| `AUTH_REFRESH_COOKIE_SECURE` | cookie refresh | producción debe ser true | false | no | false permite cookie sin Secure |
| `PLATFORM_BOOTSTRAP_TOKEN` | bootstrap | solo inicialización | vacío | sí | endpoint responde como no encontrado |
| `PARTY_SOURCE_GESTUDIO_CURRENT_SECRET` | lector HMAC | si fuente activa | vacío | sí | `503` si no hay secreto configurado |
| `PARTY_SOURCE_GESTUDIO_PREVIOUS_SECRET` | rotación HMAC | no | vacío | sí | elimina overlap anterior |
| `PARTY_SOURCE_SCALARIS_CURRENT_SECRET` | lector HMAC | si fuente activa | vacío | sí | `503` si no hay secreto configurado |
| `PARTY_SOURCE_SCALARIS_PREVIOUS_SECRET` | rotación HMAC | no | vacío | sí | elimina overlap anterior |

Nunca copiar valores de `.env`, logs o gestores de secretos a documentación, fixtures o issues.

## Pruebas relacionadas

- `PermissionContractTest`
- `PartySourceTypeContractTest`
- `PartySourceAdapterContractTest`
- `PartySourceExportContractTest`
- `SignedPartySourceExportReaderTest`
- `TenantBoundaryIT`
- `AuthenticationLifecycleIT`
- `AuthorizationIntegrationIT`
- `ReliabilityIntegrationIT`
- `PartyReferenceIntegrationIT`

Volver al [índice](README.md).
