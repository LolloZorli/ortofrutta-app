import { useEffect, useState, type FormEvent } from 'react'
import { api, companySession, errorMessage } from '../api'
import { useAuth } from '../auth'
import type { CompanyShop } from '../types'

type Mode = 'login' | 'customer' | 'employee' | 'company' | 'forgot' | 'reset'

const MODES: { id: Mode; icon: string; label: string }[] = [
  { id: 'login', icon: '↪', label: 'Accedi' },
  { id: 'customer', icon: '🧺', label: 'Cliente' },
  { id: 'employee', icon: '🧾', label: 'Commesso' },
  { id: 'company', icon: '🏪', label: 'Azienda' },
]

export default function LoginPage() {
  const { login } = useAuth()
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [companySlug, setCompanySlug] = useState(companySession.slug())
  const [shops, setShops] = useState<CompanyShop[]>([])
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [newPassword, setNewPassword] = useState('')

  useEffect(() => {
    api.get<CompanyShop[]>('/companies').then(setShops).catch(() => setShops([]))
  }, [])

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get('resetToken')
    if (token) {
      setMode('reset')
      window.history.replaceState({}, '', window.location.pathname)
      sessionStorage.setItem('ortofrutta.reset-token', token)
    }
  }, [])

  function selectMode(next: Mode) {
    setMode(next)
    setError(null)
    setNotice(null)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      if (mode !== 'company') companySession.select(companySlug)
      else companySession.select(companySlug)

      if (mode === 'forgot') {
        await api.post('/auth/forgot-password', { email: email.trim() })
        setNotice('Se l’email è registrata, riceverai un link per reimpostare la password.')
        setMode('login')
      } else if (mode === 'reset') {
        const token = sessionStorage.getItem('ortofrutta.reset-token')
        if (!token) throw new Error('Link di reset non disponibile')
        await api.post('/auth/reset-password', { token, newPassword })
        sessionStorage.removeItem('ortofrutta.reset-token')
        setNewPassword('')
        setPassword('')
        setMode('login')
        setNotice('Password aggiornata. Ora puoi accedere.')
      } else if (mode === 'company') {
        await api.post('/companies/register', {
          companyName: companyName.trim(), slug: companySlug.trim(), ownerName: name.trim(),
          email: email.trim(), password, phone: phone.trim() || null,
        })
        await login(email.trim(), password)
      } else if (mode === 'customer') {
        await api.post('/auth/register', { name: name.trim(), email: email.trim(), phone: phone.trim() || null, password })
        await login(email.trim(), password)
      } else if (mode === 'employee') {
        const response = await api.post<{ message: string }>('/auth/register-employee', {
          name: name.trim(), email: email.trim(), phone: phone.trim() || null, password,
        })
        setUsername(email.trim())
        setPassword('')
        setMode('login')
        setNotice(response.message || 'Richiesta inviata. Potrai accedere dopo l’approvazione dell’azienda.')
      } else {
        await login(username.trim(), password)
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const registering = mode !== 'login' && mode !== 'forgot' && mode !== 'reset'
  const companyRegistration = mode === 'company'
  const employeeRegistration = mode === 'employee'

  return (
    <main className="login">
      <form className="card login-card" onSubmit={submit}>
        <img className="login-logo" src="/icon.svg" alt="" />
        <h1 className="center">Ortofrutta</h1>
        <p className="muted center">Un portale per clienti e negozi di ortofrutta</p>

        {(mode !== 'forgot' && mode !== 'reset') && (
          <div className="auth-modes" role="tablist" aria-label="Tipo di accesso">
            {MODES.map((item) => <button key={item.id} type="button" role="tab" aria-selected={mode === item.id}
              className={`auth-mode${mode === item.id ? ' active' : ''}`} onClick={() => selectMode(item.id)}>
              <span>{item.icon}</span>{item.label}
            </button>)}
          </div>
        )}

        {mode !== 'company' && mode !== 'forgot' && mode !== 'reset' && <label className="field"><span>Negozio</span>
          <select value={companySlug} onChange={(e) => setCompanySlug(e.target.value)} required>
            {shops.map((shop) => <option key={shop.id} value={shop.slug}>{shop.name}</option>)}
            {!shops.some((shop) => shop.slug === companySlug) && <option value={companySlug}>{companySlug}</option>}
          </select>
          {shops.length === 0 && <small className="muted">Se non trovi il negozio, aggiorna la pagina o chiedi il suo indirizzo.</small>}
        </label>}

        {companyRegistration && <>
          <label className="field"><span>Nome dell’azienda</span><input value={companyName} onChange={(e) => setCompanyName(e.target.value)} autoComplete="organization" required /></label>
          <label className="field"><span>Indirizzo del negozio</span><input value={companySlug} onChange={(e) => setCompanySlug(e.target.value.toLowerCase())} pattern="[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*" minLength={3} maxLength={80} placeholder="es. frutta-rossi" required /></label>
        </>}

        {registering && <label className="field"><span>{companyRegistration ? 'Nome titolare' : 'Nome e cognome'}</span>
          <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={120} required />
        </label>}

        {mode === 'forgot' ? (
          <label className="field"><span>Email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              autoComplete="email" maxLength={254} required />
          </label>
        ) : mode === 'reset' ? (
          <label className="field"><span>Nuova password (almeno 8 caratteri)</span>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password" minLength={8} maxLength={72} required />
          </label>
        ) : (
          <label className="field"><span>{registering ? 'Email' : 'Email o nome utente'}</span>
            <input type={registering ? 'email' : 'text'} value={registering ? email : username}
              onChange={(e) => registering ? setEmail(e.target.value) : setUsername(e.target.value)}
              autoComplete="username" autoCapitalize="none" required />
          </label>
        )}

        {(mode === 'customer' || companyRegistration) && <label className="field"><span>Telefono (facoltativo)</span>
          <input value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" maxLength={30} />
        </label>}

        {(mode !== 'forgot' && mode !== 'reset') && (
          <label className="field"><span>Password{registering ? ' (almeno 8 caratteri)' : ''}</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 8 : undefined} required />
          </label>
        )}

        {employeeRegistration && <p className="notice">L’azienda approverà la richiesta prima di attivare il tuo accesso.</p>}
        {notice && <p className="notice success" role="status">{notice}</p>}
        {error && <p className="tone-bad" role="alert">{error}</p>}

        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Attendi…'
            : mode === 'login' ? 'Accedi'
            : mode === 'forgot' ? 'Invia link di reset'
            : mode === 'reset' ? 'Aggiorna password'
            : mode === 'customer' ? 'Crea account cliente'
            : mode === 'employee' ? 'Invia richiesta commesso'
            : 'Crea spazio aziendale'}
        </button>
        {mode === 'login' && (
          <button type="button" className="btn btn-block" onClick={() => selectMode('forgot')}>
            Password dimenticata
          </button>
        )}
        {(mode === 'forgot' || mode === 'reset') && (
          <button type="button" className="btn btn-block" onClick={() => selectMode('login')}>
            Torna all'accesso
          </button>
        )}
      </form>
    </main>
  )
}
