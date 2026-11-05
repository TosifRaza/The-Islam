import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiData } from '../../api/client'
import type { ReviewQueueRow } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Button, Card, EmptyState, ErrorState, Spinner, StatusPill } from '../../components/ui'

const TABS: { key: string; label: string }[] = [
  { key: 'all', label: 'All pending' },
  { key: 'editorial', label: 'Editorial review' },
  { key: 'scholarly', label: 'Scholarly review' },
  { key: 'ready_to_publish', label: 'Ready to publish' },
  { key: 'fatwas', label: 'Fatwas in review' },
  { key: 'rejected', label: 'Rejected' },
  { key: 'archived', label: 'Archived' },
]

/** Central review queue (Module F) — books and Fatwas awaiting decisions. */
export default function ReviewQueuePage() {
  const { hasPermission, hasAnyRole } = useAuth()
  const [params, setParams] = useSearchParams()
  const state = params.get('state') || 'all'
  const [rows, setRows] = useState<ReviewQueueRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await apiData<ReviewQueueRow[]>(`/admin/review-queue?state=${encodeURIComponent(state)}`))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [state])

  useEffect(() => {
    load()
  }, [load])

  const canEdit = hasPermission('content.edit')
  const isScholar = hasAnyRole('scholar_reviewer')

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Review queue</h1>
        <p className="text-sm text-gray-500">
          Draft → editorial review → scholarly review (where required) → approved → published. Every decision is
          recorded with reviewer identity and audited.
        </p>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setParams({ state: t.key }, { replace: true })}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${
              state === t.key ? 'bg-emerald-deep text-white' : 'bg-white text-gray-600 hover:bg-gray-100'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <Spinner label="Loading queue…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState title="Queue is empty" hint="Nothing is waiting in this view right now." />
      ) : (
        <Card className="p-0">
          <ul className="divide-y divide-gray-100">
            {rows.map((r, i) => (
              <li key={`${r.entity}-${r.entityId}-${i}`} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-gray-800">{r.title || '(untitled)'}</p>
                  <p className="text-xs text-gray-500">
                    {r.entity} · {r.state.replaceAll('_', ' ')}
                    {r.subject ? ` · ${r.subject}` : ''}
                    {typeof r.waitingDays === 'number' ? ` · waiting ${Math.max(0, r.waitingDays)} d` : ''}
                  </p>
                  {r.note && <p className="mt-0.5 max-w-xl text-xs italic text-gray-500">“{r.note}”</p>}
                </div>
                <div className="flex items-center gap-2">
                  <StatusPill status={r.state} />
                  {r.entity === 'books' && (canEdit || isScholar) && (
                    <Link to={`/books/${r.entityId}`}>
                      <Button variant="ghost">Open</Button>
                    </Link>
                  )}
                  {r.entity === 'fatwas' && (
                    <Link to={`/fatwas/${r.entityId}`}>
                      <Button variant="ghost">Open</Button>
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}
