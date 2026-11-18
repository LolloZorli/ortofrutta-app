import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import ChangePasswordForm from '../components/ChangePasswordForm'
import InstallApp from '../components/InstallApp'
import Modal from '../components/Modal'
import { Chip } from '../components/ui'

interface Item {
  to: string
  label: string
  /** Compare solo a chi ha almeno uno di questi permessi. */
  perms: string[]
}

const ITEMS: Item[] = [
  { to: '/prodotti', label: '🥕 Prodotti e prezzi di listino', perms: ['PRODUCTS_WRITE'] },
  { to: '/fornitori', label: '🚚 Fornitori', perms: ['SUPPLIERS_READ', 'SUPPLIERS_MANAGE'] },
  { to: '/clienti', label: '👥 Clienti', perms: ['CUSTOMERS_MANAGE', 'RESERVATIONS_MANAGE'] },
  { to: '/corrispettivi', label: '🧾 Corrispettivi del giorno', perms: ['REPORTS_VIEW'] },
  { to: '/report', label: '📊 Report: vendite, margini, scarti', perms: ['REPORTS_VIEW'] },
  { to: '/registro', label: '📜 Registro attività', perms: ['AUDIT_VIEW'] },
  { to: '/tipologiche', label: '🗂️ Tipologiche (categorie, unità, IVA...)', perms: ['LOOKUPS_MANAGE'] },
  { to: '/utenti', label: '👥 Commessi e richieste di accesso', perms: ['USERS_READ', 'USERS_MANAGE'] },
  { to: '/ruoli', label: '🛡️ Ruoli e permessi', perms: ['ROLES_MANAGE'] },
]

export default function MorePage() {
  const { profile, canAny, logout } = useAuth()
  const [changing, setChanging] = useState(false)
  const visible = ITEMS.filter((i) => canAny(...i.perms))

  return (
    <div className="stack">
      {visible.length > 0 && (
        <div className="card link-list">
          {visible.map((i) => (
            <Link key={i.to} to={i.to}>
              <span>{i.label}</span>
              <span className="muted">›</span>
            </Link>
          ))}
        </div>
      )}

      <InstallApp />

      <div className="card stack">
        <div className="row between">
          <span className="muted">
            Connesso come <strong>{profile?.username}</strong>
          </span>
          {profile && <Chip tone="green">{profile.roleName}</Chip>}
        </div>
        <div className="row">
          <button className="btn btn-sm grow" onClick={() => setChanging(true)}>
            Cambia password
          </button>
          <button className="btn btn-sm grow" onClick={logout}>
            Esci
          </button>
        </div>
      </div>

      {changing && (
        <Modal title="Cambia password" onClose={() => setChanging(false)}>
          <ChangePasswordForm onDone={() => setChanging(false)} />
        </Modal>
      )}
    </div>
  )
}
