import { describe, expect, it, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import HealthBadge from './HealthBadge'

afterEach(() => vi.restoreAllMocks())

describe('HealthBadge', () => {
  it('shows UP when the backend reports a healthy database', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            data: {
              status: 'UP',
              service: 'ikp-backend',
              db: 'UP',
              timestamp: '2026-11-02T00:00:00Z',
            },
          }),
      }),
    )
    render(<HealthBadge />)
    await waitFor(() => {
      expect(screen.getByTestId('health-badge')).toHaveTextContent('Backend UP · DB UP')
    })
  })

  it('shows unreachable when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('connection refused')))
    render(<HealthBadge />)
    await waitFor(() => {
      expect(screen.getByTestId('health-badge')).toHaveTextContent('Backend unreachable')
    })
  })
})
