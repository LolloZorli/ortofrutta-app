import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import ProductSelect from '../components/ProductSelect'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { dateTimeIt, eur, parseDecimal, qty } from '../format'
import { useLoad, usePaged } from '../hooks'
import { useToast } from '../toast'
import { useAuth } from '../auth'
import type { LowStockItem, PageResponse, Product, Supplier, SupplierOrder, SupplierOrderStatus } from '../types'

const STATUS: Record<SupplierOrderStatus, { label: string; tone: 'gray' | 'blue' | 'green' | 'red' }> = {
  DRAFT: { label: 'Bozza', tone: 'gray' },
  SENT: { label: 'Inviato', tone: 'blue' },
  RECEIVED: { label: 'Ricevuto', tone: 'green' },
  CANCELLED: { label: 'Annullato', tone: 'red' },
}

function orderText(o: SupplierOrder): string {
  const rows = o.lines.map((l) => `• ${l.productName}: ${qty(l.quantity, l.unit)}`)
  const notes = o.notes ? `\nNote: ${o.notes}` : ''
  return `Buongiorno, ordine del ${new Date().toLocaleDateString('it-IT')}:\n${rows.join('\n')}${notes}\nGrazie!`
}

/** Link WhatsApp: se il numero è senza prefisso internazionale si assume l'Italia (+39). */
function whatsappLink(phone: string | null, text: string): string {
  const digits = (phone ?? '').replace(/\D/g, '')
  const number = digits && (digits.startsWith('39') || digits.length > 10) ? digits : digits ? `39${digits}` : ''
  return `https://wa.me/${number}?text=${encodeURIComponent(text)}`
}

export default function SupplierOrdersPage() {
  const { can } = useAuth()
  const canManage = can('SUPPLIER_ORDERS_MANAGE')
  const canSeeCosts = can('COSTS_VIEW')
  const [creating, setCreating] = useState(false)
  const [receiving, setReceiving] = useState<SupplierOrder | null>(null)
  const { items, loading, loadingMore, error, hasMore, loadMore, reload } = usePaged<SupplierOrder>(
    (page) => api.get<PageResponse<SupplierOrder>>(`/supplier-orders?page=${page}&size=20`),
  )

  if (loading && items.length === 0) return <Loading />
  if (error && items.length === 0) return <ErrorBox message={error} onRetry={reload} />

  return (
    <div className="stack">
      <button className="btn btn-primary" onClick={() => setCreating(true)}>
        + Nuovo ordine
      </button>
      {items.length === 0 ? (
        <Empty>Nessun ordine. Crea il primo, anche partendo dai prodotti sotto scorta.</Empty>
      ) : (
        <div className="list">
          {items.map((o) => (
            <OrderCard key={o.id} order={o} canManage={canManage} onChanged={reload} onReceive={() => setReceiving(o)} />
          ))}
          {hasMore && (
            <button className="btn" disabled={loadingMore} onClick={() => void loadMore()}>
              {loadingMore ? 'Carico…' : 'Carica altri'}
            </button>
          )}
        </div>
      )}
      {creating && (
        <NewOrderModal
          canSeeCosts={canSeeCosts}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            reload()
          }}
        />
      )}
      {receiving && (
        <ReceiveModal
          order={receiving}
          onClose={() => setReceiving(null)}
          onDone={() => {
            setReceiving(null)
            reload()
          }}
        />
      )}
    </div>
  )
}

function OrderCard({ order: o, canManage, onChanged, onReceive }: { order: SupplierOrder; canManage: boolean; onChanged: () => void; onReceive: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const st = STATUS[o.status]

  async function act(fn: () => Promise<unknown>, okMessage: string) {
    setBusy(true)
    try {
      await fn()
      toast.ok(okMessage)
      onChanged()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function sendWhatsapp() {
    // window.open deve partire subito dal tocco, altrimenti il browser blocca la finestra
    window.open(whatsappLink(o.supplierPhone, orderText(o)), '_blank', 'noopener')
    return act(() => api.post(`/supplier-orders/${o.id}/send`), 'Ordine segnato come inviato')
  }

  return (
    <div className="card">
      <div className="row between">
        <div>
          <div className="item-title">{o.supplierName}</div>
          <div className="item-sub">{dateTimeIt(o.createdAt)}</div>
        </div>
        <Chip tone={st.tone}>{st.label}</Chip>
      </div>
      <ul className="lines-summary">
        {o.lines.map((l) => (
          <li key={l.id}>
            {l.productName}: {qty(l.quantity, l.unit)}
          </li>
        ))}
      </ul>
      {o.notes && <div className="item-sub">Note: {o.notes}</div>}
      {o.total !== null && o.total > 0 && (
        <div className="price-line" style={{ marginTop: 6 }}>
          <span className="muted">Totale stimato</span>
          <strong>{eur(o.total)}</strong>
        </div>
      )}
      {!canManage && (o.status === 'DRAFT' || o.status === 'SENT') &&
        <div className="notice">Ordine in attesa che un responsabile lo invii o registri la merce ricevuta.</div>}
      <div className="row wrap" style={{ marginTop: 10 }}>
        {canManage && o.status === 'DRAFT' && (
          <>
            <button className="btn btn-primary btn-sm" disabled={busy} onClick={sendWhatsapp}>
              Invia su WhatsApp
            </button>
            <button
              className="btn btn-sm"
              disabled={busy}
              onClick={() => act(() => api.post(`/supplier-orders/${o.id}/send`), 'Segnato come inviato')}
            >
              Segna inviato
            </button>
          </>
        )}
        {canManage && (o.status === 'DRAFT' || o.status === 'SENT') && (
          <button className="btn btn-sm" disabled={busy} onClick={onReceive}>
            Merce arrivata
          </button>
        )}
        {canManage && o.status === 'SENT' && (
          <button
            className="btn btn-sm btn-danger"
            disabled={busy}
            onClick={() => act(() => api.post(`/supplier-orders/${o.id}/cancel`), 'Ordine annullato')}
          >
            Annulla
          </button>
        )}
        {canManage && (o.status === 'DRAFT' || o.status === 'CANCELLED') && (
          <button
            className="btn btn-sm btn-danger"
            disabled={busy}
            onClick={() => act(() => api.del(`/supplier-orders/${o.id}`), 'Ordine eliminato')}
          >
            Elimina
          </button>
        )}
      </div>
    </div>
  )
}

interface DraftLine {
  productId: string
  quantity: string
  unitCost: string
}

function NewOrderModal({ canSeeCosts, onClose, onCreated }: { canSeeCosts: boolean; onClose: () => void; onCreated: () => void }) {
  const toast = useToast()
  const suppliers = useLoad<Supplier[]>(() => api.get('/suppliers'))
  const products = useLoad<Product[]>(() => api.get('/products'))
  const [supplierId, setSupplierId] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([{ productId: '', quantity: '', unitCost: '' }])
  const [busy, setBusy] = useState(false)

  function setLine(i: number, patch: Partial<DraftLine>) {
    setLines((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  }

  async function fillFromSuggestions() {
    try {
      const items = await api.get<LowStockItem[]>('/reports/reorder-suggestions')
      if (items.length === 0) return toast.ok('Nessun prodotto sotto scorta')
      setLines(
        items.map((i) => ({
          productId: String(i.productId),
          quantity: String(i.suggestedQuantity),
          unitCost: i.lastUnitCost !== null ? String(i.lastUnitCost) : '',
        })),
      )
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!supplierId) return toast.error('Scegli il fornitore')
    const body = []
    for (const l of lines) {
      if (!l.productId && !l.quantity) continue
      const q = parseDecimal(l.quantity)
      if (!l.productId || !(q > 0)) return toast.error('Ogni riga ha bisogno di prodotto e quantità')
      const c = parseDecimal(l.unitCost)
      body.push({ productId: Number(l.productId), quantity: q, unitCost: Number.isNaN(c) ? undefined : c })
    }
    if (body.length === 0) return toast.error('Aggiungi almeno un prodotto')
    setBusy(true)
    try {
      await api.post('/supplier-orders', { supplierId: Number(supplierId), notes: notes.trim() || null, lines: body })
      toast.ok('Ordine creato')
      onCreated()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  const productList = products.data ?? []

  return (
    <Modal title="Nuovo ordine al fornitore" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Fornitore">
          <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">Scegli…</option>
            {(suppliers.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>
        {suppliers.data && suppliers.data.length === 0 && (
          <p className="muted">Nessun fornitore: aggiungilo da Altro → Fornitori.</p>
        )}

        <button type="button" className="btn btn-sm" onClick={fillFromSuggestions}>
          ✨ Precompila con i prodotti sotto scorta
        </button>

        <div className="stack">
          {lines.map((l, i) => (
            <div key={i} className={`line-row${canSeeCosts ? ' with-cost' : ''}`}>
              <ProductSelect products={productList} value={l.productId} onChange={(v) => setLine(i, { productId: v })} />
              <input
                placeholder="Qtà"
                inputMode="decimal"
                value={l.quantity}
                onChange={(e) => setLine(i, { quantity: e.target.value })}
              />
              {canSeeCosts && <input
                  placeholder="€ costo"
                  inputMode="decimal"
                  value={l.unitCost}
                  onChange={(e) => setLine(i, { unitCost: e.target.value })}
                />}
              <button
                type="button"
                className="icon-btn"
                aria-label="Rimuovi riga"
                onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((_, idx) => idx !== i) : ls))}
              >
                ✕
              </button>
            </div>
          ))}
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => setLines((ls) => [...ls, { productId: '', quantity: '', unitCost: '' }])}
          >
            + Aggiungi prodotto
          </button>
        </div>

        <Field label="Note (facoltative)">
          <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Crea ordine
        </button>
      </form>
    </Modal>
  )
}

interface ReceiveLine {
  lineId: number
  name: string
  unit: SupplierOrder['lines'][number]['unit']
  quantity: string
  unitCost: string
  expiresAt: string
}

function ReceiveModal({ order, onClose, onDone }: { order: SupplierOrder; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [lines, setLines] = useState<ReceiveLine[]>(
    order.lines.map((l) => ({
      lineId: l.id,
      name: l.productName,
      unit: l.unit,
      quantity: String(l.quantity),
      unitCost: l.unitCost !== null && l.unitCost > 0 ? String(l.unitCost) : '',
      expiresAt: '',
    })),
  )
  const [busy, setBusy] = useState(false)

  function setLine(i: number, patch: Partial<ReceiveLine>) {
    setLines((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const body = []
    for (const l of lines) {
      const q = parseDecimal(l.quantity)
      const c = parseDecimal(l.unitCost)
      if (!(q >= 0)) return toast.error(`Quantità non valida per ${l.name}`)
      if (q > 0 && !(c >= 0)) return toast.error(`Inserisci il costo per ${l.name}`)
      body.push({ lineId: l.lineId, quantity: q, unitCost: Number.isNaN(c) ? 0 : c, expiresAt: l.expiresAt || null })
    }
    setBusy(true)
    try {
      await api.post(`/supplier-orders/${order.id}/receive`, { lines: body })
      toast.ok('Merce caricata a magazzino')
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={`Arrivo merce: ${order.supplierName}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <p className="muted">
          Controlla quantità e costo realmente fatturati. Ogni riga diventa un lotto a magazzino.
        </p>
        {lines.map((l, i) => (
          <div key={l.lineId} className="card stack">
            <div className="item-title">{l.name}</div>
            <div className="row">
              <div className="grow">
                <Field label="Ricevuti">
                  <input inputMode="decimal" value={l.quantity} onChange={(e) => setLine(i, { quantity: e.target.value })} />
                </Field>
              </div>
              <div className="grow">
                <Field label="Costo (€)">
                  <input inputMode="decimal" value={l.unitCost} onChange={(e) => setLine(i, { unitCost: e.target.value })} />
                </Field>
              </div>
            </div>
            <Field label="Consumo entro (facoltativo)">
              <input type="date" value={l.expiresAt} onChange={(e) => setLine(i, { expiresAt: e.target.value })} />
            </Field>
          </div>
        ))}
        <button className="btn btn-primary btn-block" disabled={busy}>
          Conferma arrivo
        </button>
      </form>
    </Modal>
  )
}
