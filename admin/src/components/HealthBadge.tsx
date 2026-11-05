import { useEffect, useState } from 'react'
import { fetchHealth, type HealthData } from '../api/health'

/** Live backend health indicator used on the foundation dashboard. */
export default function HealthBadge() {
  const [health, setHealth] = useState<HealthData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchHealth()
      .then((h) => !cancelled && setHealth(h))
      .catch((e) => !cancelled && setError(String(e)))
    return () => {
      cancelled = true
    }
  }, [])

  if (error) {
    return (
      <span
        data-testid="health-badge"
        className="inline-block rounded-full bg-red-100 px-3 py-1 text-sm font-medium text-red-800"
      >
        Backend unreachable
      </span>
    )
  }
  if (!health) {
    return (
      <span
        data-testid="health-badge"
        className="inline-block rounded-full bg-gray-100 px-3 py-1 text-sm font-medium text-gray-600"
      >
        Checking…
      </span>
    )
  }
  return (
    <span
      data-testid="health-badge"
      className={
        health.status === 'UP'
          ? 'inline-block rounded-full bg-emerald-100 px-3 py-1 text-sm font-medium text-emerald-800'
          : 'inline-block rounded-full bg-amber-100 px-3 py-1 text-sm font-medium text-amber-800'
      }
    >
      Backend {health.status} · DB {health.db}
    </span>
  )
}
