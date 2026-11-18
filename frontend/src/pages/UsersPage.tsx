import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import { Chip, ErrorBox, Field, Loading } from '../components/ui'
import { useLoad } from '../hooks'
import { useToast } from '../toast'
import type { RoleOption, UserAccount } from '../types'

type Editing = { kind: 'new' } | { kind: 'edit'; user: UserAccount } | { kind: 'reset'; user: UserAccount }

export default function UsersPage() {
  const { profile, can, canAny } = useAuth()
  const toast = useToast()
  const [editing, setEditing] = useState<Editing | null>(null)
  const { data, loading, error, reload } = useLoad<UserAccount[]>(() => api.get('/users'))
  const roles = useLoad<RoleOption[]>(() => api.get('/users/role-options'))

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  const close = () => setEditing(null)
  const done = () => {
    setEditing(null)
    reload()
  }
  const roleOptions = roles.data ?? []
  const canManage = can('USERS_MANAGE')
  const canApprove = canAny('USERS_APPROVE', 'USERS_MANAGE')

  async function approve(user: UserAccount) {
    try {
      await api.post(`/users/${user.id}/approve`)
      toast.ok(`${user.username} è stato attivato`)
      reload()
    } catch (err) { toast.error(errorMessage(err)) }
  }

  return (
    <div className="stack">
      <p className="muted">
        Qui trovi commessi e richieste di accesso. I clienti e i loro recapiti sono nella sezione <strong>Clienti</strong>.
      </p>
      {canManage && <button className="btn btn-primary" onClick={() => setEditing({ kind: 'new' })}>+ Nuovo utente</button>}

      <div className="list">
        {data.map((u) => {
          const isMe = u.username === profile?.username
          return (
            <div key={u.id} className="card">
              <div className="row between">
                <div>
                  <div className="item-title">
                    {u.username} {isMe && <span className="muted">(tu)</span>}
                  </div>
                  <div className="item-sub">{u.roleName}</div>
                  {u.email && <div className="item-sub">{u.email}</div>}
                </div>
                <div className="row">
                  {!u.approved && <Chip tone="orange">in attesa</Chip>}
                  {u.approved && <Chip tone={u.enabled ? 'green' : 'gray'}>{u.enabled ? 'attivo' : 'disattivato'}</Chip>}
                  {u.mustChangePassword && <Chip tone="orange">password temporanea</Chip>}
                </div>
              </div>
              <div className="row wrap" style={{ marginTop: 10 }}>
                {!u.approved && canApprove && <button className="btn btn-primary btn-sm" onClick={() => void approve(u)}>Approva accesso</button>}
                {canManage && !isMe && (
                  <button className="btn btn-sm" onClick={() => setEditing({ kind: 'edit', user: u })}>
                    Ruolo e stato
                  </button>
                )}
                {canManage && <button className="btn btn-sm" onClick={() => setEditing({ kind: 'reset', user: u })}>
                  Reimposta password
                </button>}
              </div>
            </div>
          )
        })}
      </div>

      {editing?.kind === 'new' && <NewUserForm roles={roleOptions} onClose={close} onDone={done} />}
      {editing?.kind === 'edit' && <EditUserForm user={editing.user} roles={roleOptions} onClose={close} onDone={done} />}
      {editing?.kind === 'reset' && <ResetPasswordForm user={editing.user} onClose={close} onDone={done} />}
    </div>
  )
}

function RoleSelect({ roles, value, onChange }: { roles: RoleOption[]; value: number | ''; onChange: (id: number) => void }) {
  return (
    <select value={value} onChange={(e) => onChange(Number(e.target.value))}>
      {roles.map((r) => (
        <option key={r.id} value={r.id}>
          {r.name}
        </option>
      ))}
    </select>
  )
}

function NewUserForm({ roles, onClose, onDone }: { roles: RoleOption[]; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [roleId, setRoleId] = useState<number | ''>('')
  const [busy, setBusy] = useState(false)
  const fallback: number | '' = roles.find((r) => r.code === 'STAFF')?.id ?? (roles.length > 0 ? roles[0].id : '')
  const role: number | '' = roleId === '' ? fallback : roleId

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password.length < 8) return toast.error('La password deve avere almeno 8 caratteri')
    if (role === '') return toast.error('Scegli il ruolo')
    setBusy(true)
    try {
      await api.post('/users', { username: username.trim(), email: email.trim() || null, password, roleId: role })
      toast.ok('Utente creato')
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title="Nuovo utente" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nome utente" hint="Lettere, numeri, punto, trattino e underscore">
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoCapitalize="none" required autoFocus />
        </Field>
        <Field label="Email (facoltativa)" hint="Può essere usata anche per accedere">
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        </Field>
        <Field label="Ruolo">
          <RoleSelect roles={roles} value={role} onChange={setRoleId} />
        </Field>
        <Field label="Password temporanea" hint="L'utente dovrà cambiarla al primo accesso">
          <input value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Crea utente
        </button>
      </form>
    </Modal>
  )
}

function EditUserForm({ user, roles, onClose, onDone }: { user: UserAccount; roles: RoleOption[]; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [roleId, setRoleId] = useState<number>(user.roleId)
  const [enabled, setEnabled] = useState(user.enabled)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      await api.put(`/users/${user.id}`, { roleId, enabled })
      toast.ok('Utente aggiornato')
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={`Utente ${user.username}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Ruolo">
          <RoleSelect roles={roles} value={roleId} onChange={setRoleId} />
        </Field>
        <label className="check-row">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          Utente attivo (se disattivato, perde subito l'accesso)
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Salva
        </button>
      </form>
    </Modal>
  )
}

function ResetPasswordForm({ user, onClose, onDone }: { user: UserAccount; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password.length < 8) return toast.error('La password deve avere almeno 8 caratteri')
    setBusy(true)
    try {
      await api.post(`/users/${user.id}/reset-password`, { newPassword: password })
      toast.ok('Password reimpostata: dovrà cambiarla al prossimo accesso')
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={`Reimposta password: ${user.username}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nuova password temporanea" hint="Comunicala all'utente: dovrà cambiarla al primo accesso">
          <input value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Reimposta
        </button>
      </form>
    </Modal>
  )
}
