import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'

export type ButtonProps = PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement>>

export function Button({ children, className = '', type = 'button', ...props }: ButtonProps) {
  const classes = ['jp-button', className].filter(Boolean).join(' ')

  return (
    <button className={classes} type={type} {...props}>
      {children}
    </button>
  )
}
