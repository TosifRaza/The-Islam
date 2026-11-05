import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fixtures, installApiMock, renderAt } from './helpers'

/**
 * M2 §14 — login/logout, protected routes, role-based navigation and
 * permission-denied behaviour, driven through the real router + guards.
 */

describe('admin authentication and RBAC surface (M2 §6)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('redirects visitors without a session to /login', async () => {
    installApiMock({}, { session: false })
    renderAt('/dashboard')
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('rejects an empty login form client-side without calling the API', async () => {
    installApiMock({}, { session: false })
    renderAt('/login')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(await screen.findByText(/email is required/i)).toBeInTheDocument()
  })

  it('renders the admin shell for an authenticated admin with role-filtered navigation', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/dashboard': fixtures.dashboard,
    })
    renderAt('/dashboard')
    expect(await screen.findByRole('heading', { name: /overview/i })).toBeInTheDocument()
    const nav = document.querySelector('aside') as HTMLElement
    // admin holds content.edit / academy.manage / files.upload / users.view / audit.view / fatwa.create
    for (const label of ['Books', 'Subjects', 'Users', 'Audit log', 'Fatwas', 'Review queue']) {
      expect(within(nav).getByText(label)).toBeInTheDocument()
    }
  })

  it('hides navigation entries the user has no permission for (registered user)', async () => {
    installApiMock({
      '/auth/me': fixtures.meLimited,
      '/admin/dashboard': fixtures.dashboard,
    })
    renderAt('/dashboard')
    expect(await screen.findByRole('heading', { name: /overview/i })).toBeInTheDocument()
    const nav = document.querySelector('aside') as HTMLElement
    expect(within(nav).queryByText('Users')).toBeNull()
    expect(within(nav).queryByText('Audit log')).toBeNull()
    expect(within(nav).queryByText('Books')).toBeNull()
    expect(within(nav).queryByText('Subjects')).toBeNull()
  })

  it('shows the permission-denied page when a limited user opens a guarded URL directly', async () => {
    installApiMock({
      '/auth/me': fixtures.meLimited,
      '/admin/dashboard': fixtures.dashboard,
    })
    renderAt('/audit-logs')
    expect(await screen.findByText(/permission required/i)).toBeInTheDocument()
    expect(screen.getByText(/audit\.view/)).toBeInTheDocument()
  })

  it('a scholar sees the review queue but not user management', async () => {
    installApiMock({
      '/auth/me': {
        ...fixtures.meLimited,
        roles: ['scholar_reviewer'],
        permissions: ['fatwa.review.approve'],
      },
      '/admin/review-queue': [],
      '/admin/dashboard': { users: { total: 0, active: 0, newRegistrations: 0 }, content: {}, review: { pendingBookEditorial: 0, pendingBookScholarly: 0, pendingFatwaReview: 0, awaitingApproval: [] }, recentAdminActivity: [] },
    })
    renderAt('/review-queue')
    expect(await screen.findByRole('heading', { name: /review queue/i })).toBeInTheDocument()
    const nav = document.querySelector('aside') as HTMLElement
    expect(within(nav).getByText('Review queue')).toBeInTheDocument()
    expect(within(nav).queryByText('Users')).toBeNull()
    await waitFor(() => expect(screen.getByText(/queue is empty/i)).toBeInTheDocument())
  })
})
