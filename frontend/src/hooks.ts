import { useEffect, useRef, useState } from 'react'
import { errorMessage } from './api'
import type { PageResponse } from './types'

/** Carica dati dal server e permette di ricaricarli. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fn()
      .then((d) => {
        if (!cancelled) {
          setData(d)
          setError(null)
        }
      })
      .catch((e) => {
        if (!cancelled) setError(errorMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  return { data, loading, error, reload: () => setTick((t) => t + 1) }
}

/** Elenco paginato: carica la prima pagina e le altre a richiesta ("Carica altri"). */
export function usePaged<T>(fetchPage: (page: number) => Promise<PageResponse<T>>, deps: unknown[] = []) {
  const [items, setItems] = useState<T[]>([])
  const [info, setInfo] = useState({ page: 0, totalPages: 0, total: 0 })
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)
  const generation = useRef(0)
  const loadingMoreRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    generation.current += 1
    const currentGeneration = generation.current
    loadingMoreRef.current = false
    setLoadingMore(false)
    setLoading(true)
    fetchPage(0)
      .then((r) => {
        if (cancelled) return
        setItems(r.content)
        setInfo({ page: r.page, totalPages: r.totalPages, total: r.totalElements })
        setError(null)
      })
      .catch((e) => {
        if (!cancelled) setError(errorMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
      if (generation.current === currentGeneration) generation.current += 1
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  async function loadMore() {
    if (loadingMoreRef.current) return
    loadingMoreRef.current = true
    const currentGeneration = generation.current
    setLoadingMore(true)
    try {
      const r = await fetchPage(info.page + 1)
      if (generation.current === currentGeneration) {
        setItems((prev) => [...prev, ...r.content])
        setInfo({ page: r.page, totalPages: r.totalPages, total: r.totalElements })
      }
    } catch (e) {
      if (generation.current === currentGeneration) setError(errorMessage(e))
    } finally {
      if (generation.current === currentGeneration) {
        loadingMoreRef.current = false
        setLoadingMore(false)
      }
    }
  }

  return {
    items,
    loading,
    loadingMore,
    error,
    total: info.total,
    hasMore: info.page + 1 < info.totalPages,
    loadMore,
    reload: () => setTick((t) => t + 1),
  }
}
