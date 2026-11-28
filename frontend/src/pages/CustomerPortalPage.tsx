import { useEffect, useMemo, useState } from 'react'
import { api, companySession, errorMessage } from '../api'
import { useAuth } from '../auth'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { eur, qty, todayIso } from '../format'
import { useLoad } from '../hooks'
import { useToast } from '../toast'
import type { CatalogItem, CompanyShop, CustomerOrder, DeliveryOptions } from '../types'

export default function CustomerPortalPage() {
  const { profile } = useAuth()
  const toast = useToast()
  const shops = useLoad<CompanyShop[]>(() => api.get('/companies'))
  const [selectedShopSlug, setSelectedShopSlug] = useState(() => companySession.slug())
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [pickupDate, setPickupDate] = useState('')
  const [fulfillmentType, setFulfillmentType] = useState<'PICKUP' | 'DELIVERY'>('PICKUP')
  const [deliveryAddress, setDeliveryAddress] = useState('')
  const [deliveryCity, setDeliveryCity] = useState('')
  const [deliveryPostalCode, setDeliveryPostalCode] = useState('')
  const [deliveryPhone, setDeliveryPhone] = useState('')
  const [deliveryNotes, setDeliveryNotes] = useState('')
  const [deliverySlotId, setDeliverySlotId] = useState('')
  const [notes, setNotes] = useState('')
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [basketProducts, setBasketProducts] = useState<Record<number, CatalogItem>>({})
  const [busy, setBusy] = useState(false)
  const catalog = useLoad<CatalogItem[]>(
    () => selectedShopSlug
      ? api.get(`/catalog?q=${encodeURIComponent(debouncedSearch)}`)
      : Promise.resolve([]),
    [selectedShopSlug, debouncedSearch],
  )
  const orders = useLoad<CustomerOrder[]>(
    () => selectedShopSlug ? api.get('/customer-orders/mine') : Promise.resolve([]),
    [selectedShopSlug],
  )
  const deliveryOptions = useLoad<DeliveryOptions | null>(() =>
    selectedShopSlug && fulfillmentType === 'DELIVERY' && deliveryCity.trim() && pickupDate
      ? api.get(`/delivery-zones/options?city=${encodeURIComponent(deliveryCity)}&date=${encodeURIComponent(pickupDate)}`)
      : Promise.resolve(null),
  [selectedShopSlug, deliveryCity, pickupDate, fulfillmentType])
  const visible = useMemo(() => (catalog.data ?? []).filter((item) => item.availableQuantity > 0), [catalog.data])
  const selectedShop = shops.data?.find((shop) => shop.slug === selectedShopSlug)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [search])

  useEffect(() => {
    if (shops.data && selectedShopSlug && !shops.data.some((shop) => shop.slug === selectedShopSlug)) {
      companySession.select('')
      setSelectedShopSlug('')
    }
  }, [shops.data, selectedShopSlug])

  const productSubtotal = Object.entries(quantities).reduce((total, [id, quantity]) => {
    const item = basketProducts[Number(id)]
    const parsed = Number(quantity.replace(',', '.'))
    return total + (item && Number.isFinite(parsed) ? parsed * item.salePrice : 0)
  }, 0)

  function chooseShop(slug: string) {
    companySession.select(slug)
    setSelectedShopSlug(slug)
    setSearch('')
    setDebouncedSearch('')
    setQuantities({})
    setBasketProducts({})
    setPickupDate('')
    setDeliverySlotId('')
  }

  function changeQuantity(item: CatalogItem, delta: number) {
    const current = Number((quantities[item.id] ?? '0').replace(',', '.')) || 0
    const next = Math.max(0, Math.min(item.availableQuantity, current + delta))
    setBasketProducts((currentProducts) => ({ ...currentProducts, [item.id]: item }))
    setQuantities((currentQuantities) => ({
      ...currentQuantities,
      [item.id]: next === 0 ? '' : String(next),
    }))
  }

  function openBasket() {
    document.getElementById('customer-order-checkout')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function submitOrder() {
    const lines = Object.entries(quantities).map(([productId, quantity]) => ({
      productId: Number(productId), quantity: Number(quantity.replace(',', '.')),
    })).filter((line) => line.quantity > 0)
    if (lines.length === 0) return toast.error('Aggiungi almeno un prodotto')
    for (const line of lines) {
      const item = basketProducts[line.productId]
      if (!item || line.quantity > item.availableQuantity) return toast.error(`Disponibilità insufficiente per ${item?.name ?? 'un prodotto'}`)
    }
    if (!pickupDate || pickupDate < todayIso()) return toast.error('Scegli una data valida')
    if (fulfillmentType === 'DELIVERY' && (!deliveryAddress.trim() || !deliveryCity.trim() || !deliveryPostalCode.trim() || !deliveryPhone.trim())) {
      return toast.error('Per la consegna indica indirizzo, città, CAP e telefono')
    }
    if (fulfillmentType === 'DELIVERY') {
      if (deliveryOptions.data?.zoneAvailable && productSubtotal < deliveryOptions.data.minimumOrderSubtotal) {
        return toast.error(`L'importo minimo per la consegna è ${eur(deliveryOptions.data.minimumOrderSubtotal)}`)
      }
      if (deliveryOptions.data?.zoneAvailable && !deliveryOptions.data.slots.some((slot) => String(slot.id) === deliverySlotId)) {
        return toast.error('Scegli una fascia oraria disponibile')
      }
    }
    setBusy(true)
    try {
      await api.post('/customer-orders/mine', {
        pickupDate, notes: notes.trim() || null, lines, fulfillmentType,
        deliveryAddress: fulfillmentType === 'DELIVERY' ? deliveryAddress : null,
        deliveryCity: fulfillmentType === 'DELIVERY' ? deliveryCity.trim() : null,
        deliveryPostalCode: fulfillmentType === 'DELIVERY' ? deliveryPostalCode.trim() : null,
        deliveryPhone: fulfillmentType === 'DELIVERY' ? deliveryPhone.trim() : null,
        deliveryNotes: fulfillmentType === 'DELIVERY' ? deliveryNotes.trim() || null : null,
        deliverySlotId: fulfillmentType === 'DELIVERY' && deliverySlotId ? Number(deliverySlotId) : null,
      })
      setQuantities({}); setBasketProducts({}); setNotes(''); setPickupDate(''); setDeliveryNotes(''); setDeliverySlotId('')
      orders.reload()
      toast.ok('Ordine inviato al negozio')
    } catch (error) { toast.error(errorMessage(error)) } finally { setBusy(false) }
  }

  return <div className="stack">
    <div className="card stack">
      <div>
        <div className="item-title">Ciao{profile?.displayName || profile?.username ? `, ${profile.displayName || profile.username}` : ''}!</div>
        <p className="muted">Accedi con il tuo account e scegli da quale negozio ordinare.</p>
      </div>
      <Field label="Negozio">
        <select value={selectedShopSlug} onChange={(event) => chooseShop(event.target.value)}>
          <option value="">Scegli un negozio…</option>
          {(shops.data ?? []).map((shop) => <option key={shop.id} value={shop.slug}>{shop.name}</option>)}
        </select>
      </Field>
      {selectedShop && <div className="item-sub">Catalogo e ordini di <strong>{selectedShop.name}</strong>.</div>}
      {shops.error && <ErrorBox message={shops.error} onRetry={shops.reload} />}
    </div>
    {!selectedShopSlug ? <Empty>Seleziona un negozio per consultare il catalogo e inviare ordini.</Empty> : <>
      <div className="customer-search">
        <span aria-hidden="true">⌕</span>
        <input maxLength={100} placeholder="Cerca prodotti o categorie…" value={search} onChange={(event) => setSearch(event.target.value)} />
      </div>
      {catalog.error ? <ErrorBox message={catalog.error} onRetry={catalog.reload} />
        : catalog.loading ? <p className="muted" role="status">Cerco nel catalogo…</p>
          : visible.length === 0 ? <Empty>{search.trim() ? 'Nessun prodotto disponibile corrisponde alla ricerca.' : 'Al momento non ci sono prodotti disponibili in magazzino.'}</Empty>
            : <div className="product-grid">
              {visible.map((item) => <div className="card stack" key={item.id}>
                <div><div className="item-title">{item.name}</div><div className="item-sub">{item.category} · {eur(item.salePrice)}/{item.unit}</div>
                  <div className="item-sub">Disponibili: {qty(item.availableQuantity, item.unit)}</div></div>
                <div className="quantity-stepper" aria-label={`Quantità di ${item.name}`}>
                  <button type="button" aria-label={`Rimuovi ${item.name}`} onClick={() => changeQuantity(item, -1)} disabled={!quantities[item.id]}>−</button>
                  <input inputMode="decimal" min="0" max={item.availableQuantity} step="any" value={quantities[item.id] ?? ''}
                    onChange={(event) => {
                      setBasketProducts((current) => ({ ...current, [item.id]: item }))
                      setQuantities((current) => ({ ...current, [item.id]: event.target.value }))
                    }} />
                  <button type="button" aria-label={`Aggiungi ${item.name}`} onClick={() => changeQuantity(item, 1)}>＋</button>
                </div>
              </div>)}
            </div>}
      {Object.values(quantities).some((quantity) => Number(quantity.replace(',', '.')) > 0) && (
        <button type="button" className="customer-basket-bar" onClick={openBasket}>
          <span>Vedi il tuo ordine</span>
          <strong>{eur(productSubtotal)}</strong>
        </button>
      )}
      {Object.values(quantities).some((quantity) => Number(quantity.replace(',', '.')) > 0) && <section id="customer-order-checkout" className="card stack customer-checkout">
        <h3 className="section-title">Il tuo ordine · {selectedShop?.name}</h3>
        {Object.entries(quantities).filter(([, quantity]) => Number(quantity.replace(',', '.')) > 0).map(([id, quantity]) => {
          const item = basketProducts[Number(id)]
          return item ? <div className="row between" key={id}><span>{item.name} · {qty(Number(quantity.replace(',', '.')), item.unit)}</span><strong>{eur(Number(quantity.replace(',', '.')) * item.salePrice)}</strong></div> : null
        })}
        <Field label="Come vuoi ricevere l’ordine?"><select value={fulfillmentType} onChange={(event) => setFulfillmentType(event.target.value as 'PICKUP' | 'DELIVERY')}>
          <option value="PICKUP">Ritiro in negozio</option><option value="DELIVERY">Consegna a domicilio</option>
        </select></Field>
        <Field label={fulfillmentType === 'DELIVERY' ? 'Data di consegna' : 'Data di ritiro'}><input type="date" min={todayIso()} value={pickupDate} onChange={(event) => {
          setPickupDate(event.target.value)
          setDeliverySlotId('')
        }} /></Field>
        {fulfillmentType === 'DELIVERY' && <>
          <Field label="Indirizzo di consegna"><input autoComplete="street-address" maxLength={250} value={deliveryAddress} onChange={(event) => setDeliveryAddress(event.target.value)} required /></Field>
          <div className="row wrap">
            <Field label="Città"><input autoComplete="address-level2" maxLength={120} value={deliveryCity} onChange={(event) => {
              setDeliveryCity(event.target.value)
              setDeliverySlotId('')
            }} required /></Field>
            <Field label="CAP"><input autoComplete="postal-code" maxLength={20} value={deliveryPostalCode} onChange={(event) => setDeliveryPostalCode(event.target.value)} required /></Field>
          </div>
          <Field label="Telefono per la consegna"><input autoComplete="tel" inputMode="tel" maxLength={30} value={deliveryPhone} onChange={(event) => setDeliveryPhone(event.target.value)} required /></Field>
          <Field label="Istruzioni per il corriere (facoltative)"><textarea rows={2} maxLength={500} value={deliveryNotes} onChange={(event) => setDeliveryNotes(event.target.value)} /></Field>
          {deliveryOptions.data?.zoneAvailable ? <>
            <div className="item-sub">Consegna: {eur(deliveryOptions.data.deliveryFee)} · minimo prodotti: {eur(deliveryOptions.data.minimumOrderSubtotal)}</div>
            <Field label="Fascia oraria"><select value={deliverySlotId} onChange={(event) => setDeliverySlotId(event.target.value)} required>
              <option value="">Scegli una fascia…</option>
              {deliveryOptions.data.slots.map((slot) => <option key={slot.id} value={slot.id}>
                {slot.name} · {slot.remainingOrders} posti disponibili
              </option>)}
            </select></Field>
            {deliveryOptions.data.slots.length === 0 && <div className="item-sub">Nessuna fascia disponibile per questa data.</div>}
          </> : deliveryCity.trim() && pickupDate && !deliveryOptions.loading
            ? <div className="item-sub">Consegna disponibile · prezzo da confermare con il negozio</div>
            : null}
          <div className="price-line"><span>Totale ordine</span><strong>{eur(productSubtotal + (deliveryOptions.data?.zoneAvailable ? deliveryOptions.data.deliveryFee : 0))}</strong></div>
        </>}
        <Field label="Note per il negozio"><textarea rows={2} maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Orario preferito o richieste" /></Field>
        <button className="btn btn-primary btn-block" disabled={busy} onClick={submitOrder}>{busy ? 'Invio…' : 'Invia ordine'}</button>
      </section>}
      <section className="stack">
        <h3 className="section-title">I miei ordini · {selectedShop?.name}</h3>
        {orders.loading ? <Loading /> : orders.error ? <ErrorBox message={orders.error} onRetry={orders.reload} />
          : !orders.data?.length ? <Empty>Non hai ancora inviato ordini a questo negozio.</Empty>
            : orders.data.map((order) => <article className="card stack" key={order.id}>
              <div className="row between"><strong>{order.fulfillmentType === 'DELIVERY' ? 'Consegna' : 'Ritiro'} · {new Date(`${order.pickupDate}T12:00:00`).toLocaleDateString('it-IT')}</strong>
                <Chip tone={order.status === 'READY' || order.status === 'DELIVERED' || order.status === 'PICKED_UP' ? 'green' : order.status === 'CANCELLED' ? 'red' : 'orange'}>
                  {order.status === 'NEW' ? 'In attesa' : order.status === 'READY' ? 'In preparazione' : order.status === 'PICKED_UP' ? 'Ritirato' : order.status === 'DELIVERED' ? 'Consegnato' : 'Annullato'}
                </Chip></div>
              {order.fulfillmentType === 'DELIVERY' && <div className="item-sub">{order.deliveryAddress}, {order.deliveryPostalCode} {order.deliveryCity} · Tel. {order.deliveryPhone}{order.deliverySlot ? ` · ${order.deliverySlot}` : ''}</div>}
              {order.lines.map((line) => <div className="row between" key={line.id}><span>{line.productName} · {qty(line.quantity, line.unit)}</span><span>{eur(line.quantity * line.unitPrice)}</span></div>)}
              {order.fulfillmentType === 'DELIVERY' && <div className="row between"><span>Costo consegna</span><span>{eur(order.deliveryFee)}</span></div>}
              <div className="price-line"><span>Totale</span><strong>{eur(order.total)}</strong></div>
              {order.notes && <div className="item-sub">Nota: {order.notes}</div>}
            </article>)}
      </section>
    </>}
  </div>
}
