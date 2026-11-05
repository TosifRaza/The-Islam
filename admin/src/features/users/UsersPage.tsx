import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../../api/client'
import type { PageMeta } from '../../api/client'
import type { UserSummary } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import {
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  Pagination,
  Select,
  Spinner,
  StatusPill,
  useToast,
} from '../../components/ui'

function maskOf(email: string): string {
  const at = email.indexOf('@')
  if (at <= 0) return '***'
  return email.charAt(0) + '***' + email.substring(at)
}

export default function UsersPage() {
  const { me, hasPermission } = useAuth()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<UserSummary[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [q, setQ] = useState(params.get('q') || '')
  const [status, setStatus] = useState(params.get('status') || '')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(Number(params.get('page') || 1))

  const [confirmSuspend, setConfirmSuspend] = useState<UserSummary | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<UserSummary | null>(null)
  const [busy, setBusy] = useState(false)

  const canSuspend = hasPermission('users.suspend')
  const canDelete = hasPermission('users.delete')
  const canManageRoles = hasPermission('roles.manage')
  const supportOnly = !!me?.roles.includes('support_manager') && !hasPermission('users.manage')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams()
      if (q.trim()) query.set('q', q.trim())
      if (status) query.set('status', status)
      query.set('page', String(page))
      query.set('size', '20')
      const res = await api<UserSummary[]>(`/admin/users?${query.toString()}`)
      setRows(res.data)
      setMeta(res.meta || null)
      const next = new URLSearchParams(params)
      next.set('page', String(page))
      if (q.trim()) next.set('q', q.trim())
      else next.delete('q')
      if (status) next.set('status', status)
      else next.delete('status')
      setParams(next, { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, status, page])

  useEffect(() => {
    load()
  }, [load])

  const doSuspend = async (user: UserSummary, reason: string) => {
    setBusy(true)
    try {
      await api(`/admin/users/${user.userId}/suspend`, { method: 'POST', body: { reason } })
      toast.push('success', `Suspended ${user.email}`)
      setConfirmSuspend(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Suspension failed')
    } finally {
      setBusy(false)
    }
  }

  const doDelete = async (user: UserSummary, reason: string) => {
    setBusy(true)
    try {
      await api(`/admin/users/${user.userId}/delete`, { method: 'POST', body: { reason } })
      toast.push('success', 'Deletion workflow started (audited)')
      setConfirmDelete(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Deletion request failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">User management</h1>
          <p className="text-sm text-gray-500">
            {supportOnly ? 'Support view — email addresses are masked (privacy)' : 'Search, review status and manage accounts'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Input
            placeholder="Search email…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setPage(1)
            }}
            className="w-56"
            aria-label="Search users by email"
          />
          <Select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value)
              setPage(1)
            }}
            aria-label="Filter by status"
          >
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
          </Select>
          <Button variant="secondary" onClick={load}>
            Apply
          </Button>
        </div>
      </div>

      {loading ? (
        <Spinner label="Loading users…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState title="No users match" hint="Adjust the search or status filter." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Registered</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((u) => (
                <tr key={u.userId} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <Link to={`/users/${u.userId}`} className="font-medium text-emerald-deep hover:underline">
                      {u.displayName}
                    </Link>
                    <p className="text-xs text-gray-500">{supportOnly ? maskOf(u.email) : u.email}</p>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{u.roles.join(', ')}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={u.status} />
                    {u.deletionState !== 'none' && (
                      <span className="ml-1">
                        <StatusPill status={u.deletionState} />
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {new Date(u.registeredAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <Link to={`/users/${u.userId}`}>
                        <Button variant="ghost">Details</Button>
                      </Link>
                      {canSuspend && u.status === 'active' && (
                        <Button variant="secondary" onClick={() => setConfirmSuspend(u)}>
                          Suspend
                        </Button>
                      )}
                      {canSuspend && u.status === 'suspended' && (
                        <Button
                          variant="secondary"
                          onClick={async () => {
                            try {
                              await api(`/admin/users/${u.userId}/unsuspend`, {
                                method: 'POST',
                                body: { reason: 'suspension lifted after review' },
                              })
                              toast.push('success', `Reactivated ${u.email}`)
                              await load()
                            } catch (e) {
                              toast.push('error', e instanceof Error ? e.message : 'Unsuspend failed')
                            }
                          }}
                        >
                          Unsuspend
                        </Button>
                      )}
                      {canDelete && u.deletionState === 'none' && (
                        <Button variant="danger" onClick={() => setConfirmDelete(u)}>
                          Delete…
                        </Button>
                      )}
                      {canManageRoles && (
                        <Link to={`/users/${u.userId}?edit=roles`}>
                          <Button variant="ghost">Roles</Button>
                        </Link>
                      )}
                    </div>
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

      <ConfirmDialog
        open={!!confirmSuspend}
        title="Suspend this account?"
        message={`${confirmSuspend?.email} will be signed out immediately and cannot log in until reactivated. This action is recorded in the audit log.`}
        confirmLabel="Suspend account"
        danger
        requireReason
        busy={busy}
        onCancel={() => setConfirmSuspend(null)}
        onConfirm={(reason) => confirmSuspend && doSuspend(confirmSuspend, reason || '')}
      />
      <ConfirmDialog
        open={!!confirmDelete}
        title="Start the deletion workflow?"
        message={`A deletion request will be recorded for ${confirmDelete?.email}. Super Admins schedule the purge (7-day grace); Admins file it for review. This action is recorded in the audit log.`}
        confirmLabel="Request deletion"
        danger
        requireReason
        busy={busy}
        onCancel={() => setConfirmDelete(null)}
        onConfirm={(reason) => confirmDelete && doDelete(confirmDelete, reason || '')}
      />
    </div>
  )
}
