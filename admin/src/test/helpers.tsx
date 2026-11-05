import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import type { Envelope } from '../api/client'
import App from '../App'

/**
 * Test scaffolding for the M2 admin CMS: the real router + providers render
 * against a mocked API client (mutable route table). Auth is pre-populated
 * through the same session-storage contract the real client uses, so guards,
 * navigation and permission logic behave genuinely.
 */

const mockState = vi.hoisted(() => ({
  routes: {} as Record<string, unknown>,
  hasSession: true,
}))

vi.mock('../api/client', () => {
  const resolve = (path: string): unknown => {
    const bare = path.split('?')[0]
    const keys = Object.keys(mockState.routes)
    const key = keys.find((k) => (k.endsWith('*') ? bare.startsWith(k.slice(0, -1)) : bare === k))
    if (key === undefined) throw new Error(`mock: no route for ${path}`)
    const value = mockState.routes[key]
    if (value instanceof Error) throw value
    return value
  }
  return {
    ApiError: class ApiError extends Error {
      code: string
      status: number
      detail: string
      constructor(status: number, body: { code?: string; title?: string; detail?: string } = {}) {
        super(body.detail || body.title || `HTTP ${status}`)
        this.code = body.code || `HTTP-${status}`
        this.status = status
        this.detail = body.detail || body.title || `HTTP ${status}`
      }
    },
    api: vi.fn(async (path: string): Promise<Envelope<unknown>> => {
      const v = resolve(path)
      return (v && typeof v === 'object' && 'data' in (v as object) ? v : { data: v }) as Envelope<unknown>
    }),
    apiData: vi.fn(async (path: string) => {
      const v = resolve(path)
      return v && typeof v === 'object' && 'data' in (v as object) ? (v as Envelope<unknown>).data : v
    }),
    apiUpload: vi.fn(async (path: string) => ({ data: resolve(path) })),
    storeTokens: vi.fn(),
    clearTokens: vi.fn(),
    accessToken: () => (mockState.hasSession ? 'test-access-token' : null),
    refreshToken: () => (mockState.hasSession ? 'test-refresh-token' : null),
    rotateOnce: vi.fn(async () => null),
  }
})

export const fixtures = {
  me: {
    userId: '11111111-1111-1111-1111-111111111111',
    email: 'dev-admin@ikp.test',
    displayName: 'dev-admin',
    language: 'en',
    status: 'active',
    roles: ['admin'],
    permissions: [
      'users.view',
      'users.suspend',
      'users.delete',
      'audit.view',
      'files.upload',
      'fatwa.create',
      'fatwa.publish',
      'content.edit',
      'content.publish',
      'academy.manage',
    ],
    createdAt: '2026-11-01T00:00:00Z',
  },
  meLimited: {
    userId: '33333333-3333-3333-3333-333333333333',
    email: 'reader@ikp.test',
    displayName: 'reader',
    language: 'en',
    status: 'active',
    roles: ['registered_user'],
    permissions: [] as string[],
    createdAt: '2026-11-01T00:00:00Z',
  },
  dashboard: {
    users: { total: 8, active: 7, newRegistrations: 8 },
    content: {
      subjects: 15,
      books: 2,
      publishedBooks: 1,
      draftBooks: 1,
      publishedContent: 1,
      uploads: 3,
      courses: 0,
      researchArticles: 0,
      quranStats: { imported: false, note: 'import gated at OD-05/G-3' },
      hadithStats: { imported: false, note: 'import gated at OD-05/G-3' },
    },
    review: {
      pendingBookEditorial: 1,
      pendingBookScholarly: 0,
      pendingFatwaReview: 1,
      awaitingApproval: [
        {
          entity: 'books',
          entityId: 'book-1',
          title: 'Dev Sample: Pending (synthetic)',
          state: 'in_editorial_review',
          waitingDays: 0,
        },
      ],
    },
    recentAdminActivity: [
      { at: '2026-11-02T00:00:00Z', action: 'book.create', entity: 'book', entityId: '1', actor: 'dev-admin@ikp.test' },
    ],
  },
  users: [
    {
      userId: '22222222-2222-2222-2222-222222222222',
      email: 'reader-1@ikp.test',
      displayName: 'reader-1',
      status: 'active',
      roles: ['registered_user'],
      registeredAt: '2026-11-01T00:00:00Z',
      lastSeenAt: null,
      deletionState: 'none',
    },
  ],
  subjects: Array.from({ length: 15 }, (_, i) => ({
    subjectId: `cat-${i + 1}`,
    slug: i === 5 ? 'cat_unconfirmed_06' : `cat_${i + 1}`,
    name: i === 5 ? '06/CAT-UNCONFIRMED_06' : `Category ${i + 1}`,
    nameAr: null,
    description: null,
    imageUrl: null,
    position: i + 1,
    status: i === 5 ? 'hidden_unconfirmed' : 'active',
    updatedAt: '2026-11-01T00:00:00Z',
  })),
  book: {
    bookId: 'book-1',
    title: 'Dev Sample: Pending (synthetic)',
    subtitle: null,
    description: 'fixture',
    language: 'en',
    subject: { slug: 'cat_learning_academy', name: 'Learning Academy' },
    authors: [],
    publisher: null,
    edition: null,
    publicationYear: 2026,
    pageCount: 10,
    isbn: null,
    copyrightInfo: null,
    licence: null,
    downloadAllowed: false,
    scholarlyReviewRequired: false,
    status: 'in_editorial_review',
    cover: null,
    files: [],
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-02T00:00:00Z',
    publishedAt: null,
  },
  bookPage: {
    data: [
      {
        bookId: 'book-1',
        title: 'Dev Sample: Pending (synthetic)',
        subtitle: null,
        description: null,
        language: 'en',
        subject: { slug: 'cat_learning_academy', name: 'Learning Academy' },
        authors: [],
        publisher: null,
        edition: null,
        publicationYear: 2026,
        pageCount: 10,
        isbn: null,
        copyrightInfo: null,
        licence: null,
        downloadAllowed: false,
        scholarlyReviewRequired: false,
        status: 'in_editorial_review',
        cover: null,
        files: [],
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-02T00:00:00Z',
        publishedAt: null,
      },
    ],
    meta: { page: 1, size: 20, totalElements: 1, totalPages: 1 },
  },
  fatwa: {
    fatwaId: 'fatwa-1',
    question: 'Dev fixture question (synthetic)?',
    category: 'dev-fixture',
    language: 'en',
    state: 'in_review',
    version: 1,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-02T00:00:00Z',
    publishedAt: null,
  },
  audit: [
    {
      id: 1,
      at: '2026-11-02T00:00:00Z',
      actorId: 4,
      action: 'workflow.publish',
      entityType: 'book',
      entityId: 7,
      beforeHash: 'a'.repeat(64),
      afterHash: 'b'.repeat(64),
      ip: '127.0.0.1',
      userAgent: 'vitest',
    },
  ],
}

/** (Re)install the API route table for the next render. */
export function installApiMock(routes: Record<string, unknown>, opts: { session?: boolean } = {}) {
  mockState.routes = routes
  mockState.hasSession = opts.session !== false
}

export function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}
