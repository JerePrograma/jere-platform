# Guía de cambios y flujo Git obligatorio

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Procedimiento para modificar el proyecto

1. Definir el comportamiento esperado y lo que queda fuera de alcance.
2. Localizar el símbolo por nombre y referencias, no solo por número de línea.
3. Leer interfaces, tipos, clases base, constructor e inyección.
4. Buscar todos los llamadores y consumidores.
5. Revisar persistencia, migraciones, configuración y contratos relacionados.
6. Leer pruebas existentes antes de editar el código productivo.
7. Identificar efectos secundarios, transacciones, auditoría, outbox y autorización.
8. Evaluar compatibilidad HTTP, datos persistidos, eventos y artefactos versionados.
9. Implementar el cambio mínimo coherente.
10. Añadir o actualizar pruebas focalizadas.
11. Ejecutar validaciones generales razonablemente viables.
12. Revisar diff completo, secretos, lockfiles y documentación.

## Checklist por tipo de cambio

### Backend

- ¿El módulo dueño es correcto?
- ¿Se importan solo contratos públicos?
- ¿`TenantContext` es obligatorio donde corresponde?
- ¿La transacción tiene límites claros?
- ¿Los errores conservan código y status?
- ¿La mutación requiere idempotencia/audit/outbox?
- ¿La concurrencia depende de PostgreSQL real?

### Frontend

- ¿El cambio pertenece al shell o a `packages/ui`?
- ¿El contrato TypeScript sigue al canónico?
- ¿La UI no se presenta como autorización?
- ¿Hay typecheck, lint y tests?
- ¿Se preserva accesibilidad y navegación por teclado?

### Base de datos

- ¿Se agrega una migración nueva en lugar de editar V1-V8?
- ¿La tabla tiene dueño de módulo?
- ¿Incluye `tenant_id` y unicidad tenant-aware?
- ¿Las FKs impiden ownership cruzado?
- ¿Estados y timestamps tienen constraints?
- ¿Hay estrategia forward-recovery?

### API

- ¿Método/ruta/status/body/error siguen compatibles?
- ¿Autenticación y permiso son correctos?
- ¿El tenant se deriva de contexto?
- ¿Validaciones y límites están documentados?
- ¿Necesita idempotency key?
- ¿Tests distinguen 401, 403, 404 y 409?

### Integración externa

- ¿Existe contrato versionado y owner?
- ¿Autenticación, tenant binding, timeout y retry son explícitos?
- ¿El consumer tolera at-least-once?
- ¿Los side effects salen por outbox?
- ¿No se registran secretos o payload sensible?
- ¿Hay reconciliación/dry-run y recuperación no destructiva?

### Configuración

- ¿La variable está en `.env.example` sin valor sensible?
- ¿Tiene default únicamente si es seguro?
- ¿Falla de manera clara fuera de local?
- ¿Está documentado el consumidor y formato?

### Infraestructura

- ¿El archivo existe realmente, o se está proponiendo una topología futura?
- ¿Hay health, rollback/forward recovery, secrets y observabilidad?
- ¿No se confunde Compose local con producción?

### Seguridad

- ¿Aumenta superficie pública o datos sensibles?
- ¿Cambia CORS, CSRF, cookies, token, permisos o tenant scope?
- ¿Los errores filtran existencia o ownership?
- ¿Las pruebas incluyen bypass directo HTTP?
- ¿Gitleaks y revisión manual están limpios?

### Corrección de errores

- Reproducir y agregar test de regresión primero cuando sea viable.
- Corregir la causa, no silenciar la excepción.
- No ampliar a refactor general.
- Verificar casos vecinos y compatibilidad histórica.

### Refactor focalizado

- Sin cambio observable, schema, contratos ni eventos.
- Mantener o mejorar cobertura del comportamiento existente.
- No mezclar renombrados/reformatos masivos con cambios funcionales.

## Selección de pruebas

- módulo/paquete puro: unit o contract test más cercano;
- boundary entre módulos: `ModuleArchitectureTest`;
- SQL, locks, tenant o transacciones: integración Testcontainers;
- auth/session: `AuthenticationLifecycleIT`;
- autorización: `AuthorizationIntegrationIT`;
- confiabilidad: `ReliabilityIntegrationIT`;
- parties/export: `PartyReferenceIntegrationIT` y contract tests;
- frontend: `npm --prefix frontend run check` y build.

Después de focalizadas, ejecutar la suite completa soportada siempre que el entorno permita Docker.

## Flujo Git obligatorio sobre `main`

Este proyecto trabaja deliberadamente sobre `main` y publica directamente en `origin/main`, salvo instrucción explícita en contrario.

### Antes de editar

```bash
git status
git branch --show-current
git remote -v
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
git fetch origin
git pull --ff-only origin main
```

Comprobar:

- rama activa exactamente `main`;
- remoto `origin` correcto;
- upstream `origin/main`;
- working tree sin cambios previos ajenos.

Si hay cambios locales ajenos: detenerse. No descartarlos, sobrescribirlos, incluirlos, moverlos ni usar stash salvo solicitud explícita.

### Prohibiciones

- no crear ramas;
- no crear pull requests ni forks;
- no crear worktrees;
- no usar stash salvo solicitud explícita;
- no usar `git reset --hard`;
- no usar `git clean` destructivo;
- no force push;
- no sobrescribir trabajo ajeno;
- no incluir archivos no relacionados.

### Durante el cambio

```bash
git diff --stat
git diff
git diff --check
```

- implementar únicamente alcance solicitado;
- ejecutar pruebas y checks reales;
- revisar secretos, `.env`, tokens, dumps y datos personales;
- actualizar lockfile solo por cambio legítimo de dependencias;
- añadir al staging rutas explícitas.

Ejemplo:

```bash
git add docs/assistant-reference/README.md AGENTS.md
git diff --cached --stat
git diff --cached
git diff --cached --check
git commit -m "docs: update assistant reference"
```

### Antes de publicar

```bash
git fetch origin
git rev-list --left-right --count origin/main...main
```

Si `origin/main` avanzó, rebasar únicamente commits locales no publicados:

```bash
git rebase origin/main
```

Si aparece un conflicto dudoso: detenerse y reportarlo. No resolver por descarte ni usar force push.

Publicar y verificar:

```bash
git push origin main
git status --short --branch
git rev-list --left-right --count origin/main...main
```

Resultado requerido: push exitoso, working tree limpio y divergencia `0 0`.

## Convención de commits

Preferir commits pequeños y coherentes. Conservar la convención visible del historial cuando sea clara. En caso contrario, Conventional Commits:

- `docs: ...`
- `fix: ...`
- `feat: ...`
- `test: ...`
- `refactor: ...`
- `build: ...`
- `ci: ...`

No afirmar hash, push, test o sincronización hasta obtener el resultado real.

## Revisión documental posterior

Actualizar esta referencia cuando cambie:

- stack o versiones soportadas;
- estructura/módulos/entrypoints;
- endpoints, errores o artefactos canónicos;
- variables y secretos requeridos;
- migraciones o invariantes de datos;
- CI, scripts o despliegue;
- ADR, riesgos o runbooks.

Volver al [índice](README.md).
