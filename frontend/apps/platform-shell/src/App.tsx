import {
  formatProductLabel,
  PlatformCard
} from "@jere-platform/ui";

export function App() {
  return (
    <main className="shell">
      <header>
        <p className="eyebrow">Foundation runtime</p>
        <h1>{formatProductLabel("Platform Shell")}</h1>
        <p>
          Base compartida para productos SaaS verticales, sin convertirlos en
          un ERP genérico.
        </p>
      </header>

      <PlatformCard title="Estado">
        <ul>
          <li>Monolito modular Java 21 y Spring Boot.</li>
          <li>PostgreSQL y migraciones Flyway.</li>
          <li>Workspace React, TypeScript y Vite.</li>
          <li>Límites de módulos verificados por tests.</li>
        </ul>
      </PlatformCard>
    </main>
  );
}
