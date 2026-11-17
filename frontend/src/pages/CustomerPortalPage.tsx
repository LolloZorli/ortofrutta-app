import { useMemo, useState } from 'react'
import { api, errorMessage } from '../api'
import { useAuth } from '../auth'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { eur, qty, todayIso } from '../format'
import { useLoad } from '../hooks'
import { useToast } from '../toast'
import type { CatalogItem, CustomerOrder } from '../types'

export default function CustomerPortalPage() {
  const { profile } = useAuth()
  const toast = useToast()
  const catalog = useLoad<CatalogItem[]>(() => api.get('/catalog'))
  const orders = useLoad<CustomerOrder[]>(() => api.get('/customer-orders/mine'))
  const [search, setSearch] = useState('')
  const [pickupDate, setPickupDate] = useState('')
  const [notes, setNotes] = useState('')
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState(false)
  const visible = useMemo(() => (catalog.data ?? []).filter((item) =>
    `${item.name} ${item.category}`.toLowerCase().includes(search.trim().toLowerCase())), [catalog.data, search])

  async function submitOrder() {
    const lines = Object.entries(quantities).filter(([, quantity]) => Number(quantity) > 0)
      .map(([productId, quantity]) => ({ productId: Number(productId), quantity: Number(quantity.replace(',', '.')) }))
    if (lines.length === 0) return toast.error('Aggiungi almeno un prodotto')
    if (!pickupDate || pickupDate < todayIso()) return toast.error('Scegli una data di ritiro valida')
    setBusy(true)
    try {
      await api.post('/customer-orders/mine', { pickupDate, notes: notes.trim() || null, lines })
      setQuantities({}); setNotes(''); setPickupDate('')
      orders.reload()
      toast.ok('Ordine inviato al negozio')
    } catch (error) { toast.error(errorMessage(error)) } finally { setBusy(false) }
  }

  if (catalog.loading && !catalog.data) return <Loading />
  if (catalog.error || !catalog.data) return <ErrorBox message={catalog.error ?? 'Catalogo non disponibile'} onRetry={catalog.reload} />

  return <div className="stack">
    <div className="card">
      <div className="item-title">Ciao{profile?.username ? `, ${profile.username}` : ''}!</div>
      <p className="muted">Scegli i prodotti e invia l’ordine. Il negozio ti confermerà la disponibilità e l’orario di ritiro.</p>
    </div>
    <input placeholder="Cerca nel catalogo…" value={search} onChange={(event) => setSearch(event.target.value)} />
    {visible.length === 0 ? <Empty>Nessun prodotto trovato.</Empty> : <div className="product-grid">
      {visible.map((item) => <div className="card stack" key={item.id}>
        <div><div className="item-title">{item.name}</div><div className="item-sub">{item.category} · {eur(item.salePrice)}/{item.unit}</div></div>
        <label className="field"><span>Quantità ({item.unit})</span><input inputMode="decimal" min="0" step="any" value={quantities[item.id] ?? ''}
          onChange={(event) => setQuantities((current) => ({ ...current, [item.id]: event.target.value }))} /></label>
      </div>)}
    </div>}
    {Object.values(quantities).some((quantity) => Number(quantity.replace(',', '.')) > 0) && <section className="card stack">
      <h3 className="section-title">Il tuo ordine</h3>
      {Object.entries(quantities).filter(([, quantity]) => Number(quantity.replace(',', '.')) > 0).map(([id, quantity]) => {
        const item = catalog.data?.find((product) => product.id === Number(id))
        return item ? <div className="row between" key={id}><span>{item.name} · {qty(Number(quantity.replace(',', '.')), item.unit)}</span><strong>{eur(Number(quantity.replace(',', '.')) * item.salePrice)}</strong></div> : null
      })}
      <Field label="Data di ritiro"><input type="date" min={todayIso()} value={pickupDate} onChange={(event) => setPickupDate(event.target.value)} /></Field>
      <Field label="Note per il negozio"><textarea rows={2} maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Orario preferito o richieste" /></Field>
      <button className="btn btn-primary btn-block" disabled={busy} onClick={submitOrder}>{busy ? 'Invio…' : 'Invia ordine'}</button>
    </section>}
    <section className="stack">
      <h3 className="section-title">I miei ordini</h3>
      {orders.loading && !orders.data ? <Loading /> : orders.error ? <ErrorBox message={orders.error} onRetry={orders.reload} />
        : !orders.data?.length ? <Empty>Non hai ancora inviato ordini.</Empty>
          : orders.data.map((order) => <article className="card stack" key={order.id}>
            <div className="row between"><strong>Ritiro {new Date(`${order.pickupDate}T12:00:00`).toLocaleDateString('it-IT')}</strong>
              <Chip tone={order.status === 'READY' ? 'green' : order.status === 'CANCELLED' ? 'red' : 'orange'}>
                {order.status === 'NEW' ? 'In attesa' : order.status === 'READY' ? 'Pronto' : order.status === 'PICKED_UP' ? 'Ritirato' : 'Annullato'}
              </Chip></div>
            {order.lines.map((line) => <div className="row between" key={line.id}><span>{line.productName} · {qty(line.quantity, line.unit)}</span><span>{eur(line.quantity * line.unitPrice)}</span></div>)}
            <div className="price-line"><span>Totale indicativo</span><strong>{eur(order.total)}</strong></div>
            {order.notes && <div className="item-sub">Nota: {order.notes}</div>}
          </article>)}
    </section>
  </div>
}
