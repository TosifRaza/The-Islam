import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, apiData } from '../../api/client'
import type { ActivityEntry, UserDetail } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import {
  Button,
  Card,
  ConfirmDialog,
  ErrorState,
  Spinner,
  StatusPill,
  useToast,
} from '../../components/ui'

const ALL_ROLES = [
  'super_admin',
  'admin',
  'content_manager',
  'quran_editor',
  'hadith_editor',
  'research_editor',
  'scholar_reviewer',
  'academy_manager',
  'support_manager',
]

export default function UserDetailPage() {
  const { userId } = useParams()
  const { hasPermission } = useAuth()
  const toast = useToast()
  const [user, setUser] = useState<UserDetail | null>(null)
  const [activity, setActivity] = useState<ActivityEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const [roleDraft, setRoleDraft] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [confirmSuspend, setConfirmSuspend] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const canSuspend = hasPermission('users.suspend')
  const canDelete = hasPermission('users.delete')
  const canManageRoles = hasPermission('roles.manage')

  const load = useCallback(async () => {
    setError(null)
    try {
      const detail = await apiData<UserDetail>(`/admin/users/${userId}`)
      setUser(detail)
      setRoleDraft(detail.roles)
      try {
        const acts = await apiData<ActivityEntry[]>(`/admin/users/${userId}/activity?size=10`)
        setActivity(acts || [])
      } catch {
        setActivity([])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [userId])

  useEffect(() => {
    load()
  }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!user) return <Spinner label="Loading user…" />

  const applyRoles = async () => {
    setBusy(true)
    try {
      const add = roleDraft.filter((r) => !user.roles.includes(r))
      const remove = user.roles.filter((r) => !roleDraft.includes(r))
      await api(`/admin/users/${user.userId}`, { method: 'PATCH', body: { add, remove } })
      toast.push('success', 'Roles updated (audited)')
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Role change failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/users" className="text-xs text-emerald-deep hover:underline">
            ← User management
          </Link>
          <h1 className="text-xl font-semibold text-gray-900">{user.displayName}</h1>
          <p className="text-sm text-gray-500">{user.email}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusPill status={user.status} />
          <StatusPill status={user.deletionState} />
          {canSuspend && user.status === 'active' && (
            <Button variant="secondary" onClick={() => setConfirmSuspend(true)}>
              Suspend
            </Button>
          )}
          {canSuspend && user.status === 'suspended' && (
            <Button
              variant="secondary"
              onClick={async () => {
                try {
                  await api(`/admin/users/${user.userId}/unsuspend`, {
                    method: 'POST',
                    body: { reason: 'suspension lifted after review (user detail page)' },
                  })
                  toast.push('success', 'Account reactivated')
                  await load()
                } catch (e) {
                  toast.push('error', e instanceof Error ? e.message : 'Unsuspend failed')
                }
              }}
            >
              Unsuspend
            </Button>
          )}
          {canDelete && user.deletionState === 'none' && (
            <Button variant="danger" onClick={() => setConfirmDelete(true)}>
              Deletion workflow…
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-3 font-medium text-gray-900">Account</h2>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <dt className="text-gray-500">User ID</dt>
            <dd className="font-mono text-xs text-gray-800">{user.userId}</dd>
            <dt className="text-gray-500">Status</dt>
            <dd><StatusPill status={user.status} /></dd>
            <dt className="text-gray-500">Registered</dt>
            <dd>{new Date(user.registeredAt).toLocaleString()}</dd>
            <dt className="text-gray-500">Last seen</dt>
            <dd>{user.lastSeenAt ? new Date(user.lastSeenAt).toLocaleString() : '—'}</dd>
            <dt className="text-gray-500">Deletion state</dt>
            <dd>{user.deletionState}</dd>
          </dl>
          {user.deletionRequests.length > 0 && (
            <div className="mt-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm">
              <p className="font-medium text-amber-900">Deletion requests</p>
              <ul className="mt-1 space-y-1 text-xs text-amber-800">
                {user.deletionRequests.map((d) => (
                  <li key={d.requestId}>
                    <StatusPill status={d.state} /> · {d.reason || 'no reason recorded'} ·{' '}
                    {d.purgeAt ? `purge at ${new Date(d.purgeAt).toLocaleString()}` : 'awaiting schedule'}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>

        <Card>
          <h2 className="mb-3 font-medium text-gray-900">Assigned roles</h2>
          <div className="mb-3 flex flex-wrap gap-1">
            {user.roles.map((r) => (
              <span key={r} className="rounded-full bg-emerald-deep/10 px-2 py-0.5 text-xs text-emerald-deep">
                {r}
              </span>
            ))}
          </div>
          {canManageRoles ? (
            <>
              <p className="mb-2 text-xs text-gray-500">
                Role assignment is Super-Admin-only and every change is audited. The last active Super Admin cannot be demoted.
              </p>
              <div className="max-h-48 space-y-1 overflow-y-auto rounded border border-gray-200 p-2">
                {ALL_ROLES.map((r) => (
                  <label key={r} className="flex items-center gap-2 text-sm text-gray-700">
                    <input
                      type="checkbox"
                      checked={roleDraft.includes(r)}
                      onChange={(e) =>
                        setRoleDraft((draft) => (e.target.checked ? [...draft, r] : draft.filter((x) => x !== r)))
                      }
                    />
                    {r}
                  </label>
                ))}
              </div>
              <div className="mt-3 flex gap-2">
                <Button onClick={applyRoles} disabled={busy}>
                  Save roles
                </Button>
                <Button variant="ghost" onClick={() => setRoleDraft(user.roles)} disabled={busy}>
                  Reset
                </Button>
              </div>
            </>
          ) : (
            <p className="text-xs text-gray-500">You do not hold roles.manage — role changes are restricted to Super Admins.</p>
          )}
        </Card>
      </div>

      <Card className="p-0">
        <h2 className="border-b border-gray-100 px-5 py-3 font-medium text-gray-900">Recent activity</h2>
        {activity.length === 0 ? (
          <p className="px-5 py-4 text-sm text-gray-500">No recorded activity for this account yet.</p>
        ) : (
          <ul className="divide-y divide-gray-100 text-sm">
            {activity.map((a, i) => (
              <li key={i} className="flex items-center justify-between px-5 py-2.5">
                <span className="font-medium text-gray-800">{a.action}</span>
                <span className="text-xs text-gray-400">
                  {a.entityType} · {new Date(a.at).toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <ConfirmDialog
        open={confirmSuspend}
        title="Suspend this account?"
        message="The user is signed out immediately and cannot log in until reactivated. Audited."
        danger
        requireReason
        busy={busy}
        onCancel={() => setConfirmSuspend(false)}
        onConfirm={async (reason) => {
          setBusy(true)
          try {
            await api(`/admin/users/${user.userId}/suspend`, { method: 'POST', body: { reason } })
            toast.push('success', 'Account suspended (audited)')
            setConfirmSuspend(false)
            await load()
          } catch (e) {
            toast.push('error', e instanceof Error ? e.message : 'Suspension failed')
          } finally {
            setBusy(false)
          }
        }}
      />
      <ConfirmDialog
        open={confirmDelete}
        title="Start the deletion workflow?"
        message="Super Admins schedule the purge (7-day grace); Admins file it for review. Audited."
        danger
        requireReason
        busy={busy}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={async (reason) => {
          setBusy(true)
          try {
            await api(`/admin/users/${user.userId}/delete`, { method: 'POST', body: { reason } })
            toast.push('success', 'Deletion workflow started (audited)')
            setConfirmDelete(false)
            await load()
          } catch (e) {
            toast.push('error', e instanceof Error ? e.message : 'Deletion request failed')
          } finally {
            setBusy(false)
          }
        }}
      />
    </div>
  )
}
