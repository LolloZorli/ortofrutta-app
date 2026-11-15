import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { dateIt, dateTimeIt, eur, expiryLabel, parseDecimal, qty } from '../format'
import { useLoad, usePaged } from '../hooks'
import { useLookups } from '../lookups'
import { useToast } from '../toast'
import type { Lot, Movement, PageResponse, Product, Supplier } from '../types'

type Tab = 'prodotti' | 'lotti' | 'movimenti'
type Action = { kind: 'waste' | 'receipt'; product: Product }

export default function StockPage() {
  const [tab, setTab] = useState<Tab>('prodotti')
  return (
    <div>
      <div className="tabs">
        {(['prodotti', 'lotti', 'movimenti'] as Tab[]).map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
            {t === 'prodotti' ? 'Prodotti' : t === 'lotti' ? 'Lotti' : 'Movimenti'}
          </button>
        ))}
      </div>
      {tab === 'prodotti' && <ProductsTab />}
      {tab === 'lotti' && <LotsTab />}
      {tab === 'movimenti' && <MovementsTab />}
    </div>
  )
}

function ProductsTab() {
  const { can } = useAuth()
  const [search, setSearch] = useState('')
  const [action, setAction] = useState<Action | null>(null)
  const { data, loading, error, reload } = useLoad<Product[]>(() => api.get('/products'))

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  const q = search.trim().toLowerCase()
  const rows = data.filter((p) => !q || p.name.toLowerCase().includes(q))
  const canWaste = can('WASTE_CREATE')
  const canReceive = can('STOCK_RECEIVE')

  return (
    <div className="stack">
      <input placeholder="Cerca prodotto…" value={search} onChange={(e) => setSearch(e.target.value)} />
      {rows.length === 0 ? (
        <Empty>
          {can('PRODUCTS_WRITE') ? 'Nessun prodotto. Aggiungili da Altro → Prodotti.' : "Nessun prodotto: chiedi all'amministratore di aggiungerli."}
        </Empty>
      ) : (
        <div className="list">
          {rows.map((p) => (
            <div key={p.id} className="card">
              <div className="row between">
                <div>
                  <div className="item-title">{p.name}</div>
                  <div className="item-sub">
                    {p.category} · {eur(p.salePrice)}/{p.unit}
                  </div>
                </div>
                <Chip tone={p.stock <= 0 ? 'red' : p.lowStock ? 'orange' : 'green'}>{qty(p.stock, p.unit)}</Chip>
              </div>
              {(canWaste || canReceive) && (
                <div className="row" style={{ marginTop: 10 }}>
                  {canWaste && (
                    <button className="btn btn-sm grow" onClick={() => setAction({ kind: 'waste', product: p })}>
                      Scarto
                    </button>
                  )}
                  {canReceive && (
                    <button className="btn btn-sm grow" onClick={() => setAction({ kind: 'receipt', product: p })}>
                      Carico
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {action && (
        <ActionModal
          action={action}
          onClose={() => setAction(null)}
          onDone={() => {
            setAction(null)
            reload()
          }}
        />
      )}
    </div>
  )
}

const PRESETS_DECIMAL = ['0.5', '1', '2', '5']
const PRESETS_WHOLE = ['1', '2', '5', '10']

function ActionModal({ action, onClose, onDone }: { action: Action; onClose: () => void; onDone: () => void }) {
  const { kind, product } = action
  const toast = useToast()
  const { lookups } = useLookups()
  const [quantity, setQuantity] = useState('')
  const [reasonId, setReasonId] = useState<number | null>(null)
  const [cost, setCost] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [busy, setBusy] = useState(false)
  const suppliers = useLoad<Supplier[]>(
    () => (kind === 'receipt' ? api.get<Supplier[]>('/suppliers') : Promise.resolve([])),
    [kind],
  )

  const reasons = lookups.WASTE_REASON
  const reason = reasonId ?? reasons[0]?.id ?? null
  const title = kind === 'waste' ? 'Scarto' : 'Carico merce'

  async function submit(e: FormEvent) {
    e.preventDefault()
    const q = parseDecimal(quantity)
    if (!(q > 0)) return toast.error('Inserisci una quantità valida')
    if (!product.allowDecimals && !Number.isInteger(q)) {
      return toast.error(`Per ${product.name} (${product.unit}) la quantità deve essere un numero intero`)
    }
    setBusy(true)
    try {
      if (kind === 'waste') {
        if (reason === null) {
          setBusy(false)
          return toast.error('Scegli il motivo')
        }
        await api.post('/stock/waste', { productId: product.id, quantity: q, reasonId: reason })
        toast.ok('Scarto registrato')
      } else {
        const c = parseDecimal(cost)
        if (!(c >= 0)) {
          setBusy(false)
          return toast.error('Inserisci il costo di acquisto')
        }
        await api.post('/stock/receipts', {
          productId: product.id,
          supplierId: supplierId ? Number(supplierId) : undefined,
          quantity: q,
          unitCost: c,
          expiresAt: expiresAt || undefined,
        })
        toast.ok('Carico registrato')
      }
      onDone()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={`${title}: ${product.name}`} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <p className="muted">In magazzino: {qty(product.stock, product.unit)}</p>
        <Field label="Quantità">
          <input value={quantity} onChange={(e) => setQuantity(e.target.value)} inputMode="decimal" autoFocus required />
        </Field>
        <div className="chips-row">
          {(product.allowDecimals ? PRESETS_DECIMAL : PRESETS_WHOLE).map((v) => (
            <button type="button" key={v} onClick={() => setQuantity(v)}>
              {v}
            </button>
          ))}
          {kind === 'waste' && product.stock > 0 && (
            <button type="button" onClick={() => setQuantity(String(product.stock))}>
              Tutto
            </button>
          )}
        </div>

        {kind === 'waste' && (
          <Field label="Motivo">
            <select value={reason ?? ''} onChange={(e) => setReasonId(Number(e.target.value))}>
              {reasons.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </Field>
        )}

        {kind === 'receipt' && (
          <>
            <Field label="Costo di acquisto (€)">
              <input value={cost} onChange={(e) => setCost(e.target.value)} inputMode="decimal" required />
            </Field>
            <Field label="Fornitore (facoltativo)">
              <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
                <option value="">—</option>
                {(suppliers.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Scadenza o consumo entro (facoltativa)">
              <input type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
            </Field>
          </>
        )}

        <button className="btn btn-primary btn-block" disabled={busy}>
          Conferma
        </button>
      </form>
    </Modal>
  )
}

function LotsTab() {
  const { data, loading, error, reload } = useLoad<Lot[]>(() => api.get('/stock/lots'))

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />
  if (data.length === 0) return <Empty>Nessun lotto a magazzino.</Empty>

  return (
    <div className="list">
      {data.map((l) => (
        <div key={l.id} className="card">
          <div className="row between">
            <div>
              <div className="item-title">{l.productName}</div>
              <div className="item-sub">
                Arrivo {dateIt(l.receivedAt)}
                {l.supplierName ? ` · ${l.supplierName}` : ''}
              </div>
            </div>
            {l.daysToExpiry !== null && (
              <Chip tone={l.daysToExpiry <= 1 ? 'red' : l.daysToExpiry <= 3 ? 'orange' : 'gray'}>
                {expiryLabel(l.daysToExpiry)}
              </Chip>
            )}
          </div>
          <div className="price-line" style={{ marginTop: 6 }}>
            <span>
              Rimasti <strong>{qty(l.remainingQuantity, l.unit)}</strong> di {qty(l.initialQuantity, l.unit)}
            </span>
            {l.unitCost !== null && <span className="muted">costo {eur(l.unitCost)}</span>}
          </div>
        </div>
      ))}
    </div>
  )
}

const MOVEMENT_LABEL = { RECEIPT: 'Carico', SALE: 'Vendita', WASTE: 'Scarto' } as const
const MOVEMENT_TONE = { RECEIPT: 'blue', SALE: 'green', WASTE: 'red' } as const

function MovementsTab() {
  const { items, loading, loadingMore, error, hasMore, loadMore, reload } = usePaged<Movement>(
    (page) => api.get<PageResponse<Movement>>(`/stock/movements?page=${page}&size=30`),
  )

  if (loading && items.length === 0) return <Loading />
  if (error && items.length === 0) return <ErrorBox message={error} onRetry={reload} />
  if (items.length === 0) return <Empty>Nessun movimento.</Empty>

  return (
    <div className="list">
      {items.map((m) => (
        <div key={m.id} className="card row between" style={m.voided ? { opacity: 0.55 } : undefined}>
          <div>
            <div className="item-title">{m.productName}</div>
            <div className="item-sub">
              {dateTimeIt(m.createdAt)}
              {m.createdBy ? ` · ${m.createdBy}` : ''}
              {m.reason ? ` · ${m.reason}` : ''}
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <Chip tone={m.voided ? 'gray' : MOVEMENT_TONE[m.type]}>
              {m.voided ? 'Stornato' : MOVEMENT_LABEL[m.type]}
            </Chip>
            <div className="item-sub">{qty(m.quantity, m.unit)}</div>
          </div>
        </div>
      ))}
      {hasMore && (
        <button className="btn" disabled={loadingMore} onClick={() => void loadMore()}>
          {loadingMore ? 'Carico…' : 'Carica altri'}
        </button>
      )}
    </div>
  )
}
