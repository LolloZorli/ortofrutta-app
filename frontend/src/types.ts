// ---------- utenti, ruoli, permessi ----------
export interface Profile {
  username: string
  email: string | null
  roleCode: string
  roleName: string
  permissions: string[]
  mustChangePassword: boolean
}

export interface UserAccount {
  id: number
  username: string
  email: string | null
  roleId: number
  roleName: string
  enabled: boolean
  mustChangePassword: boolean
}

export interface CatalogItem { id: number; name: string; category: string; unit: string; salePrice: number }

export interface CustomerOrderLine { id: number; productId: number; productName: string; unit: string; quantity: number; unitPrice: number }
export interface CustomerOrder {
  id: number; customerId: number; customerName: string; customerPhone: string | null
  status: 'NEW' | 'READY' | 'PICKED_UP' | 'CANCELLED'; pickupDate: string; notes: string | null
  createdAt: string; lines: CustomerOrderLine[]; total: number
}

export interface RoleInfo {
  id: number
  code: string
  name: string
  description: string | null
  system: boolean
  permissions: string[]
  userCount: number
}

export interface PermissionInfo {
  code: string
  label: string
  group: string
  adminOnly: boolean
}

export interface RoleOption {
  id: number
  code: string
  name: string
}

// ---------- paginazione ----------
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ---------- tipologiche ----------
export type LookupTypeName =
  | 'CATEGORY'
  | 'UNIT'
  | 'VAT_RATE'
  | 'ORIGIN'
  | 'WASTE_REASON'
  | 'PAYMENT_METHOD'
  | 'CUSTOMER_TYPE'

export interface LookupItem {
  id: number
  code: string
  name: string
  sortOrder: number
  active: boolean
  shortLabel: string | null
  allowDecimals: boolean | null
  rate: number | null
}

export type Lookups = Record<LookupTypeName, LookupItem[]>

export interface LookupTypeInfo {
  type: LookupTypeName
  label: string
  hasUnitAttributes: boolean
  hasRate: boolean
}

// ---------- prodotti e magazzino ----------
/** "unit" è sempre l'etichetta breve dell'unità di misura (kg, pz...). */
export interface Product {
  id: number
  name: string
  barcode: string | null
  categoryId: number
  category: string
  unitId: number
  unit: string
  allowDecimals: boolean
  salePrice: number
  minStock: number
  originId: number | null
  origin: string | null
  vatRateId: number
  vatRate: number | null
  active: boolean
  stock: number
  lowStock: boolean
}

export interface Supplier {
  id: number
  name: string
  phone: string | null
  email: string | null
  notes: string | null
}

export interface Customer {
  id: number
  name: string
  phone: string | null
  notes: string | null
  typeId: number | null
  type: string | null
}

/** Costo e fornitore sono null per chi non ha il permesso di vederli. */
export interface Lot {
  id: number
  productId: number
  productName: string
  unit: string
  supplierId: number | null
  supplierName: string | null
  receivedAt: string
  expiresAt: string | null
  initialQuantity: number
  remainingQuantity: number
  unitCost: number | null
  daysToExpiry: number | null
}

export type MovementType = 'RECEIPT' | 'SALE' | 'WASTE'

export interface Movement {
  id: number
  productId: number
  productName: string
  unit: string
  type: MovementType
  quantity: number
  unitPrice: number | null
  unitCost: number | null
  reason: string | null
  createdBy: string | null
  voided: boolean
  createdAt: string
}

// ---------- ordini fornitori ----------
export type SupplierOrderStatus = 'DRAFT' | 'SENT' | 'RECEIVED' | 'CANCELLED'

export interface SupplierOrderLine {
  id: number
  productId: number
  productName: string
  unit: string
  quantity: number
  unitCost: number | null
}

export interface SupplierOrder {
  id: number
  supplierId: number
  supplierName: string
  supplierPhone: string | null
  status: SupplierOrderStatus
  createdAt: string
  sentAt: string | null
  receivedAt: string | null
  notes: string | null
  lines: SupplierOrderLine[]
  total: number | null
}

// ---------- prenotazioni ----------
export type ReservationStatus = 'NEW' | 'READY' | 'PICKED_UP' | 'CANCELLED'

export interface ReservationLine {
  id: number
  productId: number
  productName: string
  unit: string
  quantity: number
  unitPrice: number
}

export interface Reservation {
  id: number
  customerId: number
  customerName: string
  customerPhone: string | null
  status: ReservationStatus
  pickupDate: string
  notes: string | null
  createdAt: string
  lines: ReservationLine[]
  total: number
}

// ---------- cassa ----------
export interface SaleLine {
  productName: string
  unit: string
  quantity: number
  unitPrice: number
  total: number
}

export interface Sale {
  id: number
  createdAt: string
  createdBy: string | null
  paymentMethod: string | null
  total: number
  status: 'COMPLETED' | 'VOIDED'
  voidedAt: string | null
  voidedBy: string | null
  voidReason: string | null
  lines: SaleLine[]
    customerId: number | null
    customerName: string | null
}

// ---------- report ----------
export interface LowStockItem {
  productId: number
  productName: string
  unit: string
  stock: number
  minStock: number
  suggestedQuantity: number
  lastUnitCost: number | null
    averageDailySales: number
    daysOfStock: number | null
}

export interface ExpiringLot {
  lotId: number
  productId: number
  productName: string
  unit: string
  remainingQuantity: number
  expiresAt: string
  daysToExpiry: number
}

export interface TopProduct {
  productId: number
  productName: string
  unit: string
  quantity: number
  revenue: number
}

/** Gli importi sono null per chi non ha il permesso di vederli. */
export interface Dashboard {
  todayRevenue: number | null
  stockValue: number | null
  revenue30: number | null
  margin30: number | null
  waste30Cost: number | null
  openReservations: number
  openSupplierOrders: number
  lowStock: LowStockItem[]
  expiring: ExpiringLot[]
  topProducts: TopProduct[]
}

export interface ProductReport {
  productId: number
  productName: string
  unit: string
  soldQuantity: number
  revenue: number
  cost: number
  margin: number
  marginPercent: number
  wasteQuantity: number
  wasteCost: number
  wastePercent: number
}

export interface VatRow {
  rate: number
  gross: number
  net: number
  vat: number
}

export interface PaymentRow {
  method: string
  gross: number
  sales: number
}

export interface Corrispettivi {
  date: string
  totalGross: number
  totalNet: number
  totalVat: number
  salesCount: number
  voidedCount: number
  voidedTotal: number
  byVat: VatRow[]
  byPayment: PaymentRow[]
}

export interface AuditEntry {
  id: number
  createdAt: string
  username: string
  action: string
  entityType: string
  entityId: string | null
  detail: string | null
}
