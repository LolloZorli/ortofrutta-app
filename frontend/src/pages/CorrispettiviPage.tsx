import { useState } from 'react'
import { api, errorMessage } from '../api'
import { ErrorBox, Loading, Stat } from '../components/ui'
import { eur, num, todayIso } from '../format'
import { useLoad } from '../hooks'
import type { Corrispettivi } from '../types'
import { useToast } from '../toast'

interface CashClosing { expectedCash: number; countedCash: number; difference: number; note: string | null; closedBy: string | null; closedAt: string }

export default function CorrispettiviPage() {
  const [date, setDate] = useState(todayIso())
  const toast = useToast()
  const [counted, setCounted] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const { data, loading, error, reload } = useLoad<Corrispettivi>(
    () => api.get(`/reports/corrispettivi?date=${date}`),
    [date],
  )
  const closing = useLoad<CashClosing | null>(() => api.get<CashClosing | null>(`/reports/cash-closing?date=${date}`), [date])

  async function saveClosing() {
    const amount = Number(counted.replace(',', '.'))
    if (!Number.isFinite(amount) || amount < 0) return toast.error('Inserisci il contante contato')
    setSaving(true)
    try {
      const saved = await api.post<CashClosing>(`/reports/cash-closing?date=${date}`, { countedCash: amount, note })
      setCounted(String(saved.countedCash))
      closing.reload()
      toast.ok(`Chiusura salvata · differenza ${eur(saved.difference)}`)
    } catch (e) { toast.error(errorMessage(e)) } finally { setSaving(false) }
  }

  return (
    <div className="stack">
      <label className="field">
        <span>Giorno</span>
        <input type="date" value={date} max={todayIso()} onChange={(e) => e.target.value && setDate(e.target.value)} />
      </label>

      {loading && !data ? (
        <Loading />
      ) : error || !data ? (
        <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />
      ) : (
        <>
          <div className="stats">
            <Stat label="Incassato (IVA inclusa)" value={eur(data.totalGross)} />
            <Stat label="Imponibile" value={eur(data.totalNet)} />
            <Stat label="IVA" value={eur(data.totalVat)} />
            <Stat label="Vendite" value={String(data.salesCount)} />
          </div>

          <div className="card stack">
            <div><strong>Chiusura contanti</strong><div className="item-sub">Atteso da vendite registrate: {eur(data.byPayment.find((p) => p.method.toLowerCase().includes('contant'))?.gross ?? 0)}</div></div>
            <label className="field"><span>Contante contato (€)</span><input inputMode="decimal" value={counted || (closing.data ? String(closing.data.countedCash) : '')} onChange={(e) => setCounted(e.target.value)} placeholder="0,00" /></label>
            <label className="field"><span>Nota (facoltativa)</span><input value={note || closing.data?.note || ''} onChange={(e) => setNote(e.target.value)} maxLength={500} /></label>
            {closing.data && <p className="muted">Ultima chiusura: {eur(closing.data.countedCash)} · differenza {eur(closing.data.difference)} · {closing.data.closedBy ?? 'utente'}</p>}
            <button className="btn btn-primary" disabled={saving} onClick={saveClosing}>{saving ? 'Salvataggio…' : 'Salva chiusura'}</button>
          </div>
          <h3 className="section-title">Per aliquota IVA</h3>
          {data.byVat.length === 0 ? (
            <p className="muted center pad">Nessuna vendita in questo giorno.</p>
          ) : (
            <div className="card table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Aliquota</th>
                    <th>Imponibile</th>
                    <th>IVA</th>
                    <th>Totale</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byVat.map((r) => (
                    <tr key={r.rate}>
                      <td>{num(r.rate)}%</td>
                      <td>{eur(r.net)}</td>
                      <td>{eur(r.vat)}</td>
                      <td>{eur(r.gross)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <h3 className="section-title">Per metodo di pagamento</h3>
          {data.byPayment.length === 0 ? (
            <p className="muted center pad">—</p>
          ) : (
            <div className="list">
              {data.byPayment.map((p) => (
                <div key={p.method} className="card row between">
                  <div>
                    <div className="item-title">{p.method}</div>
                    <div className="item-sub">{p.sales} vendite</div>
                  </div>
                  <strong>{eur(p.gross)}</strong>
                </div>
              ))}
            </div>
          )}

          {data.voidedCount > 0 && (
            <p className="muted">
              Vendite stornate nel giorno: {data.voidedCount} per {eur(data.voidedTotal)} (non contate sopra).
            </p>
          )}

          <div className="card">
            <p className="muted" style={{ margin: 0 }}>
              Prospetto interno per controllo di cassa. <strong>Non sostituisce</strong> la memorizzazione e la trasmissione
              dei corrispettivi con il registratore telematico. Le aliquote sono quelle assegnate ai prodotti: falle verificare dal
              commercialista.
            </p>
          </div>
        </>
      )}
    </div>
  )
}
