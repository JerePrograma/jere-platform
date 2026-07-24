# Glosario, símbolos importantes y trazabilidad

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Glosario

| Término | Significado en este proyecto |
|---|---|
| Foundation runtime | base ejecutable técnica; no producto ni producción |
| Tenant | aislamiento principal de cliente SaaS |
| Identity | sujeto global autenticable, sin autoridad de negocio |
| Membership | relación identity-tenant y unidad de autorización |
| Organization | organización tenant-owned |
| Branch | sucursal/location tenant-owned y posible scope de permiso |
| TenantContext | contexto confiable con tenant, identity, membership, branches y correlation |
| Permission | código estable de una acción |
| Role | conjunto tenant-scoped de permisos; no jerarquía ordinal |
| Entitlement | habilitación comercial de un módulo para el tenant |
| Feature flag | control técnico de rollout; no permiso ni entitlement |
| Managed role | rol base con `system_key`, reconciliable por versión |
| Custom role | rol del tenant sin `system_key`, preservado por reconciliación |
| Reliable command | frontera para mutación idempotente, auditada y transaccional |
| Idempotency key | clave cliente, persistida solo como hash |
| Outbox | trabajo externo durable registrado en la transacción local |
| DEAD | evento que agotó retries y requiere intervención |
| Party Reference | referencia mínima a perfil vertical, no perfil compartido |
| PartyRef snapshot | nombre emitido preservado para historia |
| Source type | origen cerrado/versionado de una referencia |
| Checkpoint | identidad inmutable de una extracción fuente |
| Full snapshot | artefacto que declara completitud y permite reportar ausencias |
| Replay | respuesta previamente completada sin repetir efecto |
| Foundation/target | “target” en docs puede ser arquitectura futura; no confundir con estado actual |
| Gestudio | producto fuente de estudiantes y evidencia comercial; no dependencia runtime |
| Scalaris | nombre usado para el source type comercial; integración efectiva pendiente de confirmar |

## Índice de símbolos

### Bootstrap y configuración

- `backend/platform-api/src/main/java/com/jereplatform/platform/PlatformApplication.java → PlatformApplication.main()` — entrypoint Spring Boot.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/AuthenticationRuntimeConfiguration.java → AuthenticationRuntimeConfiguration` — encoder, Clock, política y JWT.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/SecurityRuntimeProperties.java → SecurityRuntimeProperties` — binding `platform.security`.
- `backend/platform-api/src/main/resources/application.yml` — datasource, Flyway, seguridad, Actuator y shutdown.

### Tenancy

- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/tenancy/api/TenantContext.java → TenantContext` — frontera confiable tenant/actor/branches/correlation.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/tenancy/api/TenantContextHolder.java → TenantContextHolder` — scope de request; debe limpiarse.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/tenancy/application/TenantAccessService.java → TenantAccessService.activate()` — resuelve membership y tenant.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/tenancy/application/TenantProvisioningService.java → TenantProvisioningService` — crea tenant/org/branch/identity/membership.
- `backend/platform-api/src/main/java/com/jereplatform/platform/tenancy/TenantContextFilter.java → TenantContextFilter.doFilterInternal()` — integra contexto con HTTP.

### Identidad y sesiones

- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/identity/application/AuthenticationService.java → AuthenticationService.login()/refresh()/logout()` — lifecycle de sesión.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/identity/application/SessionValidationService.java → SessionValidationService.validate()` — valida bearer contra PostgreSQL.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/identity/application/CredentialRegistrationService.java → CredentialRegistrationService` — registro/reemplazo de contraseña.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/JwtTokenService.java → JwtTokenService.issue()/verify()` — JWT de referencias, sin roles.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/AccessTokenAuthenticationFilter.java → AccessTokenAuthenticationFilter` — autentica bearer.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/RefreshCookieService.java → RefreshCookieService` — cookie e intent header.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/AuthenticationController.java → AuthenticationController` — login/refresh/logout.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/BootstrapController.java → BootstrapController.initialize()` — bootstrap único.
- `backend/platform-api/src/main/java/com/jereplatform/platform/identity/SessionController.java → SessionController.current()` — sesión actual.

### Autorización

- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/authorization/api/PlatformPermission.java → PlatformPermission` — enum canónico Java.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/authorization/application/AuthorizationService.java → AuthorizationService` — snapshot y decisión efectiva.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/authorization/application/AuthorizationAdministrationService.java → AuthorizationAdministrationService` — roles, assignments, entitlements y flags.
- `backend/platform-api/src/main/java/com/jereplatform/platform/authorization/PlatformAuthorizationFacade.java → PlatformAuthorizationFacade` — bridge para `@PreAuthorize`.
- `backend/platform-api/src/main/java/com/jereplatform/platform/authorization/AuthorizationController.java → AuthorizationController.current()` — snapshot HTTP.
- `contracts/authorization/permissions.json` — fuente canónica multi-lenguaje.

### Confiabilidad

- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/ReliableCommandExecutor.java → ReliableCommandExecutor.execute()` — idempotencia + transacción + audit.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/AuditWriter.java → AuditWriter` — éxito, failure sanitizado y replay.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/OutboxService.java → OutboxService.enqueue()` — alta de evento en transacción.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/OutboxWorker.java → OutboxWorker.runOnce()` — claim, dispatch, retry/dead.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/ReliabilityAdministrationService.java → ReliabilityAdministrationService` — summary/dead/requeue tenant-scoped.
- `backend/modules/kernel/src/main/java/com/jereplatform/kernel/reliability/application/ReliabilityCleanupService.java → ReliabilityCleanupService.cleanup()` — retención conservadora.
- `backend/platform-api/src/main/java/com/jereplatform/platform/reliability/AuditController.java → AuditController.latest()`.
- `backend/platform-api/src/main/java/com/jereplatform/platform/reliability/ReliabilityController.java → ReliabilityController`.

### Party References e integración

- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/api/PartyId.java → PartyId` — ID plataforma.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/api/PartyRef.java → PartyRef` — snapshot emitible.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/api/PartySourceType.java → PartySourceType` — source types cerrados.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/application/PartyReferenceService.java → PartyReferenceService` — import/find/search/snapshot.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/application/PartyReconciliationService.java → PartyReconciliationService.analyze()` — dry-run.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/application/PartySourceSnapshotService.java → PartySourceSnapshotService` — progreso multipágina.
- `backend/modules/commercial-core/src/main/java/com/jereplatform/commercial/parties/internal/persistence/PartyReferenceStore.java → PartyReferenceStore` — persistencia interna.
- `backend/platform-api/src/main/java/com/jereplatform/platform/parties/PartyReferenceController.java → PartyReferenceController`.
- `backend/platform-api/src/main/java/com/jereplatform/platform/parties/PartySourceExportController.java → PartySourceExportController`.
- `backend/platform-api/src/main/java/com/jereplatform/platform/parties/SignedPartySourceExportReader.java → SignedPartySourceExportReader.read()` — HMAC + parse/validación.
- `contracts/parties/source-export-v1.schema.json` — schema canónico.

### Frontend

- `frontend/apps/platform-shell/src/main.tsx` — bootstrap React.
- `frontend/apps/platform-shell/src/App.tsx → App` — shell estático.
- `frontend/packages/ui/src/index.tsx → PlatformCard` — primitivo UI actual.
- `frontend/apps/platform-shell/src/authorization/permissions.ts` — tuple de permisos.
- `frontend/apps/platform-shell/src/authorization/authorization.ts` — helpers de autorización frontend.
- `frontend/apps/platform-shell/src/parties/party-reference.ts` — contratos parties frontend.
- `frontend/scripts/verify-permission-contract.mjs` — verificador JSON/TS.

### Tests clave

- `ModuleArchitectureTest`
- `PlatformApplicationIT`
- `TenantBoundaryIT`
- `AuthenticationLifecycleIT`
- `AuthorizationIntegrationIT`
- `PermissionContractTest`
- `ReliabilityIntegrationIT`
- `PartyReferenceIntegrationIT`
- `PartySourceExportContractTest`
- `SignedPartySourceExportReaderTest`
- tests frontend `authorization.test.ts`, `party-reference.test.ts`, `packages/ui/src/index.test.ts`

## Matriz de trazabilidad

| Capacidad | Entrada | Implementación | Persistencia/contrato | Prueba | Config/docs |
|---|---|---|---|---|---|
| Arranque/health | `PlatformApplication.main`, Actuator | `platform-api` | V1 y datasource | `PlatformApplicationIT` | `application.yml`, README |
| Bootstrap | `POST /api/bootstrap/initialize` | `BootstrapController`, `BootstrapAdministrationService` | marker, V2-V4 | `AuthenticationLifecycleIT` | `PLATFORM_BOOTSTRAP_TOKEN`, ADR 0004 |
| Login/session | `/api/auth/*`, `/api/session` | auth services, JWT/filter/cookie | V3 | `AuthenticationLifecycleIT` | `AUTH_*`, ADR 0004 |
| Tenant boundary | bearer + optional tenant header | `TenantContextFilter`, `TenantAccessService` | V2 composite FKs | `TenantBoundaryIT` | ADR 0003 |
| Authorization | `@PreAuthorize`, `/current` | `AuthorizationService`, facade/admin | V4/V7 + permissions JSON | `AuthorizationIntegrationIT`, contract tests | ADR 0005 |
| Audit/idempotencia | reliable command | executor/audit stores | V5-V6 | `ReliabilityIntegrationIT` | ADR 0006 |
| Outbox/recovery | reliability endpoints/worker | outbox service/worker/admin | V5 | `ReliabilityIntegrationIT` | ADR 0006 |
| Party reference | `/api/party-references` | reference/reconciliation services | V7, source types | `PartyReferenceIntegrationIT` | ADR 0007-0008 |
| Export firmado | `/api/party-source-exports` | reader/controller/service | schema v1, V8 | export/reader/party IT | secrets source, ADR 0009-0010 |
| Frontend shell | Vite entry | `App`, `PlatformCard` | permisos/source types TS | Vitest/frontend check | package scripts |
| CI | push `main`/PR | GitHub Actions | logs y Gitleaks | ejecución workflow | `.github/workflows/ci.yml` |
| PostgreSQL local | Compose | postgres image/health | named volume | Testcontainers independiente | `infra/compose.yaml` |

## Qué revisar según una modificación

- Sesiones: controllers → cookie/JWT → services → V3 → `AuthenticationLifecycleIT`.
- Permiso nuevo: JSON → enum Java → TS → migración → roles gestionados → tests.
- Tabla tenant-owned: módulo dueño → API/application → SQL con tenant FKs → integración cross-tenant.
- Party source: schema → reader → service/snapshot → V8 → fixtures → contract/IT → docs de integración.
- Outbox: draft/payload cerrado → transacción → worker/retries → métricas → test de replay/lease.
- Frontend: package dueño → contrato → accessibility → check/build; no usarlo como prueba de autorización.

Volver al [índice](README.md).
