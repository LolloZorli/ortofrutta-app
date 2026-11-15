import type { ReactNode } from 'react'

export function Loading() {
  return <p className="muted center pad">Caricamento…</p>
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="card error-box">
      <p>{message}</p>
      {onRetry && (
        <button className="btn" onClick={onRetry}>
          Riprova
        </button>
      )}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="muted center pad">{children}</p>
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small className="muted">{hint}</small>}
    </label>
  )
}

export function Chip({ tone, children }: { tone: 'green' | 'orange' | 'red' | 'gray' | 'blue'; children: ReactNode }) {
  return <span className={`chip chip-${tone}`}>{children}</span>
}

export function Stat({ label, value, tone }: { label: string; value: string; tone?: 'good' | 'bad' }) {
  return (
    <div className="stat">
      <span className="muted">{label}</span>
      <strong className={tone ? `tone-${tone}` : ''}>{value}</strong>
    </div>
  )
}
