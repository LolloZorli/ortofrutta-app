import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { useLoad } from '../hooks'
import { optionsWithCurrent, useLookups } from '../lookups'
import { useToast } from '../toast'
import type { Sale } from '../types'
import { eur, dateTimeIt } from '../format'

interface Contact {
  id: number
  name: string
  phone: string | null
  email?: string | null
  notes: string | null
  typeId?: number | null
  type?: string | null
}

type Kind = 'supplier' | 'customer'

const CONFIG: Record<Kind, { path: string; singular: string; empty: string }> = {
  supplier: { path: '/suppliers', singular: 'fornitore', empty: 'Nessun fornitore. Aggiungi il tuo grossista.' },
  customer: { path: '/customers', singular: 'cliente', empty: 'Nessun cliente. Aggiungi i clienti abituali.' },
}

export default function ContactsPage({ kind }: { kind: Kind }) {
  const cfg = CONFIG[kind]
  const toast = useToast()
  const { can } = useAuth()
  const [editing, setEditing] = useState<Contact | 'new' | null>(null)
  const [history, setHistory] = useState<{ customer: Contact; sales: Sale[] } | null>(null)
  const { data, loading, error, reload } = useLoad<Contact[]>(() => api.get(cfg.path), [kind])

  // I permessi decidono cosa si può fare: chi può solo consultare i fornitori non vede i pulsanti di modifica
  const canEdit = kind === 'supplier' ? can('SUPPLIERS_MANAGE') : can('CUSTOMERS_MANAGE') || can('RESERVATIONS_MANAGE')
  const canDelete = kind === 'supplier' ? can('SUPPLIERS_MANAGE') : can('CUSTOMERS_DELETE')

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  async function remove(c: Contact) {
    if (!window.confirm(`Eliminare ${c.name}?`)) return
    try {
      await api.del(`${cfg.path}/${c.id}`)
      toast.ok('Eliminato')
      reload()
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  async function showHistory(c: Contact) {
    try { setHistory({ customer: c, sales: await api.get<Sale[]>(`/sales/customer/${c.id}`) }) }
    catch (e) { toast.error(errorMessage(e)) }
  }

  return (
    <div className="stack">
      {canEdit && (
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + Nuovo {cfg.singular}
        </button>
      )}
      {data.length === 0 ? (
        <Empty>{cfg.empty}</Empty>
      ) : (
        <div className="list">
          {data.map((c) => (
            <div key={c.id} className="card">
              <div className="row between">
                <div className="item-title">{c.name}</div>
                {c.type && <Chip tone="blue">{c.type}</Chip>}
              </div>
              <div className="item-sub">{[c.phone, c.email].filter(Boolean).join(' · ') || 'Nessun contatto'}</div>
              {c.notes && <div className="item-sub">{c.notes}</div>}
              <div className="row wrap" style={{ marginTop: 10 }}>
                {c.phone && (
                  <a className="btn btn-sm" href={`tel:${c.phone.replace(/\s/g, '')}`}>
                    Chiama
                  </a>
                )}
                {canEdit && (
                  <button className="btn btn-sm" onClick={() => setEditing(c)}>
                    Modifica
                  </button>
                )}
                {kind === 'customer' && <button className="btn btn-sm" onClick={() => showHistory(c)}>Storico acquisti</button>}
                {canDelete && (
                  <button className="btn btn-sm btn-danger" onClick={() => remove(c)}>
                    Elimina
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <ContactForm
          kind={kind}
          contact={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reload()
          }}
        />
      )}
      {history && <Modal title={`Acquisti di ${history.customer.name}`} onClose={() => setHistory(null)}>
        {history.sales.length === 0 ? <p className="muted">Nessuna vendita associata.</p> : <div className="list">{history.sales.map((s) => <div className="card row between" key={s.id}><span>{dateTimeIt(s.createdAt)} · {s.paymentMethod}</span><strong>{eur(s.total)}</strong></div>)}</div>}
      </Modal>}
    </div>
  )
}

function ContactForm({ kind, contact, onClose, onSaved }: { kind: Kind; contact: Contact | null; onClose: () => void; onSaved: () => void }) {
  const cfg = CONFIG[kind]
  const toast = useToast()
  const { lookups } = useLookups()
  const [name, setName] = useState(contact?.name ?? '')
  const [phone, setPhone] = useState(contact?.phone ?? '')
  const [email, setEmail] = useState(contact?.email ?? '')
  const [notes, setNotes] = useState(contact?.notes ?? '')
  const [typeId, setTypeId] = useState<number | ''>(contact?.typeId ?? '')
  const [busy, setBusy] = useState(false)
  const types = optionsWithCurrent(lookups.CUSTOMER_TYPE, contact?.typeId, contact?.type)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    const body: Record<string, string | number | null> = {
      name: name.trim(),
      phone: phone.trim() || null,
      notes: notes.trim() || null,
    }
    if (kind === 'supplier') body.email = email.trim() || null
    if (kind === 'customer') body.typeId = typeId === '' ? null : typeId
    try {
      if (contact) await api.put(`${cfg.path}/${contact.id}`, body)
      else await api.post(cfg.path, body)
      toast.ok('Salvato')
      onSaved()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={contact ? `Modifica ${cfg.singular}` : `Nuovo ${cfg.singular}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nome">
          <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        {kind === 'customer' && (
          <Field label="Tipo di cliente (facoltativo)">
            <select value={typeId} onChange={(e) => setTypeId(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">—</option>
              {types.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </Field>
        )}
        <Field label="Telefono / WhatsApp">
          <input value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" />
        </Field>
        {kind === 'supplier' && (
          <Field label="Email">
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
          </Field>
        )}
        <Field label="Note">
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Salva
        </button>
      </form>
    </Modal>
  )
}
