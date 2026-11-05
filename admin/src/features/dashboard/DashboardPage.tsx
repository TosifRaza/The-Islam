import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiData } from '../../api/client'
import type { DashboardMetrics, QueueItem } from '../../api/types'
import { Button, Card, EmptyState, ErrorState, Spinner, StatusPill } from '../../components/ui'
import { useAuth } from '../../auth/AuthContext'

function KpiCard({ label, value, hint, to }: { label: string; value: number | string; hint?: string; to?: string }) {
  const body = (
    <Card className={to ? 'transition hover:border-emerald-deep/40' : ''}>
      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-emerald-deep">{value}</p>
      {hint && <p className="mt-1 text-xs text-gray-400">{hint}</p>}
    </Card>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

function fmtWhen(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export default function DashboardPage() {
  const { hasPermission, hasAnyRole } = useAuth()
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setMetrics(await apiData<DashboardMetrics>('/admin/dashboard'))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading) return <Spinner label="Loading dashboard metrics…" />
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!metrics) return null

  const canReview = hasAnyRole('super_admin', 'admin', 'content_manager', 'scholar_reviewer')
  const queue: QueueItem[] = metrics.review.awaitingApproval || []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Overview</h1>
          <p className="text-sm text-gray-500">Live metrics from the platform database</p>
        </div>
        <div className="flex gap-2">
          {hasPermission('content.edit') && (
            <Link to="/books/new">
              <Button variant="gold">+ New book</Button>
            </Link>
          )}
          {hasPermission('files.upload') && (
            <Link to="/files">
              <Button variant="secondary">Upload file</Button>
            </Link>
          )}
        </div>
      </div>

      {/* users */}
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-gray-400">Users</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <KpiCard label="Total registered" value={metrics.users.total} to="/users" />
          <KpiCard label="Active" value={metrics.users.active} to="/users?status=active" />
          <KpiCard label="New (30 days)" value={metrics.users.newRegistrations} />
        </div>
      </section>

      {/* content */}
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-gray-400">Content</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard label="Subjects" value={metrics.content.subjects} to="/subjects" />
          <KpiCard label="Books total" value={metrics.content.books} to="/books" />
          <KpiCard label="Published books" value={metrics.content.publishedBooks} />
          <KpiCard label="Draft books" value={metrics.content.draftBooks} />
          <KpiCard label="Files uploaded" value={metrics.content.uploads} to="/files" />
          <KpiCard label="Courses" value={metrics.content.courses} hint="approved later phase" />
          <KpiCard label="Research articles" value={metrics.content.researchArticles} hint="approved later phase" />
          <KpiCard
            label="Quran / Hadith import"
            value="Gated"
            hint="OD-05 / G-3 — no content imported"
          />
        </div>
      </section>

      {/* review queues */}
      <section>
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-gray-400">Review queues</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <KpiCard
            label="Books — editorial review"
            value={metrics.review.pendingBookEditorial}
            to="/review-queue?state=editorial"
          />
          <KpiCard
            label="Books — scholarly review"
            value={metrics.review.pendingBookScholarly}
            to="/review-queue?state=scholarly"
          />
          <KpiCard label="Fatwas in review" value={metrics.review.pendingFatwaReview} to="/fatwas?state=in_review" />
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* approval queue */}
        <Card className="p-0">
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3">
            <h2 className="font-medium text-gray-900">Pending approval queue</h2>
            {canReview && (
              <Link to="/review-queue" className="text-xs text-emerald-deep hover:underline">
                Open queue →
              </Link>
            )}
          </div>
          {queue.length === 0 ? (
            <div className="p-5">
              <EmptyState title="Nothing awaiting approval" hint="Submitted books and Fatwas will appear here." />
            </div>
          ) : (
            <ul className="divide-y divide-gray-100">
              {queue.map((item) => (
                <li key={`${item.entity}-${item.entityId}`} className="flex items-center justify-between gap-3 px-5 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-gray-800">{item.title || '(untitled)'}</p>
                    <p className="text-xs text-gray-500">
                      {item.entity} · waiting {Math.max(0, Math.round(item.waitingDays))} d
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusPill status={item.state} />
                    {item.entity === 'books' && hasPermission('content.edit') && (
                      <Link to={`/books/${item.entityId}`} className="text-xs text-emerald-deep hover:underline">
                        Review
                      </Link>
                    )}
                    {item.entity === 'fatwas' && (
                      <Link to={`/fatwas/${item.entityId}`} className="text-xs text-emerald-deep hover:underline">
                        Open
                      </Link>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* recent activity */}
        <Card className="p-0">
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3">
            <h2 className="font-medium text-gray-900">Recent administrative activity</h2>
            {hasPermission('audit.view') && (
              <Link to="/audit-logs" className="text-xs text-emerald-deep hover:underline">
                Full audit log →
              </Link>
            )}
          </div>
          {(metrics.recentAdminActivity || []).length === 0 ? (
            <div className="p-5">
              <EmptyState title="No activity recorded yet" />
            </div>
          ) : (
            <ul className="divide-y divide-gray-100">
              {metrics.recentAdminActivity.map((row, i) => (
                <li key={i} className="px-5 py-2.5 text-sm">
                  <span className="font-medium text-gray-800">{row.action}</span>
                  <span className="text-gray-500"> on {row.entity}</span>
                  <span className="block text-xs text-gray-400">
                    {row.actor || 'system'} · {fmtWhen(row.at)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
