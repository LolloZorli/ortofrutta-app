import { lazy, Suspense, type ReactElement } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import Layout from './components/Layout'
import { Loading } from './components/ui'
import { LookupsProvider } from './lookups'
import ForcePasswordPage from './pages/ForcePasswordPage'
import LoginPage from './pages/LoginPage'

// Ogni pagina è un "pezzo" di codice scaricato solo quando serve: l'avvio dell'app è più leggero,
// e un commesso non scarica nemmeno il codice delle pagine di amministrazione.
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const CassaPage = lazy(() => import('./pages/CassaPage'))
const StockPage = lazy(() => import('./pages/StockPage'))
const SupplierOrdersPage = lazy(() => import('./pages/SupplierOrdersPage'))
const ReservationsPage = lazy(() => import('./pages/ReservationsPage'))
const ContactsPage = lazy(() => import('./pages/ContactsPage'))
const ProductsPage = lazy(() => import('./pages/ProductsPage'))
const ReportPage = lazy(() => import('./pages/ReportPage'))
const CorrispettiviPage = lazy(() => import('./pages/CorrispettiviPage'))
const AuditPage = lazy(() => import('./pages/AuditPage'))
const UsersPage = lazy(() => import('./pages/UsersPage'))
const RolesPage = lazy(() => import('./pages/RolesPage'))
const LookupsPage = lazy(() => import('./pages/LookupsPage'))
const MorePage = lazy(() => import('./pages/MorePage'))
const CustomerPortalPage = lazy(() => import('./pages/CustomerPortalPage'))

/** Pagina riservata a chi ha almeno uno di questi permessi (il server li impone comunque). */
function Guard({ perm, children }: { perm: string | string[]; children: ReactElement }) {
  const { canAny } = useAuth()
  const perms = Array.isArray(perm) ? perm : [perm]
  return canAny(...perms) ? children : <Navigate to="/" replace />
}

export default function App() {
  const { profile, ready } = useAuth()

  if (!ready) return <Loading />
  if (!profile) return <LoginPage />
  if (profile.mustChangePassword) return <ForcePasswordPage />

  return (
    <LookupsProvider>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={profile.roleCode === 'CUSTOMER' ? <Navigate to="/area-clienti" replace /> : <DashboardPage />} />
            <Route path="area-clienti" element={<Guard perm="CUSTOMER_ORDER_READ"><CustomerPortalPage /></Guard>} />
            <Route path="altro" element={<MorePage />} />

            <Route path="cassa" element={<Guard perm="SALES_CREATE"><CassaPage /></Guard>} />
            <Route path="magazzino" element={<Guard perm="STOCK_READ"><StockPage /></Guard>} />
            <Route path="ordini" element={<Guard perm="SUPPLIER_ORDERS_MANAGE"><SupplierOrdersPage /></Guard>} />
            <Route path="prenotazioni" element={<Guard perm="RESERVATIONS_MANAGE"><ReservationsPage /></Guard>} />
            <Route
              path="clienti"
              element={<Guard perm={['CUSTOMERS_MANAGE', 'RESERVATIONS_MANAGE']}><ContactsPage kind="customer" /></Guard>}
            />
            <Route
              path="fornitori"
              element={<Guard perm={['SUPPLIERS_READ', 'SUPPLIERS_MANAGE']}><ContactsPage kind="supplier" /></Guard>}
            />
            <Route path="prodotti" element={<Guard perm="PRODUCTS_WRITE"><ProductsPage /></Guard>} />
            <Route path="report" element={<Guard perm="REPORTS_VIEW"><ReportPage /></Guard>} />
            <Route path="corrispettivi" element={<Guard perm="REPORTS_VIEW"><CorrispettiviPage /></Guard>} />
            <Route path="registro" element={<Guard perm="AUDIT_VIEW"><AuditPage /></Guard>} />
            <Route path="utenti" element={<Guard perm="USERS_MANAGE"><UsersPage /></Guard>} />
            <Route path="ruoli" element={<Guard perm="ROLES_MANAGE"><RolesPage /></Guard>} />
            <Route path="tipologiche" element={<Guard perm="LOOKUPS_MANAGE"><LookupsPage /></Guard>} />

            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </LookupsProvider>
  )
}
