# Decisiones, deuda, riesgos y runbook

**Verificado:** 2026-07-24 contra `85b1caf48c18633dd77459505e52410fdc9072d8`.

> La documentación es una guía secundaria. Antes de modificar el proyecto, verificar siempre el estado actual del código, las pruebas, la configuración y las referencias reales.

## Registro de decisiones

| ADR | Estado | Decisión verificable | Consecuencia actual |
|---|---|---|---|
| 0001 | Aceptada | monorepo y monolito modular | un deployable Spring Boot; extracción requiere evidencia operativa |
| 0002 | Aceptada | foundation pública con licencia propietaria | prohibida lógica vertical sensible hasta private/split |
| 0003 | Aceptada | identity global + membership tenant-scoped | tenant desde contexto confiable y constraints compuestas |
| 0004 | Aceptada | sesiones revocables y refresh rotativo | bearer validado contra DB; replay revoca familia |
| 0005 | Aceptada | RBAC, entitlements, flags y config separados | backend decide con estado PostgreSQL actual |
| 0006 | Aceptada | audit + idempotencia + outbox PostgreSQL | atomicidad local y entrega externa at-least-once |
| 0007 | Aceptada | sharing comercial basado en dos usos actuales | finanzas/catalog/inventory siguen diferidos |
| 0008 | Aceptada | Party Reference Directory estrecho | perfiles permanecen en productos fuente |
| 0009 | Aceptada | artefacto v1 HMAC tenant-bound | receptor sin acceso directo a DB fuente |
| 0010 | Aceptada | progreso multipágina persistido | completitud correcta y compatibilidad v1 legacy |

### Decisiones inferidas, no oficiales

- **Inferencia:** JDBC en `commercial-core` favorece stores SQL explícitos para el slice parties, pero no obliga a todos los módulos futuros a usar JDBC.
- **Inferencia:** la ausencia de OpenAPI es deliberada hasta un contrato externo que lo justifique, según estado actual; no es una prohibición permanente.
- **Inferencia:** los métricos existentes preparan operación, pero no constituyen observabilidad productiva.

### Restricciones técnicas

- Java 21 exacto por Enforcer.
- PostgreSQL 16 en Compose/Testcontainers.
- Docker obligatorio para integración local completa.
- Flyway V1-V8 inmutables.
- npm lockfile obligatorio para `npm ci`.
- permisos/source types sincronizados entre varios lenguajes.

## Deuda técnica y cuestiones abiertas

| Severidad | Evidencia | Impacto/probabilidad | Recomendación | Alcance documental |
|---|---|---|---|---|
| Crítico | repo público + futuros dominios propietarios | impacto alto si se migra lógica sensible; probabilidad real | resolver visibilidad antes de verticales | registrar, no corregir aquí |
| Alto | sin deploy/backups/recovery | bloquea producción | diseñar entornos, restore test y rollback | fuera de alcance |
| Alto | source emitter Gestudio no operado en producción | integración local no equivale a operación | cerrar evidencia de despliegue | fuera de alcance |
| Alto | Scalaris sin tenant mapping/emisor | no puede integrarse de forma segura | contrato fuente independiente | fuera de alcance |
| Alto | no MFA/recovery/rate limiting | riesgo al exponer auth | threat model e implementación focalizada | fuera de alcance |
| Medio | frontend estático sin auth/API/routing | producto no usable | implementar vertical slice, no mega-shell | fuera de alcance |
| Medio | outbox sin dispatcher/scheduler productivo | eventos no salen automáticamente | definir owner operativo y dispatcher idempotente | fuera de alcance |
| Medio | snapshots sin retención | crecimiento indefinido | diseñar cleanup con protección de activos | fuera de alcance |
| Medio | CORS no explícito | comportamiento depende de defaults/config externa | política por entorno y tests | fuera de alcance |
| Medio | sin SAST/SBOM/license gate | menor visibilidad de supply chain | añadir gates proporcionados al riesgo | fuera de alcance |
| Medio | no OpenAPI | contratos HTTP manuales | introducir cuando un consumidor externo lo necesite | fuera de alcance |
| Bajo | UI package tiene un solo componente | no es sistema de diseño | crecer por usos reales | fuera de alcance |
| Informativo | `verticals` placeholder | puede inducir documentación falsa | describirlo siempre como no implementado | cubierto |

No se encontraron TODO/FIXME concretos en los archivos críticos revisados mediante el conector. **No verificado:** búsqueda exhaustiva textual del árbol, porque el índice de búsqueda de código del conector no estaba disponible.

## Runbook de solución de problemas

### Maven rechaza Java

**Síntoma:** Enforcer informa versión fuera de rango.

**Diagnóstico:**

```bash
java -version
mvn -version
```

**Causa probable:** `java` y Maven usan JDK distintos.

**Solución:** configurar `JAVA_HOME`/PATH para que Maven use JDK 21. No desactivar Enforcer.

**Verificación:** `mvn -B -f backend/pom.xml verify`.

### Integración falla antes de tests

**Síntoma:** Testcontainers no inicia PostgreSQL.

**Diagnóstico:**

```bash
docker version
docker info
docker ps
```

**Causa probable:** Docker detenido, inaccesible o sin recursos.

**Solución:** restaurar Docker; no convertir el test a mock para ocultar el problema.

### `npm ci` rechaza lockfile

**Síntoma:** manifiestos y lockfile no coinciden.

**Causa:** cambio de `package.json` sin regenerar lockfile o versión de npm incompatible.

**Solución:** usar Node 22/npm 10, ejecutar instalación legítima y revisar diff de `package-lock.json`. No editar integrities manualmente.

### API no inicia por JWT secret

**Síntoma:** startup falla con secreto menor a 32 bytes.

**Solución:** suministrar secreto UTF-8 de al menos 32 bytes desde entorno seguro.

Si falta por completo, desarrollo inicia con clave efímera y warning; todos los access tokens se invalidan al reiniciar. Esto no es aceptable para producción.

### Bootstrap responde `404 not_found`

**Causa probable:** `PLATFORM_BOOTSTRAP_TOKEN` está vacío.

**Acción:** configurar temporalmente el token solo para inicialización. Tras bootstrap exitoso, mantenerlo deshabilitado. No crear usuarios default ni seed con contraseña.

### Bearer recibe 403 al cambiar `X-Tenant-Code`

**Causa:** comportamiento esperado. El bearer está ligado a un tenant/membership.

**Acción:** autenticar una sesión nueva seleccionando el otro tenant. No relajar el filtro.

### Refresh devuelve 403

**Causa probable:** falta `X-Refresh-Intent: rotate`.

**Acción:** enviar header y cookie. No exponer refresh token en JSON/localStorage.

### Refresh/replay devuelve 401 y sucesor también falla

**Causa:** replay detectado; familia comprometida.

**Acción:** forzar nuevo login y revisar posible robo/concurrencia del token.

### Importación devuelve `409 idempotency_key_conflict`

**Causa:** la misma clave fue usada con contenido canónico distinto.

**Acción:** no reutilizar la clave para otro comando. Investigar al cliente; no borrar el ledger.

### Export fuente devuelve `503 party_source_not_configured`

**Causa:** no existe secreto current/previous válido para esa fuente.

**Acción:** configurar secreto de al menos 32 bytes en el runtime. No colocarlo en Git ni logs.

### Export fuente devuelve 401 de firma

**Diagnóstico:** confirmar que se firmaron exactamente los bytes enviados y que header tiene `sha256=` más 64 hex.

**Acción:** evitar parsear/reserializar entre firma y envío; validar overlap current/previous.

### Página devuelve 409

- `snapshot_not_found`: se intentó página > 1 sin cabecera previa.
- `snapshot_not_complete`: se intentó finalizar sin todas las páginas.
- `page_out_of_order`: ordinal incorrecto.
- `page_conflict`: bytes distintos para página ya aceptada.

**Recuperación:** reenviar la página exacta o continuar con el siguiente ordinal. Para contenido corregido, usar un checkpoint nuevo. No editar tablas V8 manualmente.

### Reconciliación devuelve 422

**Causa:** findings bloqueantes. No hubo escritura.

**Acción:** revisar duplicados, source types, campos y ausencias; producir artefacto corregido con checkpoint apropiado. No convertir ausencias en bajas automáticas.

### Outbox queda en DEAD

**Diagnóstico:** consultar endpoints de reliability con permiso adecuado.

**Acción:** resolver causa externa y requeue tenant-scoped. El dispatcher debe tolerar delivery at-least-once.

### Health UP pero producto no funciona

Actuator prueba arranque y DB, no funcionalidad vertical ni integración externa. Ejecutar la prueba o smoke correspondiente; no usar health como evidencia de negocio.

## Riesgos de procedimientos destructivos

No usar como primera respuesta:

- borrar volumen PostgreSQL;
- eliminar tablas/filas de idempotencia, audit, outbox o snapshots;
- reescribir migraciones;
- reset/clean destructivo en Git;
- desactivar tests, constraints o security filters.

Volver al [índice](README.md).
