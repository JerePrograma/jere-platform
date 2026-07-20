# Jere Platform

Plataforma técnica para construir y operar productos SaaS verticales sin duplicar autenticación, autorización, tenancy, auditoría, infraestructura y componentes de interfaz.

> Estado: foundation runtime. El repositorio contiene una base ejecutable, pero todavía no reemplaza a Gestudio ni incorpora lógica comercial propietaria.

## Modelo

Jere Platform no es un ERP genérico ni un conjunto de microservicios. Es un monorepo con:

- un monolito modular Java 21 + Spring Boot;
- PostgreSQL y Flyway;
- un workspace React + TypeScript + Vite;
- módulos técnicos compartidos;
- aplicaciones verticales comercialmente independientes.

Productos previstos:

- **Gestudio**: academias, institutos, clubes y organizaciones con cuotas.
- **Comercio**: inventario, presupuestos, ventas, caja y cuentas corrientes.
- **Turnos**: agendas y reservas para servicios no regulados.
- **Portales**: directorios, publicaciones y generación de contactos.

Préstamos, salud y tributación deberán conservar despliegues y bases de datos separados.

## Estructura actual

```text
backend/
├── platform-api/
└── modules/
    ├── kernel/
    ├── commercial-core/
    └── verticals/

frontend/
├── apps/platform-shell/
└── packages/ui/

infra/
└── compose.yaml
```

La estructura crecerá por capacidades validadas, no por carpetas especulativas.

## Requisitos

- JDK 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker con Compose

Docker debe estar operativo para ejecutar la prueba de integración PostgreSQL/Testcontainers incluida en `mvn verify`.

## Inicio local

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
mvn -B -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend run build
```

`npm ci` usa el `frontend/package-lock.json` versionado y falla cuando los manifiestos y el lockfile no coinciden. Para actualizar dependencias, modificar los manifiestos y regenerar el lockfile con la versión soportada de Node.js/npm; no editar hashes de integridad manualmente.

Backend:

```bash
mvn -B -f backend/pom.xml -pl platform-api -am package -DskipTests
java -jar backend/platform-api/target/platform-api-0.1.0-SNAPSHOT.jar
```

Frontend:

```bash
npm --prefix frontend run dev
```

Puntos de control:

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Frontend: `http://localhost:5173`
- PostgreSQL: `localhost:5432`

## Validación

Linux/macOS:

```bash
./scripts/validate.sh
```

PowerShell:

```powershell
./scripts/validate.ps1
```

`mvn verify` ejecuta tests unitarios, reglas ArchUnit y el test de integración que levanta PostgreSQL 16 con Testcontainers, aplica Flyway y consulta el endpoint `/actuator/health`.

Estado y continuidad:

- [estado actual](docs/current-state.md);
- [mapa de dominios](docs/domain-map.md);
- [roadmap](docs/roadmap/00-foundation.md);
- [handoff y ledger](docs/project-status-and-handoff.md).
- [contrato de exportación de referencias v1](docs/integration/party-source-export-v1.md).

## Límites

1. El módulo `kernel` no depende del núcleo comercial ni de verticales.
2. El núcleo comercial no depende de verticales.
3. Una vertical puede consumir contratos públicos del kernel y del núcleo comercial.
4. Ningún módulo accede directamente a tablas internas de otro.
5. No se incorpora código completo desde repositorios anteriores.
6. Toda migración se realiza por un caso de uso coherente y comprobable.
7. No se extraen microservicios sin una necesidad operativa demostrada.

Las reglas de dependencia se verifican mediante tests de arquitectura.

## Repositorios fuente

- `JerePrograma/Gestudio`
- `JerePrograma/inventarios-muebleria`
- `JerePrograma/GestorTurnosBarberia`
- `JerePrograma/PresupuestadorFlete`
- `JerePrograma/jr-prestamos-platform`

Son fuentes de evidencia. No son dependencias permanentes ni se fusionarán íntegramente.

## Publicación y propiedad intelectual

El repositorio permanece públicamente visible durante la etapa fundacional, bajo una licencia propietaria de todos los derechos reservados. Esto **no** lo convierte en software open source.

No debe incorporarse lógica comercial sensible, secretos, datos reales ni implementaciones propietarias de clientes mientras el repositorio continúe público. Antes de migrar verticales comerciales debe cambiarse la visibilidad o separarse el código privado.

Véase `docs/adr/0002-source-visibility-and-license.md`.
