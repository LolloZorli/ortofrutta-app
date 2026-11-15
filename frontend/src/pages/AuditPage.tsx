import { api } from '../api'
import { Empty, ErrorBox, Loading } from '../components/ui'
import { dateTimeIt } from '../format'
import { usePaged } from '../hooks'
import type { AuditEntry, PageResponse } from '../types'

const ACTION_LABEL: Record<string, string> = {
  PRICE_CHANGE: 'Prezzo modificato',
  PRODUCT_CREATE: 'Prodotto creato',
  PRODUCT_UPDATE: 'Prodotto modificato',
  PRODUCT_DEACTIVATE: 'Prodotto disattivato',
  SALE_VOID: 'Vendita stornata',
  ORDER_SEND: 'Ordine inviato',
  ORDER_RECEIVE: 'Merce ricevuta',
  USER_CREATE: 'Utente creato',
  USER_UPDATE: 'Utente modificato',
  USER_PASSWORD_RESET: 'Password reimpostata',
  ROLE_CREATE: 'Ruolo creato',
  ROLE_UPDATE: 'Ruolo modificato',
  ROLE_DELETE: 'Ruolo eliminato',
  LOOKUP_CREATE: 'Tipologica: valore aggiunto',
  LOOKUP_UPDATE: 'Tipologica: valore modificato',
}

export default function AuditPage() {
  const { items, loading, loadingMore, error, hasMore, loadMore, reload } = usePaged<AuditEntry>(
    (page) => api.get<PageResponse<AuditEntry>>(`/audit?page=${page}&size=30`),
  )

  if (loading && items.length === 0) return <Loading />
  if (error && items.length === 0) return <ErrorBox message={error} onRetry={reload} />
  if (items.length === 0) return <Empty>Nessuna attività registrata.</Empty>

  return (
    <div className="list">
      {items.map((e) => (
        <div key={e.id} className="card">
          <div className="row between">
            <div className="item-title">{ACTION_LABEL[e.action] ?? e.action}</div>
            <span className="item-sub">{dateTimeIt(e.createdAt)}</span>
          </div>
          {e.detail && <div className="item-sub">{e.detail}</div>}
          <div className="item-sub">di {e.username}</div>
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
