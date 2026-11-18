import { useEffect, useState, type FormEvent } from 'react'
import { api, companySession, errorMessage } from '../api'
import { useAuth } from '../auth'
import type { CompanyShop } from '../types'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [registering, setRegistering] = useState(false)
  const [registeringCompany, setRegisteringCompany] = useState(false)
  const [companyName, setCompanyName] = useState('')
  const [companySlug, setCompanySlug] = useState(companySession.slug())
  const [shops, setShops] = useState<CompanyShop[]>([])
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<CompanyShop[]>('/companies').then(setShops).catch(() => setShops([]))
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      companySession.select(companySlug)
      if (registeringCompany) {
        await api.post('/companies/register', {
          companyName: companyName.trim(), slug: companySlug.trim(), ownerName: name.trim(),
          email: email.trim(), password, phone: phone.trim() || null,
        })
        await login(email.trim(), password)
      } else if (registering) {
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
        <p className="muted center">{registeringCompany ? 'Apri il portale della tua azienda' : registering ? 'Crea o collega il tuo account cliente a un ortofrutta' : 'Accedi al gestionale o al portale ordini'}</p>
        {!registeringCompany && <label className="field"><span>Ortofrutta</span>
          <select value={companySlug} onChange={(e) => setCompanySlug(e.target.value)} required>
            {shops.map((shop) => <option key={shop.id} value={shop.slug}>{shop.name}</option>)}
            {!shops.some((shop) => shop.slug === companySlug) && <option value={companySlug}>{companySlug}</option>}
          </select>
        </label>}
        {registeringCompany && <>
          <label className="field"><span>Nome dell'azienda</span><input value={companyName} onChange={(e) => setCompanyName(e.target.value)} required /></label>
          <label className="field"><span>Indirizzo negozio (slug)</span><input value={companySlug} onChange={(e) => setCompanySlug(e.target.value.toLowerCase())} pattern="[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*" minLength={3} maxLength={80} required /></label>
        </>}
        {(registering || registeringCompany) && <label className="field"><span>{registeringCompany ? 'Nome del titolare' : 'Nome e cognome'}</span><input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required /></label>}
        <label className="field">
          <span>{registering || registeringCompany ? 'Email' : 'Email o nome utente'}</span>
          <input type={registering || registeringCompany ? 'email' : 'text'}
            value={registering || registeringCompany ? email : username}
            onChange={(e) => registering || registeringCompany ? setEmail(e.target.value) : setUsername(e.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            required
          />
        </label>
        {(registering || registeringCompany) && <label className="field"><span>Telefono (facoltativo)</span><input value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" /></label>}
        <label className="field">
          <span>Password{registering || registeringCompany ? ' (almeno 8 caratteri)' : ''}</span>
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
          {busy ? (registering || registeringCompany ? 'Creazione…' : 'Accesso…') : registeringCompany ? 'Crea azienda' : registering ? 'Crea o collega account cliente' : 'Accedi'}
        </button>
        {!registeringCompany && <button type="button" className="btn btn-sm" disabled={busy} onClick={() => { setRegistering((value) => !value); setError(null) }}>
          {registering ? 'Hai già un account? Accedi' : 'Sei un cliente? Crea un account per ordinare'}
        </button>}
        <button type="button" className="btn btn-sm" disabled={busy} onClick={() => { setRegisteringCompany((value) => !value); setRegistering(false); setError(null) }}>
          {registeringCompany ? 'Torna all’accesso' : 'Gestisci un ortofrutta? Registra la tua azienda'}
        </button>
      </form>
    </div>
  )
}
