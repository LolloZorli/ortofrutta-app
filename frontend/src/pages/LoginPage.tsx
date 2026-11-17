import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [registering, setRegistering] = useState(false)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (registering) {
        await api.post('/auth/register', { name: name.trim(), email: email.trim(), phone: phone.trim() || null, password })
        await login(email.trim(), password)
      } else {
        await login(username.trim(), password)
      }
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="card login-card" onSubmit={submit}>
        <img className="login-logo" src="/icon.svg" alt="" />
        <h1 className="center">Ortofrutta</h1>
        <p className="muted center">{registering ? 'Crea un account cliente per inviare ordini al negozio' : 'Accedi al gestionale o al portale ordini'}</p>
        {registering && <label className="field"><span>Nome e cognome</span><input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required /></label>}
        <label className="field">
          <span>{registering ? 'Email' : 'Email o nome utente'}</span>
          <input
            type={registering ? 'email' : 'text'}
            value={registering ? email : username}
            onChange={(e) => registering ? setEmail(e.target.value) : setUsername(e.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            required
          />
        </label>
        {registering && <label className="field"><span>Telefono (facoltativo)</span><input value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" /></label>}
        <label className="field">
          <span>Password{registering ? ' (almeno 8 caratteri)' : ''}</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="tone-bad">{error}</p>}
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? (registering ? 'Creazione account…' : 'Accesso…') : registering ? 'Crea account cliente' : 'Accedi'}
        </button>
        <button type="button" className="btn btn-sm" disabled={busy} onClick={() => { setRegistering((value) => !value); setError(null) }}>
          {registering ? 'Hai già un account? Accedi' : 'Sei un cliente? Crea un account per ordinare'}
        </button>
      </form>
    </div>
  )
}
