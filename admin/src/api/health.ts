export type HealthData = {
  status: 'UP' | 'DEGRADED'
  service: string
  db: 'UP' | 'DOWN'
  timestamp: string
}

export type HealthResponse = { data: HealthData }

/** Calls the backend ops health endpoint (approved envelope: {"data": ...}). */
export async function fetchHealth(baseUrl = ''): Promise<HealthData> {
  const res = await fetch(`${baseUrl}/api/v1/health`)
  if (!res.ok) throw new Error(`health endpoint returned ${res.status}`)
  const body = (await res.json()) as HealthResponse
  return body.data
}
