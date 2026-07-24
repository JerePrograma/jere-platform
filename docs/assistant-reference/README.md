# Autorreferencia técnica para agentes

| Campo | Valor verificado |
|---|---|
| Repositorio | `JerePrograma/jere-platform` |
| Rama funcional inspeccionada | `main` |
| Baseline funcional | `85b1caf48c18633dd77459505e52410fdc9072d8` |
| Fecha de verificación | 2026-07-24 |
| Estado | Foundation runtime ejecutable; no producción |

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Propósito

Este directorio concentra contexto técnico verificable para iniciar nuevas sesiones de ChatGPT, Codex u otros agentes sin repetir toda la investigación estable del repositorio. No sustituye la inspección del código actual ni los ADR.

## Resumen ejecutivo

Jere Platform es un monorepo para construir productos SaaS verticales sobre una base técnica compartida. El backend es un monolito modular Java 21/Spring Boot con PostgreSQL y Flyway. El frontend es un workspace npm con React, TypeScript y Vite. El runtime implementado cubre tenancy, identidad, sesiones, autorización, auditoría, idempotencia, outbox transaccional y un directorio tenant-scoped de referencias comerciales. Las verticales y el frontend funcional siguen incompletos.

No existen en el repositorio despliegue productivo, backups, recuperación, broker, Kubernetes, OpenAPI, UI de autenticación ni lógica vertical propietaria.

## Mapa documental

1. [Sistema, arquitectura y mapa del repositorio](01-sistema-y-repositorio.md)
2. [Dominio, datos, APIs, contratos e integraciones](02-dominio-datos-y-contratos.md)
3. [Desarrollo local, pruebas, CI y operación](03-desarrollo-pruebas-y-operacion.md)
4. [Seguridad, invariantes y convenciones](04-seguridad-invariantes-y-convenciones.md)
5. [Guía de cambios y flujo Git obligatorio](05-guia-de-cambios-y-git.md)
6. [Decisiones, deuda, riesgos y runbook](06-decisiones-riesgos-y-runbook.md)
7. [Glosario, símbolos y trazabilidad](07-glosario-simbolos-y-trazabilidad.md)

Documentos canónicos existentes que deben conservar precedencia:

- [README raíz](../../README.md)
- [Contexto rápido para agentes](../../AGENTS.md)
- [Estado actual](../current-state.md)
- [Mapa de dominios](../domain-map.md)
- [Arquitectura objetivo](../architecture/target-architecture.md)
- [Estrategia de migración](../architecture/migration-strategy.md)
- [Roadmap de foundation](../roadmap/00-foundation.md)
- [Handoff y ledger](../project-status-and-handoff.md)
- [Contrato party-source export v1](../integration/party-source-export-v1.md)
- [ADR](../adr/0001-modular-monolith.md)

## Orden recomendado de lectura

- Cambio pequeño y localizado: `AGENTS.md` → este índice → documento temático → código y pruebas afectadas.
- Cambio de contrato o persistencia: agregar `02`, `04` y ADR relacionados.
- Integración o incidente: agregar `03` y `06`.
- Cambio arquitectónico: leer todos los ADR y registrar una decisión nueva o una sustitución explícita.

## Comandos rápidos

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
mvn -B -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build
```

Validación integral soportada:

```bash
./scripts/validate.sh
```

```powershell
./scripts/validate.ps1
```

Clasificación: **comandos definidos por el repositorio**. En esta generación documental no pudieron ejecutarse contra un checkout local porque el entorno no pudo resolver `github.com`; existen evidencias históricas de ejecución exitosa en `docs/current-state.md`, pero no deben confundirse con una validación actual.

## Protocolo mínimo antes de cualquier cambio

1. Ejecutar `git status`, comprobar `main`, `origin` y upstream.
2. Ejecutar `git fetch origin` y `git pull --ff-only origin main`.
3. Detenerse si existen cambios locales previos ajenos a la tarea.
4. Localizar símbolos, interfaces, consumidores, persistencia, configuración y pruebas.
5. Realizar el cambio mínimo; no ampliar alcance ni cambiar contratos accidentalmente.
6. Ejecutar pruebas focalizadas y luego las validaciones generales viables.
7. Ejecutar `git diff --check`, revisar el diff y buscar secretos.
8. Confirmar únicamente archivos relacionados, volver a hacer `git fetch origin` y publicar con `git push origin main` sin force push.

El procedimiento completo está en [Guía de cambios y Git](05-guia-de-cambios-y-git.md).

## Áreas no verificadas o ausentes

- **No verificado:** configuración CORS efectiva fuera de `ApiSecurityConfiguration`.
- **No verificado:** comportamiento en un entorno productivo; no existe despliegue configurado.
- **No verificado:** backups, restauración, alertas, dashboards y tracing; no existen implementaciones.
- **Pendiente:** operación productiva del emisor Gestudio y definición independiente de Scalaris.
- **Pendiente:** política de retención de snapshots party-source.
- **No existe evidencia suficiente:** duración estable de los comandos en equipos distintos.
- **No existe evidencia suficiente:** cobertura porcentual exigida; JaCoCo genera reporte, pero no hay umbral configurado.

## Regla de mantenimiento

Actualizar esta autorreferencia cuando cambien contratos públicos, módulos, migraciones, variables, entrypoints, CI, invariantes o procedimientos operativos. Para cambios puramente internos, actualizar únicamente el documento cuya afirmación haya quedado obsoleta.
