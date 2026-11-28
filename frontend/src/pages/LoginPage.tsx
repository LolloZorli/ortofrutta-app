import { useEffect, useState, type FormEvent } from 'react'
import { api, companySession, errorMessage } from '../api'
import { useAuth } from '../auth'
import type { CompanyShop } from '../types'

type AccountType = 'customer' | 'store'
type Mode = 'login' | 'customer-register' | 'employee-request' | 'company-register' | 'forgot' | 'reset'

export default function LoginPage() {
  const { login } = useAuth()
  const [accountType, setAccountType] = useState<AccountType>('customer')
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

  function selectAccount(next: AccountType) {
    setAccountType(next)
    setMode('login')
    setError(null)
    setNotice(null)
  }

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
      if (accountType === 'customer' || mode === 'customer-register') companySession.select('')
      else if (companySlug) companySession.select(companySlug)

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
      } else if (mode === 'company-register') {
        await api.post('/companies/register', {
          companyName: companyName.trim(), slug: companySlug.trim(), ownerName: name.trim(),
          email: email.trim(), password, phone: phone.trim() || null,
        })
        await login(email.trim(), password)
      } else if (mode === 'customer-register') {
        await api.post('/auth/register', { name: name.trim(), email: email.trim(), phone: phone.trim() || null, password })
        await login(email.trim(), password)
      } else if (mode === 'employee-request') {
        const response = await api.post<{ message: string }>('/auth/register-employee', {
          name: name.trim(), email: email.trim(), phone: phone.trim() || null, password,
        })
        setUsername(email.trim())
        setPassword('')
        setMode('login')
        setNotice(response.message || 'Richiesta inviata. L’azienda attiverà il tuo accesso.')
      } else {
        await login(username.trim(), password)
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const specialMode = mode === 'forgot' || mode === 'reset'
  const registering = mode === 'customer-register' || mode === 'employee-request' || mode === 'company-register'

  return (
    <main className="login">
      <form className="card login-card" onSubmit={submit}>
        <div className="login-brand">
          <img className="login-logo" src="/icon.svg" alt="Ortofrutta" />
          <div>
            <h1>Ortofrutta</h1>
            <p>Fresco, semplice, vicino a te.</p>
          </div>
        </div>

        {!specialMode && (
          <div className="account-switch" role="tablist" aria-label="Tipo di account">
            <button type="button" role="tab" aria-selected={accountType === 'customer'}
              className={accountType === 'customer' ? 'active' : ''} onClick={() => selectAccount('customer')}>
              Sono cliente
            </button>
            <button type="button" role="tab" aria-selected={accountType === 'store'}
              className={accountType === 'store' ? 'active' : ''} onClick={() => selectAccount('store')}>
              Sono del negozio
            </button>
          </div>
        )}

        {mode === 'customer-register' && <p className="login-kicker">Crea il tuo account una sola volta. Poi potrai scegliere qualsiasi negozio.</p>}
        {mode === 'employee-request' && <p className="login-kicker">Richiedi l’accesso al negozio. Non servirà creare account in due negozi diversi.</p>}
        {mode === 'company-register' && <p className="login-kicker">Crea lo spazio della tua azienda e accedi come titolare.</p>}

        {mode === 'forgot' ? (
          <label className="field"><span>Email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" maxLength={254} required />
          </label>
        ) : mode === 'reset' ? (
          <label className="field"><span>Nuova password (almeno 8 caratteri)</span>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required />
          </label>
        ) : (
          <>
            {accountType === 'store' && mode === 'login' && (
              <label className="field"><span>Negozio</span>
                <select value={companySlug} onChange={(e) => setCompanySlug(e.target.value)} required>
                  <option value="">Seleziona il tuo negozio</option>
                  {shops.map((shop) => <option key={shop.id} value={shop.slug}>{shop.name}</option>)}
                </select>
              </label>
            )}
            {mode === 'company-register' && <>
              <label className="field"><span>Nome dell’azienda</span>
                <input value={companyName} onChange={(e) => setCompanyName(e.target.value)} autoComplete="organization" required />
              </label>
              <label className="field"><span>Codice del negozio</span>
                <input value={companySlug} onChange={(e) => setCompanySlug(e.target.value.toLowerCase())} pattern="[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*" minLength={3} maxLength={80} placeholder="es. frutta-rossi" required />
              </label>
            </>}
            {registering && (
              <label className="field"><span>{mode === 'company-register' ? 'Nome titolare' : 'Nome e cognome'}</span>
                <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={120} required />
              </label>
            )}
            <label className="field"><span>{registering ? 'Email' : 'Email o nome utente'}</span>
              <input type={registering ? 'email' : 'text'} value={registering ? email : username}
                onChange={(e) => registering ? setEmail(e.target.value) : setUsername(e.target.value)}
                autoComplete={registering ? 'email' : 'username'} autoCapitalize="none" required />
            </label>
            {(mode === 'customer-register' || mode === 'company-register' || mode === 'employee-request') && (
              <label className="field"><span>Telefono <small>(facoltativo)</small></span>
                <input value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" maxLength={30} />
              </label>
            )}
            <label className="field"><span>Password{registering ? ' (almeno 8 caratteri)' : ''}</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 8 : undefined} required />
            </label>
          </>
        )}

        {notice && <p className="notice success" role="status">{notice}</p>}
        {error && <p className="tone-bad" role="alert">{error}</p>}

        <button className="btn btn-primary btn-block login-submit" disabled={busy}>
          {busy ? 'Attendi…'
            : mode === 'login' ? 'Accedi'
            : mode === 'forgot' ? 'Invia link'
            : mode === 'reset' ? 'Aggiorna password'
            : mode === 'customer-register' ? 'Crea account cliente'
            : mode === 'employee-request' ? 'Invia richiesta'
            : 'Crea spazio aziendale'}
        </button>

        {mode === 'login' && accountType === 'customer' && (
          <div className="login-actions">
            <button type="button" className="link-button" onClick={() => selectMode('customer-register')}>Non hai un account? Registrati</button>
            <button type="button" className="link-button" onClick={() => selectMode('forgot')}>Password dimenticata</button>
          </div>
        )}
        {mode === 'login' && accountType === 'store' && (
          <div className="login-actions">
            <button type="button" className="link-button" onClick={() => selectMode('employee-request')}>Richiedi accesso commesso</button>
            <button type="button" className="link-button" onClick={() => selectMode('company-register')}>Registra un negozio</button>
          </div>
        )}
        {(registering || mode === 'forgot' || mode === 'reset') && (
          <button type="button" className="btn btn-block" onClick={() => selectMode('login')}>
            Torna all'accesso
          </button>
        )}
      </form>
    </main>
  )
}
