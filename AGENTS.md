# Contexto rápido para agentes

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Propósito

Jere Platform es la base técnica compartida para varios productos SaaS verticales. El estado actual es un **foundation runtime ejecutable**, no un producto terminado ni un entorno productivo.

Documentación ampliada: [`docs/assistant-reference/README.md`](docs/assistant-reference/README.md).

## Stack y módulos

- Java 21, Spring Boot 3.5.7, Maven.
- PostgreSQL 16, Flyway V1-V8, JPA/JDBC.
- React 18, TypeScript 5.7, Vite 6, npm workspaces.
- `kernel`: tenancy, identidad, autorización y confiabilidad.
- `commercial-core`: Party Reference Directory.
- `verticals`: placeholder, sin casos de uso.
- `platform-api`: composición HTTP/security; no es dueño de negocio.
- `platform-shell`: frontend estático, sin autenticación ni llamadas API.

## Fuente de verdad y límites

- Inspeccionar código, tests, configuración, migraciones y referencias antes de editar.
- No importar repositorios fuente completos.
- No compartir entidades JPA ni consultar tablas de otro módulo.
- No importar paquetes `internal` desde fuera de su módulo.
- No agregar abstracciones genéricas sin dos usos actuales demostrados.
- No extraer microservicios sin ADR y evidencia operativa.
- Todo dato tenant-owned debe incluir aislamiento y unicidad tenant-aware.
- Identity, membership, role, entitlement, feature flag y configuración son conceptos distintos.
- Access JWT no contiene roles ni permisos; autorización backend consulta PostgreSQL actual.
- Mutaciones reintentables usan idempotencia, auditoría y outbox.
- Migraciones publicadas son forward-only.
- El repositorio es público y propietario: no incorporar lógica vertical sensible, datos reales, secretos, dumps ni infraestructura privada.

## Comandos esenciales

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
mvn -B -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build
```

Validación integral:

```bash
./scripts/validate.sh
```

```powershell
./scripts/validate.ps1
```

Docker debe estar operativo para Testcontainers. No afirmar que una validación pasó si no fue ejecutada.

## Flujo Git obligatorio

Trabajar directamente sobre `main` y publicar en `origin/main`, salvo instrucción explícita en contrario.

1. `git status`; comprobar rama, remoto y upstream.
2. Confirmar `main` y `origin/main`.
3. `git fetch origin`.
4. `git pull --ff-only origin main`.
5. Detenerse ante cambios locales previos ajenos.
6. Implementar solo el alcance solicitado.
7. Ejecutar pruebas focalizadas y generales viables.
8. Revisar `git diff`, ejecutar `git diff --check` y buscar secretos.
9. Stagear solo archivos relacionados y crear commits pequeños.
10. `git fetch origin`; si avanzó, rebasar solo commits locales no publicados.
11. Ante conflictos dudosos, detenerse.
12. `git push origin main` y confirmar divergencia `0 0`.

Prohibido sin solicitud explícita: ramas, PR, forks, worktrees, stash, `git reset --hard`, `git clean` destructivo y force push.

## Validación mínima por cambio

- Backend: tests del módulo + `mvn verify`.
- Tenant/persistencia/concurrencia: integración PostgreSQL/Testcontainers.
- Autorización: pruebas 401/403, tenant, entitlement y branch scope.
- API: conservar rutas, status, errores y contratos.
- Frontend: `npm run check` y `npm run build`.
- Migración: nueva versión Flyway; no editar V1-V8.
- Integración externa: contrato versionado, tenant binding, idempotencia, retry y outbox.
- Documentación: actualizar autorreferencia si cambia una afirmación estable.

## Invariantes principales

- `kernel` no depende de negocio; `commercial-core` no depende de `verticals`.
- `platform-api` adapta HTTP y compone módulos; no acumula lógica de dominio.
- Tenant context se deriva de sesión/membership; el cliente no lo autoriza.
- Auditoría es append-only.
- Same idempotency key + different content produce conflicto.
- Outbox implica entrega at-least-once; consumers deben ser idempotentes.
- Party Reference contiene referencia mínima, nunca perfil vertical.
- Source export se firma sobre bytes exactos y preserva v1 legacy.
- Cambios de contratos JSON/Java/TypeScript se hacen coordinadamente.

## Antes de terminar

- revisar interfaces, consumidores, pruebas y efectos secundarios;
- no introducir refactors generales ni upgrades no solicitados;
- no dejar TODO, código provisional o mocks de producción;
- documentar comandos reales y limitaciones;
- confirmar working tree limpio y sincronizado.
