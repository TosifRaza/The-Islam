import { screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fixtures, installApiMock, renderAt } from './helpers'

/** M2 §14 — books, uploads, review workflow, fatwa guardrails and audit display. */

describe('book management (M2 Module D)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists books with filters, format and publication state', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/subjects': fixtures.subjects,
      '/admin/books': fixtures.bookPage,
    })
    renderAt('/books')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('Dev Sample: Pending (synthetic)')).toBeInTheDocument()
    expect(within(table).getByText('in editorial review')).toBeInTheDocument()
    // workflow filter options exist
    expect(screen.getByLabelText('Status')).toBeInTheDocument()
    expect(screen.getByLabelText('Format')).toBeInTheDocument()
  })

  it('empty database renders the empty state with a create action', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/subjects': fixtures.subjects,
      '/admin/books': { data: [], meta: { page: 1, size: 20, totalElements: 0, totalPages: 1 } },
    })
    renderAt('/books')
    expect(await screen.findByText('No books match')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /add book/i }).length).toBeGreaterThan(0)
  })

  it('book detail exposes only the transitions valid for the current state (editorial review)', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/books/book-1': fixtures.book,
      '/admin/books/book-1/reviews': [],
    })
    renderAt('/books/book-1')
    expect(await screen.findByText('Metadata')).toBeInTheDocument()
    // in_editorial_review + admin → approve/reject offered; publish NOT yet
    expect(screen.getByRole('button', { name: /approve \(editorial\)/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reject…/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^publish$/i })).toBeNull()
    // draft notice is absent (not a draft)
    expect(screen.queryByText(/this book is a private draft/i)).toBeNull()
  })

  it('draft books show the not-public notice and only the submit action', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/books/book-1': { ...fixtures.book, status: 'draft' },
      '/admin/books/book-1/reviews': [],
    })
    renderAt('/books/book-1')
    expect(await screen.findByText(/this book is a private draft/i)).toBeInTheDocument()
    // draft state: submit-for-review offered; approve/reject/publish not applicable
    expect(screen.getByRole('button', { name: /submit for review/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /reject…/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /^publish$/i })).toBeNull()
  })
})

describe('review queue and fatwa guardrails (M2 Module F)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('queues books and fatwas with the state tabs and open links', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/review-queue': [
        { entity: 'books', entityId: 'book-1', title: 'Dev Sample: Pending (synthetic)', state: 'in_editorial_review', waitingDays: 1 },
        { entity: 'fatwas', entityId: 'fatwa-1', title: 'Dev fixture question (synthetic)?', state: 'in_review' },
      ],
    })
    renderAt('/review-queue')
    expect(await screen.findByText('Dev Sample: Pending (synthetic)')).toBeInTheDocument()
    expect(screen.getByText('Dev fixture question (synthetic)?')).toBeInTheDocument()
    const tabs = ['Editorial review', 'Scholarly review', 'Ready to publish', 'Fatwas in review', 'Rejected', 'Archived']
    for (const t of tabs) expect(screen.getByRole('button', { name: t })).toBeInTheDocument()
  })

  it('scholar sees the decision panel on an in-review Fatwa; publish only appears in approved state', async () => {
    installApiMock({
      '/auth/me': {
        ...fixtures.me,
        roles: ['scholar_reviewer'],
        permissions: ['fatwa.review.approve'],
      },
      '/admin/fatwas/fatwa-1': fixtures.fatwa,
      '/admin/fatwas/fatwa-1/audit': [],
    })
    renderAt('/fatwas/fatwa-1')
    expect(await screen.findByText('Scholarly decision')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^approve$/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^publish$/i })).toBeNull()
  })

  it('an admin (non-scholar) sees no decision panel — separation of duties is reflected in the UI', async () => {
    installApiMock({
      '/auth/me': fixtures.me, // admin: fatwa.publish but NOT fatwa.review.approve
      '/admin/fatwas/fatwa-1': fixtures.fatwa,
      '/admin/fatwas/fatwa-1/audit': [],
    })
    renderAt('/fatwas/fatwa-1')
    expect(await screen.findByText('Audit trail')).toBeInTheDocument()
    expect(screen.queryByText('Scholarly decision')).toBeNull()
  })

  it('an approved Fatwa offers publish to a fatwa.publish holder', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/fatwas/fatwa-1': { ...fixtures.fatwa, state: 'approved' },
      '/admin/fatwas/fatwa-1/audit': [],
    })
    renderAt('/fatwas/fatwa-1')
    expect(await screen.findByRole('button', { name: /^publish$/i })).toBeInTheDocument()
  })
})

describe('files and audit (M2 Modules E + G)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('upload inventory shows scan verdicts and quarantined files are not downloadable', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/files': {
        data: [
          { mediaId: 'm-1', purpose: 'book_pdf', originalFilename: 'a.pdf', mime: 'application/pdf', sizeBytes: 1200, scanStatus: 'clean', scanner: 'test-only-magic-scanner', createdAt: '2026-11-01T00:00:00Z' },
          { mediaId: 'm-2', purpose: 'cover', originalFilename: 'bad.png', mime: 'image/png', sizeBytes: 300, scanStatus: 'infected', scanner: 'test-only-magic-scanner', createdAt: '2026-11-01T00:00:00Z' },
        ],
        meta: { page: 1, size: 20, totalElements: 2, totalPages: 1 },
      },
    })
    renderAt('/files')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('a.pdf')).toBeInTheDocument()
    expect(within(table).getByText('bad.png')).toBeInTheDocument()
    // clean file → signed URL action; infected → quarantined label, no action
    expect(within(table).getByRole('button', { name: /signed url/i })).toBeInTheDocument()
    expect(within(table).getByText('quarantined')).toBeInTheDocument()
    // the page discloses the test-only scanner honestly (note + per-row scanner column)
    expect(screen.getAllByText(/test-only-magic-scanner/i).length).toBeGreaterThanOrEqual(2)
  })

  it('audit log renders entries with actor, entity and state hashes', async () => {
    installApiMock({
      '/auth/me': fixtures.me,
      '/admin/audit-logs': {
        data: fixtures.audit,
        meta: { page: 1, size: 20, totalElements: 1, totalPages: 1 },
      },
    })
    renderAt('/audit-logs')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('workflow.publish')).toBeInTheDocument()
    expect(within(table).getByText('book #7')).toBeInTheDocument()
    expect(within(table).getByText('127.0.0.1')).toBeInTheDocument()
    expect(within(table).getByText(/before aaaaaaaaaa…/i)).toBeInTheDocument()
  })
})
