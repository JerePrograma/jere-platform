# Jere Platform

Plataforma técnica para construir, operar y comercializar productos SaaS verticales sin duplicar autenticación, autorización, tenancy, pagos, caja, documentos, notificaciones, auditoría e infraestructura en cada producto.

> Estado: fundación arquitectónica. Este repositorio todavía no contiene una aplicación productiva ni reemplaza a Gestudio u otros productos existentes.

## Propósito

Jere Platform no es un ERP genérico ni un conjunto de microservicios. Es una base modular multi-tenant sobre la cual se desarrollan productos especializados con propuestas comerciales independientes.

Productos iniciales previstos:

- **Gestudio**: academias, institutos, clubes y espacios con alumnos, actividades y cuotas.
- **Comercio**: inventario, presupuestos, ventas, caja y cuentas corrientes.
- **Turnos**: agendas, disponibilidad, reservas y recordatorios para servicios no regulados.
- **Portales**: directorios y publicaciones para turismo, inmuebles y comercios.

Productos de mayor riesgo, como préstamos, salud o tributación, podrán reutilizar componentes y contratos, pero mantendrán despliegue y datos separados.

## Decisiones iniciales

- Monorepo.
- Monolito modular antes que microservicios.
- Java 21, Spring Boot, PostgreSQL y Flyway para backend.
- React, TypeScript y Vite para frontend.
- Arquitectura multi-tenant desde el modelo de datos.
- RBAC, entitlements y alcance por organización/sucursal como conceptos diferentes.
- Integraciones externas mediante outbox y workers cuando corresponda.
- Migración selectiva desde repositorios existentes; no se fusionarán historiales completos.
- Una única implementación canónica por capacidad compartida.

## Límites de alto nivel

```text
jere-platform/
├── apps/                 # aplicaciones ejecutables
├── modules/
│   ├── kernel/           # tenancy, identidad, permisos, auditoría
│   ├── commercial-core/  # clientes, catálogo, cobros, caja, documentos
│   └── verticals/        # academia, inventario, turnos, etc.
├── packages/             # UI kit, contratos y clientes compartidos
├── infra/                # despliegue, backups, CI y observabilidad
└── docs/                 # ADR, arquitectura, migración y roadmap
```

## Principios no negociables

1. Un módulo no accede directamente a las tablas internas de otro módulo.
2. Toda entidad perteneciente a un cliente debe quedar aislada por tenant.
3. Permisos, módulos contratados y configuración no se modelan como el mismo concepto.
4. No se extrae un microservicio sin una razón operativa medible.
5. No se generaliza una abstracción por un único caso de uso.
6. No se migra código sin pruebas de caracterización o criterios de aceptación.
7. La plataforma no debe detener la comercialización del producto principal.

## Primera secuencia de trabajo

1. Definir arquitectura, convenciones y criterios de migración.
2. Crear el esqueleto ejecutable y la validación CI.
3. Implementar tenancy, identidad, RBAC, entitlements y auditoría.
4. Extraer desde Gestudio las capacidades compartidas ya probadas.
5. Implementar la vertical `academy` y validar paridad funcional.
6. Incorporar `commerce-inventory` como segunda prueba de reutilización.
7. Evaluar extracción de servicios sólo después de contar con carga y necesidades reales.

## Repositorios fuente iniciales

- `JerePrograma/Gestudio`
- `JerePrograma/inventarios-muebleria`
- `JerePrograma/GestorTurnosBarberia`
- `JerePrograma/PresupuestadorFlete`
- `JerePrograma/jr-prestamos-platform`

Estos repositorios son fuentes de conocimiento y código candidato. No serán dependencias permanentes ni se copiarán íntegramente.

## Seguridad

Este repositorio es público. No deben incluirse secretos, credenciales, datos reales de clientes, dumps productivos ni configuraciones privadas. El núcleo comercial propietario deberá revisarse antes de cada publicación.

## Licencia

Todavía no se definió una licencia de distribución. Hasta tomar esa decisión, no asumir que el código puede reutilizarse o redistribuirse libremente.
