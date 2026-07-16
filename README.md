# Jere Platform

Plataforma técnica para construir, operar y comercializar productos SaaS verticales sin duplicar autenticación, autorización, tenancy, pagos, caja, documentos, notificaciones, auditoría e infraestructura en cada producto.

> Estado: fundación ejecutable. El repositorio contiene un monolito modular mínimo, un workspace frontend, PostgreSQL/Flyway, pruebas de arquitectura y CI. Todavía no reemplaza a Gestudio ni contiene lógica comercial migrada.

## Propósito

Jere Platform no es un ERP genérico ni un conjunto de microservicios. Es una base modular multi-tenant sobre la cual se desarrollan productos especializados con propuestas comerciales independientes.

Productos iniciales previstos:

- **Gestudio**: academias, institutos, clubes y espacios con alumnos, actividades y cuotas.
- **Comercio**: inventario, presupuestos, ventas, caja y cuentas corrientes.
- **Turnos**: agendas, disponibilidad, reservas y recordatorios para servicios no regulados.
- **Portales**: directorios y publicaciones para turismo, inmuebles y comercios.

Productos de mayor riesgo, como préstamos, salud o tributación, podrán reutilizar componentes y contratos, pero mantendrán despliegue y datos separados.

## Stack fundacional

- Java 21.
- Spring Boot 3.5.x.
- Maven multi-módulo.
- PostgreSQL 17 y Flyway.
- Testcontainers y ArchUnit.
- React 19, TypeScript, Vite 8 y Vitest 4.
- Node.js 24 LTS y npm workspaces.
- GitHub Actions y Dependabot.

## Estructura actual

```text
jere-platform/
├── backend/
│   ├── platform-kernel/       # tenancy y capacidades técnicas transversales
│   ├── commercial-core/       # contratos comerciales compartidos
│   ├── vertical-academy/      # bounded context de academia
│   └── platform-api/          # aplicación Spring Boot ejecutable
├── frontend/
│   ├── apps/portal/           # shell de producto independiente
│   └── packages/ui/           # primitivas visuales compartidas
├── docs/
│   ├── adr/
│   ├── architecture/
│   └── roadmap/
├── scripts/
├── compose.yaml
└── .github/
```

## Requisitos

- JDK 21.
- Maven 3.6.3 o superior.
- Node.js 24 LTS.
- npm.
- Docker con Compose.

## Inicio local

### 1. Base de datos

```bash
cp .env.example .env
docker compose up -d postgres
```

En PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
```

La configuración por defecto es únicamente local. No debe reutilizarse en producción.

### 2. Backend

```bash
mvn -f backend/pom.xml -pl platform-api -am spring-boot:run
```

Verificación de salud:

```text
http://localhost:8080/actuator/health
```

### 3. Frontend

```bash
npm --prefix frontend install
npm --prefix frontend run dev
```

Aplicación local:

```text
http://localhost:5173
```

## Validación completa

Linux/macOS:

```bash
bash scripts/validate.sh
```

PowerShell:

```powershell
./scripts/validate.ps1
```

Los mismos bloques principales se ejecutan en GitHub Actions:

- compilación y pruebas Maven;
- migración Flyway sobre PostgreSQL real mediante Testcontainers;
- reglas de dependencia modular;
- lint, tipos, pruebas y build frontend;
- revisión de dependencias;
- detección de secretos.

## Dependencias permitidas

```text
platform-kernel
      ↑
commercial-core
      ↑
vertical-academy
      ↑
platform-api
```

Las pruebas de arquitectura bloquean ciclos y accesos en dirección inversa. Los módulos deben comunicarse mediante contratos públicos; no mediante entidades de persistencia internas.

## Principios no negociables

1. Un módulo no accede directamente a las tablas internas de otro módulo.
2. Toda entidad perteneciente a un cliente debe quedar aislada por tenant.
3. Permisos, módulos contratados y configuración no se modelan como el mismo concepto.
4. No se extrae un microservicio sin una razón operativa medible.
5. No se generaliza una abstracción por un único caso de uso.
6. No se migra código sin pruebas de caracterización o criterios de aceptación.
7. La plataforma no debe detener la comercialización del producto principal.
8. Una capacidad compartida tiene una única implementación canónica.

## Secuencia de trabajo

1. Consolidar y validar esta fundación.
2. Implementar tenancy, identidad, RBAC, entitlements y auditoría.
3. Inventariar Gestudio con referencias a commit, tablas, APIs, UI y pruebas.
4. Migrar una capacidad por vez, empezando por una vertical completa y verificable.
5. Validar paridad funcional de `academy`.
6. Incorporar `commerce-inventory` como segunda prueba de reutilización.
7. Evaluar servicios independientes sólo después de contar con carga o aislamiento real.

## Repositorios fuente iniciales

- `JerePrograma/Gestudio`
- `JerePrograma/inventarios-muebleria`
- `JerePrograma/GestorTurnosBarberia`
- `JerePrograma/PresupuestadorFlete`
- `JerePrograma/jr-prestamos-platform`

Son fuentes de conocimiento y código candidato. No serán dependencias permanentes ni se copiarán íntegramente.

## Seguridad y visibilidad

Este repositorio sigue siendo público. No deben incluirse secretos, credenciales, datos reales de clientes, dumps productivos ni configuraciones privadas. Antes de migrar lógica comercial propietaria debe resolverse la visibilidad del repositorio y la estrategia de licencia.

## Licencia

Todavía no se definió una licencia de distribución. No asumir que el código puede reutilizarse o redistribuirse libremente.
