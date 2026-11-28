import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { api } from './api'
import { useLoad } from './hooks'
import type { LookupItem, Lookups } from './types'

const EMPTY: Lookups = {
  CATEGORY: [],
  UNIT: [],
  VAT_RATE: [],
  ORIGIN: [],
  WASTE_REASON: [],
  PAYMENT_METHOD: [],
  CUSTOMER_TYPE: [],
  SUPPLIER_TYPE: [],
  REGION: [],
  PROVINCE: [],
  CITY: [],
  STREET_TYPE: [],
}

interface LookupsState {
  lookups: Lookups
  ready: boolean
  reload: () => void
}

const LookupsContext = createContext<LookupsState | null>(null)

/** Le tipologiche (categorie, unità, IVA...) si scaricano una volta sola e servono a tutti i moduli. */
export function LookupsProvider({ children }: { children: ReactNode }) {
  const { data, reload } = useLoad<Lookups>(() => api.get('/lookups'))
  const value = useMemo<LookupsState>(() => ({ lookups: data ?? EMPTY, ready: data !== null, reload }), [data, reload])
  return <LookupsContext.Provider value={value}>{children}</LookupsContext.Provider>
}

export function useLookups(): LookupsState {
  const ctx = useContext(LookupsContext)
  if (!ctx) throw new Error('useLookups fuori da LookupsProvider')
  return ctx
}

/** Opzioni per una tendina: i valori attivi, più quello attualmente assegnato anche se poi disattivato. */
export function optionsWithCurrent(items: LookupItem[], currentId: number | null | undefined, currentName?: string | null) {
  const list = items.map((i) => ({ id: i.id, name: i.name }))
  if (currentId && !list.some((o) => o.id === currentId)) {
    list.push({ id: currentId, name: `${currentName ?? 'Valore'} (non più attivo)` })
  }
  return list
}
