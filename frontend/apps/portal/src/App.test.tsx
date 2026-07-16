import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('reveals the platform layers from an accessible control', () => {
    render(<App />)

    const toggle = screen.getByRole('button', { name: 'Ver arquitectura' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(toggle)

    expect(screen.getByRole('region', { name: 'Capas de la plataforma' })).toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('heading', { name: 'Commercial Core' })).toBeInTheDocument()
  })
})
