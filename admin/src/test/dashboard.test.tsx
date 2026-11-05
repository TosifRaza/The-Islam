import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fixtures, installApiMock, renderAt } from './helpers'

/** M2 §14 — dashboard rendering with real backend-shaped metrics + zero/error states. */

describe('dashboard (M2 Module A)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders KPIs, the approval queue and recent activity from backend data', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/dashboard': fixtures.dashboard,
    })
    renderAt('/dashboard')
    expect(await screen.findByText('Total registered')).toBeInTheDocument()
    expect(screen.getByText('Total registered').closest('div')).toHaveTextContent('8')
    expect(screen.getByText('Published books').closest('div')).toHaveTextContent('1')
    expect(screen.getByText(/import gated at OD-05\/G-3/i)).toBeInTheDocument()
    // approval queue contains the submitted book with a review link
    expect(screen.getByText('Dev Sample: Pending (synthetic)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^review$/i })).toHaveAttribute('href', '/books/book-1')
    // recent administrative activity shows a real audited action
    expect(screen.getByText(/book\.create/)).toBeInTheDocument()
  })

  it('reports not-yet-approved phases as honest zeros and links the queue', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/dashboard': fixtures.dashboard,
    })
    renderAt('/dashboard')
    await screen.findByText('Total registered')
    expect(screen.getByText('Courses').closest('div')).toHaveTextContent('0')
    expect(screen.getByText('Research articles').closest('div')).toHaveTextContent('0')
    expect(screen.getByRole('link', { name: /open queue/i })).toHaveAttribute('href', '/review-queue')
  })

  it('renders an error state with a retry control when the API fails', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/dashboard': Object.assign(new Error('backend down'), { code: 'SERVER-001' }),
    })
    renderAt('/dashboard')
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })

  it('navigates from a KPI card to the users list', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/dashboard': fixtures.dashboard,
      '/admin/users': fixtures.users,
    })
    renderAt('/dashboard')
    await screen.findByText('Total registered')
    const user = userEvent.setup()
    await user.click(screen.getByRole('link', { name: /total registered/i }))
    expect(await screen.findByRole('heading', { name: /user management/i })).toBeInTheDocument()
    const table = screen.getByRole('table')
    expect(within(table).getByText('reader-1@ikp.test')).toBeInTheDocument()
  })
})
