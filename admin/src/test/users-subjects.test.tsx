import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fixtures, installApiMock, renderAt } from './helpers'

/** M2 §14 — user management + subject management surfaces with the 15-position rules. */

describe('user management (M2 Module B)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists users with roles, status and audited action buttons', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/users': fixtures.users,
    })
    renderAt('/users')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('reader-1@ikp.test')).toBeInTheDocument()
    expect(within(table).getByText('registered_user')).toBeInTheDocument()
    // admin holds users.suspend + users.delete → action buttons visible
    expect(within(table).getByRole('button', { name: 'Suspend' })).toBeInTheDocument()
    expect(within(table).getByRole('button', { name: 'Delete…' })).toBeInTheDocument()
  })

  it('a content manager has no users.view at all — the guard blocks the page entirely', async () => {
    installApiMock({
      '/auth/me': { ...fixtures.me, roles: ['content_manager'], permissions: ['content.edit', 'files.upload', 'academy.manage'] },
      '/admin/users': fixtures.users,
    })
    renderAt('/users')
    expect(await screen.findByText(/permission required/i)).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('opens the suspension confirmation requiring an audited reason', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/users': fixtures.users,
    })
    renderAt('/users')
    const table = await screen.findByRole('table')
    const user = userEvent.setup()
    await user.click(within(table).getByRole('button', { name: 'Suspend' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/suspend this account\?/i)).toBeInTheDocument()
    // confirm disabled until a ≥10-char reason is recorded
    const confirm = within(dialog).getByRole('button', { name: /suspend account/i })
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/reason/i), 'policy violation investigation')
    expect(confirm).toBeEnabled()
  })

  it('shows the user detail page with roles editor for Super Admin capability', async () => {
    installApiMock({
      '/auth/me': { ...fixtures.me, roles: ['super_admin'], permissions: [...fixtures.me.permissions, 'roles.manage'] },
      '/admin/users/22222222-2222-2222-2222-222222222222': {
        ...fixtures.users[0],
        deletionRequests: [],
      },
      '/admin/users/22222222-2222-2222-2222-222222222222/activity': [],
    })
    renderAt('/users/22222222-2222-2222-2222-222222222222')
    expect(await screen.findByRole('heading', { name: 'reader-1' })).toBeInTheDocument()
    expect(screen.getByText('Assigned roles')).toBeInTheDocument()
    // all 11 approved roles are offered
    for (const role of ['super_admin', 'scholar_reviewer', 'support_manager']) {
      expect(screen.getByRole('checkbox', { name: role })).toBeInTheDocument()
    }
  })
})

describe('subject management (M2 Module C)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists all 15 positions with stable slugs and locks category 06', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/subjects': fixtures.subjects,
    })
    renderAt('/subjects')
    const table = await screen.findByRole('table')
    // exactly 15 rows
    expect(within(table).getAllByRole('row').length - 1).toBe(15)
    // category 06 shown locked with the unresolved label
    expect(within(table).getByText('Unidentified category — unresolved')).toBeInTheDocument()
    expect(within(table).getByText('cat_unconfirmed_06')).toBeInTheDocument()
    expect(within(table).getByText('locked (OD-03)')).toBeInTheDocument()
    // an ordinary category has edit + publish controls
    expect(within(table).getAllByRole('button', { name: 'Edit' }).length).toBe(14)
  })

  it('category 06 row has no edit/publish controls and cannot be mutated from the UI', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/subjects': fixtures.subjects,
    })
    renderAt('/subjects')
    const table = await screen.findByRole('table')
    const row = table.querySelector('tr.bg-gray-50\\/60') as HTMLElement
    expect(row).toBeTruthy()
    expect(within(row).queryByRole('button', { name: 'Edit' })).toBeNull()
    expect(within(row).queryByRole('button', { name: 'Unpublish' })).toBeNull()
  })
})
