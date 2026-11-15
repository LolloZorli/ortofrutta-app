import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import { Chip, ErrorBox, Field, Loading } from '../components/ui'
import { useLoad } from '../hooks'
import { useToast } from '../toast'
import type { PermissionInfo, RoleInfo } from '../types'

export default function RolesPage() {
  const toast = useToast()
  const [editing, setEditing] = useState<RoleInfo | 'new' | null>(null)
  const roles = useLoad<RoleInfo[]>(() => api.get('/roles'))
  const perms = useLoad<PermissionInfo[]>(() => api.get('/roles/permissions'))

  if ((roles.loading && !roles.data) || (perms.loading && !perms.data)) return <Loading />
  if (roles.error || !roles.data) return <ErrorBox message={roles.error ?? 'Nessun dato'} onRetry={roles.reload} />
  if (perms.error || !perms.data) return <ErrorBox message={perms.error ?? 'Nessun dato'} onRetry={perms.reload} />

  async function remove(r: RoleInfo) {
    if (!window.confirm(`Eliminare il ruolo "${r.name}"?`)) return
    try {
      await api.del(`/roles/${r.id}`)
      toast.ok('Ruolo eliminato')
      roles.reload()
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  return (
    <div className="stack">
      <p className="muted">
        Un ruolo è un insieme di permessi. Crea i ruoli che servono al tuo negozio e assegnali agli utenti. I permessi di
        amministrazione (utenti, ruoli, tipologiche) restano riservati all'amministratore.
      </p>
      <button className="btn btn-primary" onClick={() => setEditing('new')}>
        + Nuovo ruolo
      </button>

      <div className="list">
        {roles.data.map((r) => (
          <div key={r.id} className="card">
            <div className="row between">
              <div>
                <div className="item-title">{r.name}</div>
                {r.description && <div className="item-sub">{r.description}</div>}
              </div>
              {r.system && <Chip tone="green">di sistema</Chip>}
            </div>
            <div className="item-sub" style={{ marginTop: 6 }}>
              {r.system ? 'Tutti i permessi' : `${r.permissions.length} permessi`} · {r.userCount}{' '}
              {r.userCount === 1 ? 'utente' : 'utenti'}
            </div>
            {!r.system && (
              <div className="row" style={{ marginTop: 10 }}>
                <button className="btn btn-sm" onClick={() => setEditing(r)}>
                  Modifica
                </button>
                <button className="btn btn-sm btn-danger" onClick={() => remove(r)}>
                  Elimina
                </button>
              </div>
            )}
          </div>
        ))}
      </div>

      {editing && (
        <RoleForm
          role={editing === 'new' ? null : editing}
          catalog={perms.data}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            roles.reload()
          }}
        />
      )}
    </div>
  )
}

function RoleForm({
  role,
  catalog,
  onClose,
  onSaved,
}: {
  role: RoleInfo | null
  catalog: PermissionInfo[]
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const [name, setName] = useState(role?.name ?? '')
  const [description, setDescription] = useState(role?.description ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set(role?.permissions ?? []))
  const [busy, setBusy] = useState(false)

  const groups = catalog.reduce<Record<string, PermissionInfo[]>>((acc, p) => {
    ;(acc[p.group] ??= []).push(p)
    return acc
  }, {})

  function toggle(code: string) {
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      const body = { name: name.trim(), description: description.trim() || null, permissions: [...selected] }
      if (role) await api.put(`/roles/${role.id}`, body)
      else await api.post('/roles', body)
      toast.ok('Ruolo salvato')
      onSaved()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={role ? `Ruolo ${role.name}` : 'Nuovo ruolo'} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nome">
          <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        <Field label="Descrizione (facoltativa)">
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>

        {Object.entries(groups).map(([group, items]) => (
          <div key={group} className="perm-group">
            <h4>{group}</h4>
            {items.map((p) => (
              <label key={p.code} className="check-row">
                <input
                  type="checkbox"
                  checked={selected.has(p.code)}
                  disabled={p.adminOnly}
                  onChange={() => toggle(p.code)}
                />
                <span>
                  {p.label}
                  {p.adminOnly && <span className="muted"> (solo amministratore)</span>}
                </span>
              </label>
            ))}
          </div>
        ))}
        <p className="muted">
          Alcuni permessi ne richiedono altri e vengono aggiunti da soli (per esempio, chi vende deve poter vedere il listino).
        </p>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Salva ruolo
        </button>
      </form>
    </Modal>
  )
}
