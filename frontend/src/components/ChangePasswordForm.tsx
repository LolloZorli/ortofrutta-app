import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import { useToast } from '../toast'
import { Field } from './ui'

export default function ChangePasswordForm({ onDone }: { onDone: () => void }) {
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next.length < 8) return toast.error('La nuova password deve avere almeno 8 caratteri')
    if (next !== confirm) return toast.error('Le due password non coincidono')
    setBusy(true)
    try {
      await api.post('/auth/change-password', { currentPassword: current, newPassword: next })
      toast.ok('Password cambiata')
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Password attuale">
        <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
      </Field>
      <Field label="Nuova password" hint="Almeno 8 caratteri">
        <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" required />
      </Field>
      <Field label="Ripeti la nuova password">
        <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required />
      </Field>
      <button className="btn btn-primary btn-block" disabled={busy}>
        Cambia password
      </button>
    </form>
  )
}
