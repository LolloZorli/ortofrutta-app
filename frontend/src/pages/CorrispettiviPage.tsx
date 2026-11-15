import { useState } from 'react'
import { api } from '../api'
import { ErrorBox, Loading, Stat } from '../components/ui'
import { eur, num, todayIso } from '../format'
import { useLoad } from '../hooks'
import type { Corrispettivi } from '../types'

export default function CorrispettiviPage() {
  const [date, setDate] = useState(todayIso())
  const { data, loading, error, reload } = useLoad<Corrispettivi>(
    () => api.get(`/reports/corrispettivi?date=${date}`),
    [date],
  )

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

          <h3 className="section-title">Per aliquota IVA</h3>
          {data.byVat.length === 0 ? (
            <p className="muted center pad">Nessuna vendita in questo giorno.</p>
          ) : (
            <div className="card" style={{ overflowX: 'auto' }}>
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
