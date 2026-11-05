import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { ApiError } from '../../api/client'
import { Button, ErrorState, Field, Input } from '../../components/ui'

/** Admin login on the approved M1/M2 auth foundation (email + password baseline; OD-04 open). */
export default function LoginPage() {
  const { me, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({})
  const [serverError, setServerError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const from = (location.state as { from?: string } | null)?.from || '/dashboard'

  if (!loading && me) return <Navigate to={from} replace />

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const errs: typeof fieldErrors = {}
    if (!email.trim()) errs.email = 'Email is required'
    else if (!/^\S+@\S+\.\S+$/.test(email)) errs.email = 'Enter a valid email address'
    if (!password) errs.password = 'Password is required'
    setFieldErrors(errs)
    if (Object.keys(errs).length > 0) return
    setBusy(true)
    setServerError(null)
    try {
      await login(email.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setServerError(
          err.code === 'RATE-001'
            ? 'Too many attempts — please wait a few minutes and try again.'
            : err.code === 'AUTH-004'
              ? 'This account is suspended or deleted. Contact a Super Admin.'
              : err.code === 'AUTH-001'
                ? 'Invalid email or password.'
                : err.detail,
        )
      } else {
        setServerError('Cannot reach the backend. Is the API server running?')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory-warm p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-semibold text-emerald-deep">
            The Islam
          </h1>
          <p className="mt-1 text-sm text-gray-500">Admin Portal</p>
        </div>
        <form className="rounded-lg bg-white p-8 shadow" onSubmit={submit} noValidate>
          {serverError && (
            <div className="mb-4">
              <ErrorState message={serverError} />
            </div>
          )}
          <Field label="Email" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@ikp.test"
            />
          </Field>
          <Field label="Password" error={fieldErrors.password}>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
          </Field>
          <Button type="submit" className="w-full py-2" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
          <p className="mt-4 text-xs leading-relaxed text-gray-400">
            Authorised administrators only. All sign-ins and administrative actions are
            recorded in the audit log. Development credentials are listed in the project
            README — they are synthetic and never used in production.
          </p>
        </form>
        <p className="mt-4 text-center text-xs text-gray-400">
          <Link to="/health" className="hover:text-emerald-deep">
            Platform status
          </Link>
        </p>
      </div>
    </div>
  )
}
