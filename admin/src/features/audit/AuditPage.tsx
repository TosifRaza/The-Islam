import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { Envelope, PageMeta } from '../../api/client'
import type { AuditRow } from '../../api/types'
import { Button, Card, EmptyState, ErrorState, Input, Pagination, Select, Spinner } from '../../components/ui'

/** Searchable administrative audit log (Module G). Read-only — history cannot be edited or erased. */
export default function AuditPage() {
  const [rows, setRows] = useState<AuditRow[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(1)
  const [action, setAction] = useState('')
  const [entity, setEntity] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams({ page: String(page), size: '20' })
      if (action.trim()) query.set('action', action.trim())
      if (entity.trim()) query.set('entity', entity.trim())
      if (from) query.set('from', new Date(from).toISOString())
      if (to) query.set('to', new Date(to).toISOString())
      const res: Envelope<AuditRow[]> = await api(`/admin/audit-logs?${query.toString()}`)
      setRows(res.data)
      setMeta(res.meta || null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [page, action, entity, from, to])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Audit log</h1>
        <p className="text-sm text-gray-500">
          Append-only record of administrative actions — logins, suspensions, role changes, content and workflow
          events. Ordinary administrators cannot edit or erase history.
        </p>
      </div>

      <Card className="flex flex-wrap items-end gap-2">
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="aa">
            Action contains
          </label>
          <Input id="aa" className="mt-1 w-48" value={action} onChange={(e) => { setAction(e.target.value); setPage(1) }} placeholder="e.g. workflow.publish" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="ae">
            Entity type
          </label>
          <Select id="ae" className="mt-1" value={entity} onChange={(e) => { setEntity(e.target.value); setPage(1) }}>
            <option value="">All</option>
            <option value="user">user</option>
            <option value="subject">subject</option>
            <option value="book">book</option>
            <option value="fatwa">fatwa</option>
            <option value="media">media</option>
            <option value="author">author</option>
          </Select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="af">
            From
          </label>
          <Input id="af" type="datetime-local" className="mt-1" value={from} onChange={(e) => { setFrom(e.target.value); setPage(1) }} />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="at">
            To
          </label>
          <Input id="at" type="datetime-local" className="mt-1" value={to} onChange={(e) => { setTo(e.target.value); setPage(1) }} />
        </div>
        <Button variant="secondary" onClick={load}>
          Apply
        </Button>
      </Card>

      {loading ? (
        <Spinner label="Loading audit trail…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState title="No audit entries match" hint="Adjust the filters — the trail itself is never empty after activity." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">When</th>
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Entity</th>
                <th className="px-4 py-3">Actor</th>
                <th className="px-4 py-3">IP</th>
                <th className="px-4 py-3">State hashes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50">
                  <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-600">{new Date(r.at).toLocaleString()}</td>
                  <td className="px-4 py-3 font-medium text-gray-800">{r.action}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {r.entityType}
                    {r.entityId ? ` #${r.entityId}` : ''}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{r.actorId ?? 'system'}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{r.ip || '—'}</td>
                  <td className="px-4 py-3 font-mono text-[10px] text-gray-400">
                    {r.beforeHash ? `before ${r.beforeHash.slice(0, 10)}…` : ''}{' '}
                    {r.afterHash ? `after ${r.afterHash.slice(0, 10)}…` : ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {meta && (
            <div className="px-4">
              <Pagination page={meta.page} totalPages={meta.totalPages} totalElements={meta.totalElements} onChange={setPage} />
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
