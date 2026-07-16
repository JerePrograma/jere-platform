# Inventario de capacidades de Gestudio

- Estado: completado para planificación; no autoriza migración automática.
- Repositorio fuente: `JerePrograma/Gestudio`
- Commit congelado: `3f314ba8cc61a71bfa434a46593cd02336ec16e5`
- Fecha de revisión: 2026-07-16
- Objetivo: decidir qué comportamiento puede alimentar Jere Platform sin copiar el monorepo ni conservar sus acoplamientos.

## Conclusión ejecutiva

Gestudio es la mejor fuente inicial para seguridad, autorización, auditoría, idempotencia, obligaciones, pagos, caja, recibos y la vertical de academias. No es, sin embargo, una plataforma multi-tenant ni un conjunto de módulos ya separados.

La estrategia correcta es:

1. construir tenancy y organizaciones de forma nativa en Jere Platform;
2. migrar contratos y reglas de identidad/RBAC, no entidades persistentes completas;
3. extraer auditoría, idempotencia y outbox como capacidades técnicas;
4. comparar parties, caja y catálogo contra `inventarios-muebleria` antes de declararlos genéricos;
5. migrar pagos mediante un caso de uso completo después de estabilizar tenancy y autorización;
6. mantener alumnos, disciplinas, inscripciones, horarios y asistencia dentro de `verticals/academy`.

Copiar `PagoServicio`, el esquema V1 o el frontend completo reproduciría el problema de duplicación que esta plataforma debe eliminar.

## Evidencia fuente relevante

### Plataforma y migraciones

- `README.md`
- `backend/pom.xml`
- `backend/src/main/resources/db/migration/V1__canonical_schema.sql`
- `backend/src/main/resources/db/migration/V2__security_superadmin_sessions_audit.sql`
- `backend/src/main/resources/db/migration/V5__base_roles_permissions_seed.sql`
- `backend/src/main/resources/db/migration/V6__rbac_permission_catalog_and_base_roles.sql`
- `backend/src/test/java/gestudio/infra/persistencia/PostgreSqlSchemaValidationTest.java`
- `backend/src/test/java/gestudio/infra/persistencia/CanonicalArchitectureContractTest.java`

### Identidad, sesión y autorización

- `backend/src/main/java/gestudio/infra/seguridad/AutenticacionService.java`
- `backend/src/main/java/gestudio/infra/seguridad/RefreshSessionService.java`
- `backend/src/main/java/gestudio/infra/seguridad/SecurityConfigurations.java`
- `backend/src/main/java/gestudio/infra/seguridad/RbacService.java`
- `backend/src/main/java/gestudio/infra/seguridad/PermissionCodes.java`
- `backend/src/main/java/gestudio/infra/seguridad/SuperadminBootstrapService.java`
- `backend/src/test/java/gestudio/infra/seguridad/SecurityHttpIntegrationTest.java`
- `backend/src/test/java/gestudio/infra/seguridad/AutenticacionServiceTest.java`
- `backend/src/test/java/gestudio/infra/seguridad/SuperadminBootstrapPostgreSqlTest.java`
- `frontend/src/config/permissions.ts`

### Auditoría e idempotencia

- `backend/src/main/java/gestudio/auditoria/application/AuditService.java`
- `backend/src/main/java/gestudio/auditoria/application/AuditFailureService.java`
- `backend/src/main/java/gestudio/infra/idempotencia/IdempotencyLockService.java`
- `backend/src/main/java/gestudio/infra/idempotencia/RequestHash.java`
- `backend/src/test/java/gestudio/auditoria/AuditServicePostgreSqlTest.java`
- `backend/src/test/java/gestudio/infra/persistencia/IdempotenciaCanonicaPostgreSqlTest.java`
- `backend/src/test/java/gestudio/infra/persistencia/DataAuditSqlPostgreSqlTest.java`

### Obligaciones, pagos, caja y documentos

- `backend/src/main/java/gestudio/servicios/mensualidad/MensualidadServicio.java`
- `backend/src/main/java/gestudio/servicios/cargo/CargoServicio.java`
- `backend/src/main/java/gestudio/cuotas/application/CargoEventoServicio.java`
- `backend/src/main/java/gestudio/servicios/pago/PagoServicio.java`
- `backend/src/test/java/gestudio/servicios/cargo/CargoSaldoPostgreSqlTest.java`
- `backend/src/test/java/gestudio/servicios/pago/PagoCanonicoPostgreSqlTest.java`
- `backend/src/test/java/gestudio/infra/persistencia/ReciboOutboxPostgreSqlTest.java`

Las tablas y columnas exactas deben confirmarse contra `V1__canonical_schema.sql` al preparar cada migración. Este inventario no convierte nombres observados en un contrato nuevo.

## Matriz de clasificación

| Capacidad | Clasificación objetivo | Evidencia de reutilización | Riesgo | Decisión |
|---|---|---|---|---|
| Tenancy y organizaciones | `kernel/tenancy` | Justificación de plataforma | Crítico | Construir nativamente; Gestudio no es fuente canónica suficiente. |
| Identidad y sesiones | `kernel/identity` | Todos los productos requieren acceso seguro | Alto | Migrar reglas de sesión, rotación e invalidación; rediseñar persistencia para memberships multi-tenant. |
| RBAC | `kernel/authorization` | Academias, comercio, turnos y administración | Alto | Reutilizar semántica 401/403 y permisos efectivos; agregar alcance por tenant y sucursal. |
| Entitlements | `kernel/entitlements` | Necesario para planes y módulos SaaS | Alto | Construir separado de RBAC; no existe como capacidad completa en Gestudio. |
| Auditoría | `kernel/audit` | Seguridad, finanzas y soporte | Alto | Migrar contrato con actor, tenant, correlación, resultado y datos sanitizados. |
| Idempotencia | `kernel/idempotency` | Pagos, imports, webhooks y jobs | Alto | Extraer lock + request hash detrás de una interfaz; cubrir concurrencia. |
| Outbox y jobs | `kernel/integration-outbox` | Recibos, email e integraciones | Alto | Generalizar el patrón `ReciboPendiente`, no su entidad específica. |
| Personas y contactos | Sin clasificar aún | Gestudio + mueblería potencial | Medio | `Alumno` sigue siendo academy hasta comparar con terceros/clientes de comercio. |
| Obligaciones/cuentas por cobrar | `commercial-core/receivables` candidato | Cuotas, ventas a cuenta y servicios | Alto | Separar cargo genérico de la regla academy que lo genera. |
| Pagos y aplicaciones | `commercial-core/payments` candidato | Academias y comercio | Crítico | Migrar como flujo completo; no copiar `PagoServicio`. |
| Caja | `commercial-core/cash` candidato | Academias y comercio | Alto | Comparar invariantes con `inventarios-muebleria` antes de fijar contrato. |
| Crédito a favor | `commercial-core/receivables` candidato | Pagos y cuentas corrientes | Alto | Conservar ledger compensatorio; validar segundo uso real. |
| Recibos/documentos | `commercial-core/documents` candidato | Pagos, ventas y presupuestos | Medio | Separar documento, almacenamiento, generación y entrega. |
| Notificaciones | `kernel/notifications` | Todos los verticales | Medio | Consumir outbox; evitar enviar dentro de transacciones financieras. |
| Alumnos, disciplinas e inscripciones | `verticals/academy` | Sólo vertical academy | Medio | Mantener lenguaje y reglas específicos. |
| Horarios y asistencia | `verticals/academy` | Sólo vertical academy por ahora | Medio | No convertir prematuramente en agenda genérica. |
| Inventario | Pendiente de segunda fuente | Gestudio y mueblería | Alto | La fuente canónica se decide comparando ambos repositorios. |
| Tooling y seeds | `infra` y fixtures | Reutilización operativa | Bajo | Adoptar patrones, no copiar datos demo ni historiales Flyway. |

## Hallazgos críticos

### Gestudio no aporta tenancy suficiente

Antes de portar autenticación o RBAC, Jere Platform necesita definir:

- tenant u organización;
- membresía usuario-organización;
- selección segura del tenant activo;
- alcance por sucursal;
- restricciones únicas tenant-aware;
- auditoría con tenant obligatorio;
- aislamiento probado con al menos dos tenants.

Agregar `tenant_id` después de migrar todo sería una reconstrucción, no una evolución.

### `PagoServicio` es una fuente de reglas, no un módulo reutilizable

El servicio actual orquesta, en una misma transacción:

- autorización;
- lock de idempotencia y hash del request;
- bloqueo determinista de alumno y cargos;
- aplicaciones de pago;
- saldo y estado de cargos;
- caja;
- crédito a favor;
- recibo;
- creación de trabajo pendiente para generar y enviar el recibo.

Su anulación crea registros compensatorios de caja y crédito y revierte aplicaciones. Estas reglas son valiosas. La dependencia directa de numerosas entidades y repositorios impide copiarlo como núcleo compartido.

La extracción debe introducir contratos explícitos entre:

- `receivables`;
- `payments`;
- `cash`;
- `documents`;
- `idempotency`;
- `authorization`;
- `audit/outbox`.

### Seguridad tiene contratos que deben conservarse

El hardening de Gestudio establece:

- anónimo: 401;
- autenticado sin permiso: 403;
- conflicto de negocio: 409;
- permisos efectivos calculados en backend;
- catálogo y matrices determinísticas;
- migraciones de autorización forward-only;
- SUPERADMIN utilizable sin depender del seed demo;
- refresh token HttpOnly;
- frontend alineado con el catálogo backend.

La plataforma debe conservar esas semánticas, pero no asumir roles globales ni un único establecimiento.

### Los datos demo no son migraciones productivas

Los seeds de demostración sirven como fixtures y ambientes de venta. No pueden ser:

- catálogo de permisos productivo;
- bootstrap obligatorio;
- fuente de IDs estables;
- reemplazo de migraciones Flyway;
- datos usados para validar aislamiento multi-tenant.

## Mapa de dependencias objetivo

```text
tenancy
  └── identity/memberships
        ├── authorization
        ├── entitlements
        └── audit

idempotency ─┐
authorization├── payments ─── receivables
audit        │       ├── cash
outbox       ┘       ├── credit
                     └── documents/notifications

academy ─── receivables + payments + documents
```

Las flechas representan consumo de contratos públicos, no acceso directo a entidades o tablas.

## Primer corte migrable recomendado

### Nombre

`Tenant-aware authenticated authorization`

### Flujo

1. crear dos organizaciones de prueba;
2. crear una identidad y memberships explícitas;
3. iniciar sesión y emitir una sesión rotatable;
4. seleccionar un tenant permitido;
5. calcular roles y permisos efectivos para esa membership;
6. autorizar un endpoint `/api/me` y uno protegido de demostración;
7. registrar auditoría de éxito y denegación;
8. demostrar que cambiar un identificador, header o URL no permite cruzar tenants.

### No incluye

- alumnos;
- cuotas;
- pagos;
- caja;
- módulos comerciales;
- migración de usuarios reales.

### Aceptación

- 401, 403 y conflicto de negocio permanecen diferenciados.
- Una identidad puede tener memberships independientes.
- Los permisos se calculan dentro del tenant activo.
- Un tenant no puede consultar ni inferir datos de otro.
- La sesión puede invalidarse mediante versión o rotación.
- La auditoría contiene tenant, actor, acción, resultado y correlación sin secretos.
- Existen pruebas PostgreSQL/Testcontainers y HTTP.
- La implementación no depende de tablas de Gestudio.

## Orden de migración

1. Resolver visibilidad privada antes de introducir lógica comercial sensible.
2. Implementar tenancy, organizaciones, sucursales y memberships.
3. Migrar identidad y ciclo de sesión.
4. Migrar RBAC con alcance tenant-aware.
5. Implementar entitlements por separado.
6. Migrar auditoría, idempotencia y outbox.
7. Comparar parties, catálogo y caja con `inventarios-muebleria`.
8. Diseñar contratos de receivables y payments.
9. Migrar un flujo financiero vertical completo con reconciliación.
10. Migrar `academy` y ejecutar paridad funcional contra Gestudio.

## Gaps de pruebas antes de portar producción

- aislamiento real entre tenants;
- membership revocada durante una sesión activa;
- rotación y replay de refresh tokens;
- roles personalizados conservados durante upgrades;
- idempotencia concurrente entre instancias;
- retries, backoff y dead-letter de outbox;
- conciliación de cargos, pagos, caja, crédito y recibos;
- importación restartable con reporte de rechazados;
- autorización por sucursal;
- observabilidad y recuperación ante fallos parciales.

## Decisiones de no migración

- No importar la historia Flyway V1–V6 dentro de Jere Platform.
- No copiar entidades JPA como librería compartida.
- No conservar `Alumno` como entidad central genérica.
- No convertir horarios de academias en el motor universal de turnos.
- No mover pagos antes de tenancy, autorización, auditoría e idempotencia.
- No mantener dos implementaciones compartidas activas indefinidamente.
- No publicar lógica comercial propietaria mientras el repositorio siga público.

## Próximos issues

1. Implementar tenancy, organizaciones, sucursales y memberships.
2. Implementar identidad y sesiones tenant-aware.
3. Implementar autorización y entitlements.
4. Implementar auditoría, idempotencia y outbox.
5. Comparar núcleo comercial con `inventarios-muebleria`.
6. Ejecutar el primer corte migrable y medir paridad.
