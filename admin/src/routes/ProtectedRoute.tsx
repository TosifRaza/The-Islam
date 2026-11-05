import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { PermissionDenied, Spinner } from '../components/ui'

/** Blocks unauthenticated access; remembers the intended destination. */
export function RequireAuth() {
  const { me, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner label="Checking your session…" />
  if (!me) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  return <Outlet />
}

/** Server-derived permission gate — hiding UI is never the security boundary. */
export function RequirePermission({ anyPermission, anyRole, children }: {
  anyPermission?: string[]
  anyRole?: string[]
  children: React.ReactNode
}) {
  const { me, loading, hasPermission, hasAnyRole } = useAuth()
  if (loading) return <Spinner />
  if (!me) return <Navigate to="/login" replace />
  const allowed =
    (!anyPermission || anyPermission.some((p) => hasPermission(p))) &&
    (!anyRole || anyRole.some((r) => hasAnyRole(r)))
  if (!allowed) return <PermissionDenied needed={anyPermission ? anyPermission.join(' / ') : anyRole?.join(' / ')} />
  return <>{children}</>
}
