import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { num, parseDecimal } from '../format'
import { useLoad } from '../hooks'
import { useLookups } from '../lookups'
import { useToast } from '../toast'
import type { LookupItem, LookupTypeInfo, LookupTypeName } from '../types'

/** Gestione delle tipologiche: tabelle di valori fissi (categorie, unità, IVA...) che solo l'amministratore modifica. */
export default function LookupsPage() {
  const types = useLoad<LookupTypeInfo[]>(() => api.get('/admin/lookups'))
  const [type, setType] = useState<LookupTypeName>('CATEGORY')
  const [editing, setEditing] = useState<LookupItem | 'new' | null>(null)
  const items = useLoad<LookupItem[]>(() => api.get(`/admin/lookups/${type}`), [type])
  const shared = useLookups()

  if (types.loading && !types.data) return <Loading />
  if (types.error || !types.data) return <ErrorBox message={types.error ?? 'Nessun dato'} onRetry={types.reload} />

  const info = types.data.find((t) => t.type === type) ?? types.data[0]

  const saved = () => {
    setEditing(null)
    items.reload()
    shared.reload() // aggiorna anche gli elenchi usati dai moduli dell'app
  }

  return (
    <div>
      <div className="tabs">
        {types.data.map((t) => (
          <button key={t.type} className={type === t.type ? 'active' : ''} onClick={() => setType(t.type)}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="stack">
        <p className="muted">
          I valori non si cancellano (i dati già registrati li usano): si <strong>disattivano</strong>, e da quel momento non
          si possono più scegliere per i nuovi dati.
        </p>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + Aggiungi a «{info.label}»
        </button>

        {items.loading && !items.data ? (
          <Loading />
        ) : items.error || !items.data ? (
          <ErrorBox message={items.error ?? 'Nessun dato'} onRetry={items.reload} />
        ) : items.data.length === 0 ? (
          <Empty>Nessun valore.</Empty>
        ) : (
          <div className="list">
            {items.data.map((i) => (
              <div key={i.id} className="card row between" style={i.active ? undefined : { opacity: 0.6 }}>
                <div>
                  <div className="item-title">{i.name}</div>
                  <div className="item-sub">
                    {info.hasUnitAttributes && `${i.shortLabel} · ${i.allowDecimals ? 'ammette decimali' : 'solo interi'}`}
                    {info.hasRate && i.rate !== null && `${num(i.rate)}%`}
                    {!info.hasUnitAttributes && !info.hasRate && `codice ${i.code}`}
                  </div>
                </div>
                <div className="row">
                  {!i.active && <Chip tone="gray">disattivato</Chip>}
                  <button className="btn btn-sm" onClick={() => setEditing(i)}>
                    Modifica
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {editing && (
        <LookupForm
          type={type}
          info={info}
          item={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={saved}
        />
      )}
    </div>
  )
}

function LookupForm({
  type,
  info,
  item,
  onClose,
  onSaved,
}: {
  type: LookupTypeName
  info: LookupTypeInfo
  item: LookupItem | null
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const [name, setName] = useState(item?.name ?? '')
  const [sortOrder, setSortOrder] = useState(item ? String(item.sortOrder) : '')
  const [active, setActive] = useState(item?.active ?? true)
  const [shortLabel, setShortLabel] = useState(item?.shortLabel ?? '')
  const [allowDecimals, setAllowDecimals] = useState(item?.allowDecimals ?? false)
  const [rate, setRate] = useState(item?.rate !== null && item?.rate !== undefined ? String(item.rate) : '')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const body: Record<string, unknown> = { name: name.trim(), active }
    const sort = parseDecimal(sortOrder)
    if (Number.isInteger(sort)) body.sortOrder = sort
    if (info.hasUnitAttributes) {
      if (!shortLabel.trim()) return toast.error("Indica l'etichetta breve (es. kg)")
      body.shortLabel = shortLabel.trim()
      body.allowDecimals = allowDecimals
    }
    if (info.hasRate) {
      const r = parseDecimal(rate)
      if (!(r >= 0 && r <= 100)) return toast.error('Indica una percentuale tra 0 e 100')
      body.rate = r
    }
    setBusy(true)
    try {
      if (item) await api.put(`/admin/lookups/${type}/${item.id}`, body)
      else await api.post(`/admin/lookups/${type}`, body)
      toast.ok('Salvato')
      onSaved()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={item ? `Modifica: ${item.name}` : `Nuovo valore: ${info.label}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nome">
          <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        {info.hasUnitAttributes && (
          <>
            <Field label="Etichetta breve" hint="Come compare accanto alle quantità: kg, pz, mazzo...">
              <input value={shortLabel} onChange={(e) => setShortLabel(e.target.value)} />
            </Field>
            <label className="check-row">
              <input type="checkbox" checked={allowDecimals} onChange={(e) => setAllowDecimals(e.target.checked)} />
              Ammette quantità con decimali (es. 1,5 kg)
            </label>
          </>
        )}
        {info.hasRate && (
          <Field
            label="Percentuale (%)"
            hint="Cambiarla vale per le vendite future: quelle già registrate mantengono l'aliquota di allora"
          >
            <input value={rate} onChange={(e) => setRate(e.target.value)} inputMode="decimal" />
          </Field>
        )}
        <Field label="Ordine nell'elenco (facoltativo)">
          <input value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} inputMode="numeric" />
        </Field>
        {item && (
          <label className="check-row">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Attivo (se disattivato non si può più scegliere per i nuovi dati)
          </label>
        )}
        <button className="btn btn-primary btn-block" disabled={busy}>
          Salva
        </button>
      </form>
    </Modal>
  )
}
