import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { eur } from '../format'
import { useLoad } from '../hooks'
import { useToast } from '../toast'
import type { DeliverySlot, DeliveryZone } from '../types'

interface DraftSlot extends Partial<DeliverySlot> {
  name: string
  maxOrders: number
  active: boolean
}

export default function DeliverySettingsPage() {
  const { data, loading, error, reload } = useLoad<DeliveryZone[]>(() => api.get('/delivery-zones'))
  const [editing, setEditing] = useState<DeliveryZone | null | undefined>(undefined)

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  return <div className="stack">
    <button className="btn btn-primary" onClick={() => setEditing(null)}>+ Aggiungi zona</button>
    {!data.length ? <Empty>Non hai ancora configurato zone di consegna.</Empty> : data.map((zone) =>
      <article className="card stack" key={zone.id}>
        <div className="row between">
          <div><div className="item-title">{zone.city}</div><div className="item-sub">
            Consegna {eur(zone.deliveryFee)} · minimo prodotti {eur(zone.minimumOrderSubtotal)}
          </div></div>
          <Chip tone={zone.active ? 'green' : 'gray'}>{zone.active ? 'Attiva' : 'Disattivata'}</Chip>
        </div>
        <div className="item-sub">{zone.slots.filter((slot) => slot.active).map((slot) =>
          `${slot.name} (max ${slot.maxOrders})`).join(' · ') || 'Nessuna fascia attiva'}</div>
        <button className="btn btn-sm" onClick={() => setEditing(zone)}>Modifica zona</button>
      </article>)}
    {editing !== undefined && <ZoneForm zone={editing} onClose={() => setEditing(undefined)} onSaved={() => {
      setEditing(undefined)
      reload()
    }} />}
  </div>
}

function ZoneForm({ zone, onClose, onSaved }: { zone: DeliveryZone | null; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const [city, setCity] = useState(zone?.city ?? '')
  const [fee, setFee] = useState(String(zone?.deliveryFee ?? 0))
  const [minimum, setMinimum] = useState(String(zone?.minimumOrderSubtotal ?? 0))
  const [active, setActive] = useState(zone?.active ?? true)
  const [slots, setSlots] = useState<DraftSlot[]>(zone?.slots.map((slot) => ({ ...slot })) ?? [
    { name: '', maxOrders: 10, active: true },
  ])
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const body = {
      city, deliveryFee: Number(fee.replace(',', '.')), minimumOrderSubtotal: Number(minimum.replace(',', '.')),
      active, slots: slots.filter((slot) => slot.name.trim()).map((slot) => ({
        id: slot.id, name: slot.name.trim(), maxOrders: Number(slot.maxOrders), active: slot.active,
      })),
    }
    if (!body.slots.length || body.slots.some((slot) => !Number.isInteger(slot.maxOrders) || slot.maxOrders < 1)) {
      toast.error('Aggiungi almeno una fascia con una capienza intera maggiore di zero')
      return
    }
    setBusy(true)
    try {
      if (zone) await api.put(`/delivery-zones/${zone.id}`, body)
      else await api.post('/delivery-zones', body)
      toast.ok('Zona di consegna salvata')
      onSaved()
    } catch (e) {
      toast.error(errorMessage(e))
      setBusy(false)
    }
  }

  function updateSlot(index: number, patch: Partial<DraftSlot>) {
    setSlots((current) => current.map((slot, i) => i === index ? { ...slot, ...patch } : slot))
  }

  return <Modal title={zone ? `Zona: ${zone.city}` : 'Nuova zona di consegna'} onClose={onClose}>
    <form className="stack" onSubmit={submit}>
      <Field label="Città"><input value={city} maxLength={120} required onChange={(event) => setCity(event.target.value)} /></Field>
      <div className="row wrap">
        <Field label="Costo consegna (€)"><input inputMode="decimal" value={fee} required onChange={(event) => setFee(event.target.value)} /></Field>
        <Field label="Minimo prodotti (€)"><input inputMode="decimal" value={minimum} required onChange={(event) => setMinimum(event.target.value)} /></Field>
      </div>
      <label className="row"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /> Zona attiva</label>
      <h3 className="section-title">Fasce orarie e capienza giornaliera</h3>
      {slots.map((slot, index) => <div className="card stack" key={slot.id ?? index}>
        <Field label="Fascia (es. 09:00–12:00)"><input value={slot.name} maxLength={80}
          onChange={(event) => updateSlot(index, { name: event.target.value })} /></Field>
        <Field label="Massimo ordini per data"><input type="number" min={1} step={1} value={slot.maxOrders}
          onChange={(event) => updateSlot(index, { maxOrders: Number(event.target.value) })} /></Field>
        <div className="row between">
          <label className="row"><input type="checkbox" checked={slot.active}
            onChange={(event) => updateSlot(index, { active: event.target.checked })} /> Fascia attiva</label>
          <button type="button" className="btn btn-sm btn-danger" onClick={() =>
            setSlots((current) => current.length > 1 ? current.filter((_, i) => i !== index) : current.map((s, i) => i === index ? { ...s, name: '' } : s))
          }>Rimuovi</button>
        </div>
      </div>)}
      <button type="button" className="btn btn-sm" onClick={() =>
        setSlots((current) => [...current, { name: '', maxOrders: 10, active: true }])}>+ Aggiungi fascia</button>
      <button className="btn btn-primary btn-block" disabled={busy}>Salva zona</button>
    </form>
  </Modal>
}
