import { useMemo, useState } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { dateTimeIt, eur, parseDecimal, qty } from '../format'
import { useLoad, usePaged } from '../hooks'
import { useLookups } from '../lookups'
import { useToast } from '../toast'
import type { PageResponse, Product, Sale } from '../types'

interface CartLine {
  productId: number
  quantity: string
  price: string
}

type Tab = 'cassa' | 'vendite'

export default function CassaPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<Tab>('cassa')
  return (
    <div>
      <div className="tabs">
        <button className={tab === 'cassa' ? 'active' : ''} onClick={() => setTab('cassa')}>
          Cassa
        </button>
        <button className={tab === 'vendite' ? 'active' : ''} onClick={() => setTab('vendite')}>
          {can('SALES_VOID') || can('REPORTS_VIEW') ? 'Vendite' : 'Le mie vendite'}
        </button>
      </div>
      {tab === 'cassa' ? <Register /> : <SalesList />}
    </div>
  )
}

function Register() {
  const toast = useToast()
  const { can } = useAuth()
  const { lookups } = useLookups()
  const canOverride = can('SALES_PRICE_OVERRIDE')
  const products = useLoad<Product[]>(() => api.get('/products'))
  const [search, setSearch] = useState('')
  const [cart, setCart] = useState<CartLine[]>([])
  const [paymentId, setPaymentId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)

  const list = products.data ?? []
  const byId = useMemo(() => new Map(list.map((p) => [p.id, p])), [list])
  const methods = lookups.PAYMENT_METHOD
  const payment = paymentId ?? methods[0]?.id ?? null

  if (products.loading && !products.data) return <Loading />
  if (products.error || !products.data) return <ErrorBox message={products.error ?? 'Nessun dato'} onRetry={products.reload} />

  const q = search.trim().toLowerCase()
  const visible = list.filter((p) => !q || p.name.toLowerCase().includes(q))

  function add(p: Product) {
    setCart((c) => {
      const existing = c.find((l) => l.productId === p.id)
      if (existing) {
        const next = (parseDecimal(existing.quantity) || 0) + 1
        return c.map((l) => (l.productId === p.id ? { ...l, quantity: String(next) } : l))
      }
      return [...c, { productId: p.id, quantity: '1', price: String(p.salePrice) }]
    })
  }

  function setLine(productId: number, patch: Partial<CartLine>) {
    setCart((c) => c.map((l) => (l.productId === productId ? { ...l, ...patch } : l)))
  }

  const lineTotal = (l: CartLine) => {
    const p = byId.get(l.productId)
    const quantity = parseDecimal(l.quantity)
    const price = canOverride ? parseDecimal(l.price) : (p?.salePrice ?? 0)
    return Number.isFinite(quantity) && Number.isFinite(price) ? quantity * price : 0
  }
  const total = cart.reduce((sum, l) => sum + lineTotal(l), 0)

  async function pay() {
    if (cart.length === 0) return
    if (payment === null) return toast.error('Scegli il metodo di pagamento')
    const items = []
    for (const l of cart) {
      const p = byId.get(l.productId)
      const quantity = parseDecimal(l.quantity)
      if (!p || !(quantity > 0)) return toast.error(`Quantità non valida per ${p?.name ?? 'un prodotto'}`)
      if (!p.allowDecimals && !Number.isInteger(quantity)) {
        return toast.error(`${p.name}: la quantità deve essere un numero intero`)
      }
      const price = parseDecimal(l.price)
      items.push({
        productId: p.id,
        quantity,
        unitPrice: canOverride && Number.isFinite(price) && price !== p.salePrice ? price : undefined,
      })
    }
    setBusy(true)
    try {
      const sale = await api.post<Sale>('/sales', { paymentMethodId: payment, items })
      toast.ok(`Incassati ${eur(sale.total)}`)
      setCart([])
      products.reload()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack">
      {cart.length > 0 && (
        <div className="card stack">
          <div className="row between">
            <strong>Carrello</strong>
            <button className="btn btn-sm" onClick={() => setCart([])}>
              Svuota
            </button>
          </div>
          {cart.map((l) => {
            const p = byId.get(l.productId)
            if (!p) return null
            return (
              <div key={l.productId} className="cart-line">
                <div className="grow">
                  <div className="item-title">{p.name}</div>
                  <div className="item-sub">
                    {canOverride ? '' : `${eur(p.salePrice)}/${p.unit} · `}
                    {eur(lineTotal(l))}
                  </div>
                </div>
                <input
                  className="qty-input"
                  inputMode="decimal"
                  value={l.quantity}
                  onChange={(e) => setLine(l.productId, { quantity: e.target.value })}
                  aria-label={`Quantità ${p.name}`}
                />
                <span className="muted">{p.unit}</span>
                {canOverride && (
                  <input
                    className="qty-input"
                    inputMode="decimal"
                    value={l.price}
                    onChange={(e) => setLine(l.productId, { price: e.target.value })}
                    aria-label={`Prezzo ${p.name}`}
                  />
                )}
                <button
                  className="icon-btn"
                  aria-label="Rimuovi"
                  onClick={() => setCart((c) => c.filter((x) => x.productId !== l.productId))}
                >
                  ✕
                </button>
              </div>
            )
          })}

          <div className="chips-row">
            {methods.map((m) => (
              <button
                key={m.id}
                type="button"
                className={payment === m.id ? 'chip-on' : ''}
                onClick={() => setPaymentId(m.id)}
              >
                {m.name}
              </button>
            ))}
          </div>
          <button className="btn btn-primary btn-block" disabled={busy} onClick={pay}>
            Incassa {eur(total)}
          </button>
        </div>
      )}

      <input placeholder="Cerca prodotto…" value={search} onChange={(e) => setSearch(e.target.value)} />
      {visible.length === 0 ? (
        <Empty>Nessun prodotto.</Empty>
      ) : (
        <div className="product-grid">
          {visible.map((p) => {
            const inCart = cart.find((l) => l.productId === p.id)
            return (
              <button
                key={p.id}
                className={`product-tile${inCart ? ' in-cart' : ''}`}
                onClick={() => add(p)}
                disabled={p.stock <= 0}
              >
                <span className="item-title">{p.name}</span>
                <span className="item-sub">
                  {eur(p.salePrice)}/{p.unit}
                </span>
                <Chip tone={p.stock <= 0 ? 'red' : p.lowStock ? 'orange' : 'green'}>{qty(p.stock, p.unit)}</Chip>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

function SalesList() {
  const toast = useToast()
  const { can } = useAuth()
  const { items, loading, loadingMore, error, hasMore, loadMore, reload } = usePaged<Sale>(
    (page) => api.get<PageResponse<Sale>>(`/sales?page=${page}&size=20`),
  )
  const [open, setOpen] = useState<Sale | null>(null)
  const [voiding, setVoiding] = useState<Sale | null>(null)

  async function show(s: Sale) {
    try {
      setOpen(await api.get<Sale>(`/sales/${s.id}`))
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  if (loading && items.length === 0) return <Loading />
  if (error && items.length === 0) return <ErrorBox message={error} onRetry={reload} />
  if (items.length === 0) return <Empty>Nessuna vendita.</Empty>

  return (
    <div className="list">
      {items.map((s) => (
        <button key={s.id} className="card sale-card" onClick={() => show(s)}>
          <div className="row between">
            <div>
              <div className="item-title">{eur(s.total)}</div>
              <div className="item-sub">
                {dateTimeIt(s.createdAt)} · {s.createdBy ?? '—'} · {s.paymentMethod ?? 'n/d'}
              </div>
            </div>
            {s.status === 'VOIDED' ? <Chip tone="red">stornata</Chip> : <Chip tone="green">ok</Chip>}
          </div>
        </button>
      ))}
      {hasMore && (
        <button className="btn" disabled={loadingMore} onClick={() => void loadMore()}>
          {loadingMore ? 'Carico…' : 'Carica altre'}
        </button>
      )}

      {open && (
        <Modal title={`Vendita ${dateTimeIt(open.createdAt)}`} onClose={() => setOpen(null)}>
          <ul className="lines-summary">
            {open.lines.map((l, i) => (
              <li key={i}>
                {l.productName}: {qty(l.quantity, l.unit)} × {eur(l.unitPrice)} = {eur(l.total)}
              </li>
            ))}
          </ul>
          <div className="price-line">
            <span className="muted">Totale · {open.paymentMethod ?? 'n/d'}</span>
            <strong>{eur(open.total)}</strong>
          </div>
          {open.status === 'VOIDED' && (
            <p className="tone-bad">
              Stornata da {open.voidedBy}: {open.voidReason}
            </p>
          )}
          {open.status === 'COMPLETED' && can('SALES_VOID') && (
            <button
              className="btn btn-danger"
              onClick={() => {
                setVoiding(open)
                setOpen(null)
              }}
            >
              Storna vendita
            </button>
          )}
        </Modal>
      )}

      {voiding && (
        <VoidForm
          sale={voiding}
          onClose={() => setVoiding(null)}
          onDone={() => {
            setVoiding(null)
            reload()
          }}
        />
      )}
    </div>
  )
}

function VoidForm({ sale, onClose, onDone }: { sale: Sale; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit() {
    if (!reason.trim()) return toast.error('Scrivi il motivo dello storno')
    setBusy(true)
    try {
      await api.post(`/sales/${sale.id}/void`, { reason: reason.trim() })
      toast.ok('Vendita stornata: la merce è tornata a magazzino')
      onDone()
    } catch (e) {
      toast.error(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <Modal title={`Storna vendita da ${eur(sale.total)}`} onClose={onClose}>
      <p className="muted">La merce torna nei lotti e la vendita esce da incassi e corrispettivi. Resta traccia nel registro.</p>
      <Field label="Motivo">
        <input value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
      </Field>
      <button className="btn btn-danger btn-block" disabled={busy} onClick={submit}>
        Conferma storno
      </button>
    </Modal>
  )
}
