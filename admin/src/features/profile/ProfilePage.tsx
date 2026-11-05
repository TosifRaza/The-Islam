import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { api, ApiError } from '../../api/client'
import { Button, Card, Spinner, useToast } from '../../components/ui'
import type { SessionInfo } from '../../api/types'

/** Profile & sessions: identity, roles, permissions and session revocation (spec §3.1.9–3.1.10). */
export default function ProfilePage() {
  const { me, listSessions, revokeSession, logout, refreshMe } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<SessionInfo[] | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    try {
      setSessions(await listSessions())
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Could not load sessions')
    }
  }, [listSessions, toast])

  useEffect(() => {
    load()
  }, [load])

  if (!me) return <Spinner />

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-xl font-semibold text-gray-900">Profile &amp; sessions</h1>

      <Card>
        <h2 className="mb-3 font-medium text-gray-900">Identity</h2>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          <dt className="text-gray-500">Display name</dt>
          <dd>{me.displayName}</dd>
          <dt className="text-gray-500">Email</dt>
          <dd>{me.email}</dd>
          <dt className="text-gray-500">Status</dt>
          <dd>{me.status}</dd>
          <dt className="text-gray-500">Member since</dt>
          <dd>{new Date(me.createdAt).toLocaleString()}</dd>
          <dt className="text-gray-500">Roles</dt>
          <dd>{me.roles.join(', ')}</dd>
          <dt className="text-gray-500">Permissions</dt>
          <dd className="text-xs leading-relaxed text-gray-700">{me.permissions.join(', ')}</dd>
        </dl>
      </Card>

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3">
          <h2 className="font-medium text-gray-900">Active sessions</h2>
          <Button
            variant="ghost"
            onClick={async () => {
              try {
                await api('/auth/logout', { method: 'POST', body: {} })
                await logout()
                navigate('/login')
              } catch (e) {
                toast.push('error', e instanceof ApiError ? e.detail : 'Sign-out failed')
              }
            }}
          >
            Sign out everywhere
          </Button>
        </div>
        {!sessions ? (
          <Spinner label="Loading sessions…" />
        ) : sessions.length === 0 ? (
          <p className="px-5 py-4 text-sm text-gray-500">No other active sessions.</p>
        ) : (
          <ul className="divide-y divide-gray-100 text-sm">
            {sessions.map((s) => (
              <li key={s.sessionId} className="flex items-center justify-between px-5 py-3">
                <div>
                  <p className="font-medium text-gray-800">{s.deviceName || 'unnamed device'}</p>
                  <p className="text-xs text-gray-500">
                    started {new Date(s.createdAt).toLocaleString()} · expires {new Date(s.expiresAt).toLocaleString()}
                  </p>
                </div>
                <Button
                  variant="secondary"
                  disabled={busyId === s.sessionId}
                  onClick={async () => {
                    setBusyId(s.sessionId)
                    try {
                      await revokeSession(s.sessionId)
                      toast.push('success', 'Session revoked')
                      await load()
                      await refreshMe()
                    } catch (e) {
                      toast.push('error', e instanceof Error ? e.message : 'Revoke failed')
                    } finally {
                      setBusyId(null)
                    }
                  }}
                >
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <p className="text-xs text-gray-400">
        Authentication is the approved M1/M2 email + password baseline (Argon2id, 15-minute access tokens, rotating
        refresh tokens with reuse detection). OTP/social sign-in remains an OD-04 owner decision — no provider has
        been chosen.
      </p>
    </div>
  )
}
