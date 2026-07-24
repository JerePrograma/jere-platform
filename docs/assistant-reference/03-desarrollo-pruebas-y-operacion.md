# Desarrollo local, pruebas, CI y operación

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Requisitos

- JDK 21; Maven Enforcer rechaza Java fuera de `[21,22)`.
- Maven 3.9+.
- Node.js 22+ y npm 10+.
- Docker con Compose para PostgreSQL local y Testcontainers.

Verificar herramientas antes de atribuir un fallo al código:

```bash
java -version
mvn -version
node --version
npm --version
docker version
docker compose version
```

Existe evidencia histórica de un host Windows donde `java` resolvía Java 8 y Maven Java 21. Para este repositorio, el JDK mostrado por `mvn -version` determina la validación Maven.

## Inicio local

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
mvn -B -f backend/pom.xml -pl platform-api -am package -DskipTests
java -jar backend/platform-api/target/platform-api-0.1.0-SNAPSHOT.jar
```

Frontend en otra terminal:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

Puntos locales definidos:

- API: `http://localhost:8080`
- health: `http://localhost:8080/actuator/health`
- frontend: `http://localhost:5173`
- PostgreSQL: `localhost:5432`

**No existe** script de seed general, reset seguro de base, UI de bootstrap ni migración de datos de clientes. No inventar esos procedimientos.

## Depuración

Backend: iniciar el jar o ejecutar el módulo desde el IDE con `PlatformApplication`. Mantener variables fuera del repositorio. Para sesiones persistentes entre reinicios configurar un `AUTH_JWT_SECRET` estable de al menos 32 bytes.

Frontend: Vite proporciona hot reload. El shell actual no llama a la API; verificar cambios visuales por separado de la seguridad backend.

Base de datos: Flyway gestiona `platform`. Hibernate solo valida. Diagnósticos SQL deben respetar ownership de módulos y no convertirse en accesos cross-module productivos.

## Build, pruebas y calidad

| Comando | Propósito | Alcance | Estado en esta tarea |
|---|---|---|---|
| `mvn -B -f backend/pom.xml verify` | compilar, unit/contract, ArchUnit, integración y JaCoCo | backend completo; Docker | **No ejecutado:** sin checkout local por fallo DNS |
| `npm --prefix frontend ci` | instalación reproducible | workspace frontend | **No ejecutado** |
| `npm --prefix frontend run check` | contrato de permisos, lint, typecheck y Vitest | frontend completo | **No ejecutado** |
| `npm --prefix frontend run build` | build de todos los workspaces | frontend | **No ejecutado** |
| `./scripts/validate.sh` | secuencia integral Unix | backend + frontend | **No ejecutado** |
| `./scripts/validate.ps1` | secuencia integral PowerShell con propagación de exit code | backend + frontend | **No ejecutado** |
| `git diff --check` | whitespace y conflictos visibles | cambios Git | Ejecutado sobre staging documental temporal; ver informe final |

Evidencia histórica registrada el 2026-07-20 en `docs/current-state.md`:

- Maven verify: 11 unit/contract tests y 46 integration tests, PASS;
- frontend check: 19 permisos, lint, typecheck y 9 tests, PASS;
- frontend build, PASS;
- smoke Gestudio source export, PASS.

No usar esa evidencia para afirmar que el HEAD futuro sigue verde sin ejecutar los comandos nuevamente.

## Pruebas focalizadas

Maven Failsafe reconoce clases `*IT`. Patrón inferido y utilizado previamente en el historial:

```bash
mvn -B -f backend/pom.xml -pl platform-api -am \
  -Dit.test=TenantBoundaryIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Sustituir el nombre únicamente por una clase existente. Tras pruebas focalizadas, ejecutar `mvn verify` completo cuando Docker y tiempo lo permitan.

Matriz de tests:

| Área | Prueba principal | Qué protege |
|---|---|---|
| Arranque | `PlatformApplicationIT` | PostgreSQL 16, Flyway y health UP |
| Arquitectura | `ModuleArchitectureTest` | direcciones de dependencia y encapsulación |
| Tenancy | `TenantBoundaryIT` | membresía, aislamiento, constraints y cleanup de contexto |
| Sesiones | `AuthenticationLifecycleIT` | bootstrap, errores genéricos, cookies, rotación, replay, revocación y concurrencia |
| Autorización | `AuthorizationIntegrationIT` | 401/403, tenant, entitlement, branch, flags, roles gestionados/custom |
| Permisos | `PermissionContractTest` + frontend verifier | JSON/Java/TS sincronizados |
| Confiabilidad | `ReliabilityIntegrationIT` | atomicidad, rollback, audit, outbox, retries, lease y cleanup |
| Party References | `PartyReferenceIntegrationIT` | tenant, idempotencia, snapshots, HMAC y multipágina |
| Contrato fuente | `PartySourceExportContractTest`, `SignedPartySourceExportReaderTest` | schema, firma, rotación y validación |
| Frontend | tests en `authorization`, `parties` y `packages/ui` | contratos y primitivo UI |

## CI

`.github/workflows/ci.yml` se activa en pull requests y pushes a `main`.

- Backend: Ubuntu, Temurin 21, cache Maven, timeout 20 min, guarda últimas 200 líneas del log ante fallo.
- Frontend: Ubuntu, Node 22, npm cacheado por lockfile, timeout 15 min, install/lint/typecheck/test/build.
- Secrets: checkout completo y Gitleaks.

Dependabot revisa semanalmente Maven, npm y Actions; agrupa minor/patch y no propone majors.

**Limitaciones:** no hay SAST, SBOM, policy gate de licencias/dependencias, deploy ni aprobación de entorno.

## Infraestructura y despliegue

`infra/compose.yaml` crea únicamente `postgres:16-alpine`, readiness mediante `pg_isready` y volumen nombrado. No es un stack productivo.

No hay pipeline de publicación, contenedor de aplicación, registro de imágenes, migración previa al deploy, rollback, backups, restore ni DR. Todo procedimiento que los mencione debe marcarse como diseño pendiente.

## Observabilidad

Controles actuales:

- Actuator `health` e `info`;
- probes de health activas;
- correlation ID en contexto/respuestas;
- auditoría tenant-scoped;
- resumen de confiabilidad y dead letters;
- contadores Micrometer de idempotencia y party-source.

Ausencias:

- exportador de métricas configurado;
- dashboards y alertas;
- schema de logs estructurados;
- tracing backend;
- runbook de incidentes productivos.

## Limpieza segura

Para detener PostgreSQL sin borrar datos:

```bash
docker compose --env-file .env -f infra/compose.yaml down
```

La eliminación del volumen borra datos locales y no debe proponerse como primer paso. No existe un comando oficial de reset. Antes de cualquier limpieza destructiva, confirmar explícitamente que los datos son prescindibles.

## Validación de documentación

No existe markdownlint configurado. Para cambios documentales:

1. revisar enlaces relativos;
2. verificar rutas y símbolos contra el repositorio;
3. buscar secretos y placeholders sospechosos;
4. ejecutar `git diff --check`;
5. confirmar que solo cambió documentación;
6. ejecutar validaciones generales cuando el entorno lo permita.

Volver al [índice](README.md).
