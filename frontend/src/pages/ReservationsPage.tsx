import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import ProductSelect from '../components/ProductSelect'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { dateIt, eur, parseDecimal, qty, tomorrowIso } from '../format'
import { useLoad } from '../hooks'
import { optionsWithCurrent, useLookups } from '../lookups'
import { useToast } from '../toast'
import type { Customer, DeliveryOptions, Product, Reservation, ReservationStatus } from '../types'

const STATUS: Record<ReservationStatus, { label: string; tone: 'blue' | 'green' | 'gray' | 'red' }> = {
  NEW: { label: 'Nuova', tone: 'blue' },
  READY: { label: 'Pronta', tone: 'green' },
  PICKED_UP: { label: 'Ritirata', tone: 'gray' },
  DELIVERED: { label: 'Consegnata', tone: 'green' },
  CANCELLED: { label: 'Annullata', tone: 'red' },
}

export default function ReservationsPage() {
  const [onlyOpen, setOnlyOpen] = useState(true)
  const [creating, setCreating] = useState(false)
  const { data, loading, error, reload } = useLoad<Reservation[]>(
    () => api.get(`/customer-orders?onlyOpen=${onlyOpen}`),
    [onlyOpen],
  )

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  return (
    <div className="stack">
      <button className="btn btn-primary" onClick={() => setCreating(true)}>
        + Nuova prenotazione
      </button>
      <div className="tabs">
        <button className={onlyOpen ? 'active' : ''} onClick={() => setOnlyOpen(true)}>
          Da preparare
        </button>
        <button className={!onlyOpen ? 'active' : ''} onClick={() => setOnlyOpen(false)}>
          Tutte
        </button>
      </div>

      {data.length === 0 ? (
        <Empty>{onlyOpen ? 'Nessuna prenotazione aperta.' : 'Nessuna prenotazione.'}</Empty>
      ) : (
        <div className="list">
          {data.map((r) => (
            <ReservationCard key={r.id} reservation={r} onChanged={reload} />
          ))}
        </div>
      )}

      {creating && (
        <NewReservationModal
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            reload()
          }}
        />
      )}
    </div>
  )
}

function ReservationCard({ reservation: r, onChanged }: { reservation: Reservation; onChanged: () => void }) {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const [picking, setPicking] = useState(false)
  const st = STATUS[r.status]
  const open = r.status === 'NEW' || r.status === 'READY'
  const contactPhone = r.fulfillmentType === 'DELIVERY' ? r.deliveryPhone : r.customerPhone

  async function act(action: 'ready' | 'pickup' | 'deliver' | 'cancel', okMessage: string, body?: unknown) {
    setBusy(true)
    try {
      await api.post(`/customer-orders/${r.id}/${action}`, body)
      toast.ok(okMessage)
      onChanged()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <div className="row between">
        <div>
          <div className="item-title">{r.customerName}</div>
          <div className="item-sub">{r.fulfillmentType === 'DELIVERY' ? 'Consegna' : 'Ritiro'}: {dateIt(r.pickupDate)}</div>
        </div>
        <Chip tone={st.tone}>{st.label}</Chip>
      </div>
      {r.fulfillmentType === 'DELIVERY' && <div className="item-sub" style={{ marginTop: 8 }}>
        <strong>Indirizzo:</strong> {r.deliveryAddress}, {r.deliveryPostalCode} {r.deliveryCity}{r.deliverySlot && <> · <strong>Fascia:</strong> {r.deliverySlot}</>}<br />
        <strong>Telefono:</strong> {r.deliveryPhone}{r.deliveryNotes && <><br /><strong>Istruzioni:</strong> {r.deliveryNotes}</>}
      </div>}
      <ul className="lines-summary">
        {r.lines.map((l) => (
          <li key={l.id}>
            {l.productName}: {qty(l.quantity, l.unit)}
          </li>
        ))}
      </ul>
      {r.notes && <div className="item-sub">Note: {r.notes}</div>}
      <div className="price-line" style={{ marginTop: 6 }}>
        <span className="muted">Totale</span>
        <strong>{eur(r.total)}</strong>
      </div>
      {r.fulfillmentType === 'DELIVERY' && <div className="item-sub">Prodotti {eur(r.productSubtotal)} · Consegna {eur(r.deliveryFee)}</div>}
      {open && (
        <div className="row wrap" style={{ marginTop: 10 }}>
          {r.status === 'NEW' && (
            <button className="btn btn-sm" disabled={busy} onClick={() => act('ready', 'Segnata come pronta')}>
              Pronta
            </button>
          )}
          <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => setPicking(true)}>
            {r.fulfillmentType === 'DELIVERY' ? 'Segna consegnata' : 'Ritirata'}
          </button>
          <button className="btn btn-sm btn-danger" disabled={busy} onClick={() => act('cancel', 'Prenotazione annullata')}>
            Annulla
          </button>
          {contactPhone && (
            <a className="btn btn-sm" href={`tel:${contactPhone.replace(/\s/g, '')}`}>
              Chiama
            </a>
          )}
        </div>
      )}
      {picking && (
        <PickupForm
          reservation={r}
          onClose={() => setPicking(false)}
          onPay={(paymentMethodId) => {
            setPicking(false)
            void act(r.fulfillmentType === 'DELIVERY' ? 'deliver' : 'pickup',
              r.fulfillmentType === 'DELIVERY' ? 'Consegna registrata e incassata: magazzino aggiornato' : 'Ritirata e incassata: magazzino aggiornato',
              { paymentMethodId })
          }}
        />
      )}
    </div>
  )
}

/** Il completamento dell'ordine diventa una vendita di cassa: serve il metodo di pagamento. */
function PickupForm({
  reservation,
  onClose,
  onPay,
}: {
  reservation: Reservation
  onClose: () => void
  onPay: (paymentMethodId: number) => void
}) {
  const { lookups } = useLookups()
  const [selected, setSelected] = useState<number | null>(null)
  const methods = lookups.PAYMENT_METHOD
  const method = selected ?? methods[0]?.id ?? null

  return (
    <Modal title={`${reservation.fulfillmentType === 'DELIVERY' ? 'Consegna' : 'Ritiro'} di ${reservation.customerName}`} onClose={onClose}>
      <div className="price-line">
        <span className="muted">Da incassare</span>
        <strong>{eur(reservation.total)}</strong>
      </div>
      <div className="chips-row">
        {methods.map((m) => (
          <button key={m.id} type="button" className={method === m.id ? 'chip-on' : ''} onClick={() => setSelected(m.id)}>
            {m.name}
          </button>
        ))}
      </div>
      <button className="btn btn-primary btn-block" disabled={method === null} onClick={() => method !== null && onPay(method)}>
        {reservation.fulfillmentType === 'DELIVERY' ? 'Conferma consegna e incassa' : 'Incassa e consegna'}
      </button>
    </Modal>
  )
}

interface DraftLine {
  productId: string
  quantity: string
}

function NewReservationModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const toast = useToast()
  const { lookups } = useLookups()
  const customers = useLoad<Customer[]>(() => api.get('/customers'))
  const products = useLoad<Product[]>(() => api.get('/products'))
  const [customerId, setCustomerId] = useState('')
  const [newCustomer, setNewCustomer] = useState(false)
  const [newName, setNewName] = useState('')
  const [newPhone, setNewPhone] = useState('')
  const [pickupDate, setPickupDate] = useState(tomorrowIso())
  const [fulfillmentType, setFulfillmentType] = useState<'PICKUP' | 'DELIVERY'>('PICKUP')
  const [streetType, setStreetType] = useState('')
  const [streetName, setStreetName] = useState('')
  const [deliveryProvince, setDeliveryProvince] = useState('')
  const [deliveryCity, setDeliveryCity] = useState('')
  const [deliveryPostalCode, setDeliveryPostalCode] = useState('')
  const [deliveryPhone, setDeliveryPhone] = useState('')
  const [deliveryNotes, setDeliveryNotes] = useState('')
  const [deliverySlotId, setDeliverySlotId] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([{ productId: '', quantity: '' }])
  const [busy, setBusy] = useState(false)
  const deliveryOptions = useLoad<DeliveryOptions>(() => api.get(
    `/delivery-zones/options?city=${encodeURIComponent(deliveryCity)}&date=${encodeURIComponent(pickupDate)}`,
  ), [deliveryCity, pickupDate, fulfillmentType])
  const productSubtotal = lines.reduce((total, line) => {
    const product = products.data?.find((item) => String(item.id) === line.productId)
    const quantity = parseDecimal(line.quantity)
    return total + (product && Number.isFinite(quantity) ? quantity * product.salePrice : 0)
  }, 0)

  function setLine(i: number, patch: Partial<DraftLine>) {
    setLines((ls) => ls.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!newCustomer && !customerId) return toast.error('Scegli il cliente')
    if (newCustomer && !newName.trim()) return toast.error('Scrivi il nome del cliente')

    const body = []
    for (const l of lines) {
      if (!l.productId && !l.quantity) continue
      const q = parseDecimal(l.quantity)
      if (!l.productId || !(q > 0)) return toast.error('Ogni riga ha bisogno di prodotto e quantità')
      body.push({ productId: Number(l.productId), quantity: q })
    }
    if (body.length === 0) return toast.error('Aggiungi almeno un prodotto')
    const address = `${streetType} ${streetName}`.trim()
    if (fulfillmentType === 'DELIVERY' && (!streetType || !streetName.trim() || !deliveryProvince || !deliveryCity.trim() || !deliveryPostalCode.trim() || !deliveryPhone.trim())) {
      return toast.error('Per la consegna indica via, provincia, città, CAP e telefono')
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
      let id = Number(customerId)
      if (newCustomer) {
        const created = await api.post<Customer>('/customers', { name: newName.trim(), phone: newPhone.trim() || null })
        id = created.id
      }
      await api.post('/customer-orders', {
        customerId: id, pickupDate, notes: notes.trim() || null, lines: body, fulfillmentType,
        deliveryAddress: fulfillmentType === 'DELIVERY' ? address : null,
        deliveryCity: fulfillmentType === 'DELIVERY' ? deliveryCity.trim() : null,
        deliveryPostalCode: fulfillmentType === 'DELIVERY' ? deliveryPostalCode.trim() : null,
        deliveryPhone: fulfillmentType === 'DELIVERY' ? deliveryPhone.trim() : null,
        deliveryNotes: fulfillmentType === 'DELIVERY' ? deliveryNotes.trim() || null : null,
        deliverySlotId: fulfillmentType === 'DELIVERY' && deliverySlotId ? Number(deliverySlotId) : null,
      })
      toast.ok('Prenotazione creata')
      onCreated()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title="Nuova prenotazione" onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        {newCustomer ? (
          <>
            <Field label="Nome cliente">
              <input value={newName} onChange={(e) => setNewName(e.target.value)} autoFocus />
            </Field>
            <Field label="Telefono (facoltativo)">
              <input value={newPhone} onChange={(e) => setNewPhone(e.target.value)} inputMode="tel" />
            </Field>
            <button type="button" className="btn btn-sm" onClick={() => setNewCustomer(false)}>
              Scegli un cliente esistente
            </button>
          </>
        ) : (
          <>
            <Field label="Cliente">
              <select value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
                <option value="">Scegli…</option>
                {(customers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </Field>
            <button type="button" className="btn btn-sm" onClick={() => setNewCustomer(true)}>
              + Nuovo cliente
            </button>
          </>
        )}

        <Field label="Modalità di evasione"><select value={fulfillmentType} onChange={(e) => setFulfillmentType(e.target.value as 'PICKUP' | 'DELIVERY')}>
          <option value="PICKUP">Ritiro in negozio</option><option value="DELIVERY">Consegna a domicilio</option>
        </select></Field>
        <Field label={fulfillmentType === 'DELIVERY' ? 'Data di consegna' : 'Data di ritiro'}>
          <input type="date" value={pickupDate} onChange={(e) => {
            setPickupDate(e.target.value)
            setDeliverySlotId('')
          }} required />
        </Field>
        {fulfillmentType === 'DELIVERY' && <>
          <div className="row wrap">
            <Field label="Tipo di via"><select value={streetType} onChange={(e) => setStreetType(e.target.value)} required>
              <option value="">Seleziona…</option>
              {optionsWithCurrent(lookups.STREET_TYPE, null).map((item) => <option key={item.id} value={item.name}>{item.name}</option>)}
            </select></Field>
            <Field label="Nome via / piazza"><input maxLength={230} value={streetName} onChange={(e) => setStreetName(e.target.value)} required /></Field>
          </div>
          <Field label="Regione"><input value="Lazio" readOnly /></Field>
          <div className="row wrap">
            <Field label="Provincia"><select value={deliveryProvince} onChange={(e) => {
              setDeliveryProvince(e.target.value)
              setDeliveryCity('')
              setDeliverySlotId('')
            }} required>
              <option value="">Seleziona provincia…</option>
              {optionsWithCurrent(lookups.PROVINCE, null).map((item) => <option key={item.id} value={item.name}>{item.name}</option>)}
            </select></Field>
            <Field label="Città"><select value={deliveryCity} onChange={(e) => {
              setDeliveryCity(e.target.value)
              setDeliverySlotId('')
            }} required>
              <option value="">Seleziona città…</option>
              {optionsWithCurrent(lookups.CITY, null).map((item) => <option key={item.id} value={item.name}>{item.name}</option>)}
            </select></Field>
            <Field label="CAP"><input maxLength={20} value={deliveryPostalCode} onChange={(e) => setDeliveryPostalCode(e.target.value)} required /></Field>
          </div>
          <Field label="Telefono per la consegna"><input maxLength={30} value={deliveryPhone} onChange={(e) => setDeliveryPhone(e.target.value)} required /></Field>
          <Field label="Istruzioni per il corriere"><textarea rows={2} maxLength={500} value={deliveryNotes} onChange={(e) => setDeliveryNotes(e.target.value)} /></Field>
          {deliveryOptions.data?.zoneAvailable ? <>
            <div className="item-sub">Consegna: {eur(deliveryOptions.data.deliveryFee)} · minimo prodotti: {eur(deliveryOptions.data.minimumOrderSubtotal)}</div>
            <Field label="Fascia oraria"><select value={deliverySlotId} onChange={(e) => setDeliverySlotId(e.target.value)} required>
              <option value="">Scegli una fascia…</option>
              {deliveryOptions.data.slots.map((slot) => <option key={slot.id} value={slot.id}>
                {slot.name} · {slot.remainingOrders} posti disponibili
              </option>)}
            </select></Field>
            {deliveryOptions.data.slots.length === 0 && <div className="item-sub">Nessuna fascia disponibile per questa data.</div>}
          </> : deliveryCity.trim() && pickupDate
            ? <div className="item-sub">Consegna disponibile · prezzo da confermare con il negozio</div>
            : null}
          <div className="price-line"><span>Totale ordine</span><strong>{eur(productSubtotal + (deliveryOptions.data?.zoneAvailable ? deliveryOptions.data.deliveryFee : 0))}</strong></div>
        </>}

        <div className="stack">
          {lines.map((l, i) => (
            <div key={i} className="line-row">
              <ProductSelect products={products.data ?? []} value={l.productId} onChange={(v) => setLine(i, { productId: v })} />
              <input
                placeholder="Qtà"
                inputMode="decimal"
                value={l.quantity}
                onChange={(e) => setLine(i, { quantity: e.target.value })}
              />
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
          <button type="button" className="btn btn-sm" onClick={() => setLines((ls) => [...ls, { productId: '', quantity: '' }])}>
            + Aggiungi prodotto
          </button>
        </div>

        <Field label="Note (facoltative)">
          <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Crea prenotazione
        </button>
      </form>
    </Modal>
  )
}
