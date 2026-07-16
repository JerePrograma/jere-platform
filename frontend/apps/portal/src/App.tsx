import { useState } from 'react'
import { Button } from '@jere-platform/ui'

const modules = [
  ['Kernel', 'Tenancy, identidad, permisos, auditoría y capacidades contratadas.'],
  ['Commercial Core', 'Clientes, catálogo, cobros, caja, documentos y reportes.'],
  ['Verticales', 'Productos especializados sin mezclar reglas de negocio.'],
] as const

export function App() {
  const [detailsVisible, setDetailsVisible] = useState(false)

  return (
    <main className="shell">
      <section className="hero" aria-labelledby="platform-title">
        <p className="eyebrow">Fundación técnica</p>
        <h1 id="platform-title">Jere Platform</h1>
        <p className="summary">
          Una plataforma modular para lanzar productos SaaS especializados sin duplicar la base técnica.
        </p>
        <Button aria-expanded={detailsVisible} onClick={() => setDetailsVisible((visible) => !visible)}>
          {detailsVisible ? 'Ocultar arquitectura' : 'Ver arquitectura'}
        </Button>
      </section>

      {detailsVisible && (
        <section aria-label="Capas de la plataforma" className="module-grid">
          {modules.map(([title, description]) => (
            <article className="module-card" key={title}>
              <h2>{title}</h2>
              <p>{description}</p>
            </article>
          ))}
        </section>
      )}
    </main>
  )
}
