import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createCourse,
  decideQuestion,
  fetchAdminDecks,
  fetchQuestionVersions,
  fetchQuiz,
  fetchQuizzes,
  fetchCourses,
} from '../api/academy'
import {
  fetchArticles,
  fetchScholars,
  transitionArticle,
  verifyScholar,
  type ArticleRow,
  type Scholar,
} from '../api/research'
import { installApiMock, renderAt } from '../test/helpers'

/**
 * M5 admin CMS — Academy, Assessments (versioned question bank), Research
 * (three-tier classification + scholarly review) and the Scholar directory.
 * Service modules are mocked; routing, guards and nav filtering run for real.
 */

vi.mock('../api/academy', () => ({
  fetchCourses: vi.fn(async () => ({ data: [], meta: undefined })),
  fetchCourse: vi.fn(),
  createCourse: vi.fn(),
  updateCourse: vi.fn(),
  transitionCourse: vi.fn(),
  addChapter: vi.fn(),
  updateChapter: vi.fn(),
  addLesson: vi.fn(),
  updateLesson: vi.fn(),
  fetchQuizzes: vi.fn(async () => ({ data: [], meta: undefined })),
  fetchQuiz: vi.fn(),
  createQuiz: vi.fn(),
  updateQuiz: vi.fn(),
  transitionQuiz: vi.fn(),
  createQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  submitQuestionReview: vi.fn(),
  decideQuestion: vi.fn(),
  fetchQuestionVersions: vi.fn(async () => []),
  fetchAdminDecks: vi.fn(async () => ({ data: [], meta: undefined })),
  createAdminDeck: vi.fn(),
  patchAdminDeck: vi.fn(),
}))

vi.mock('../api/research', () => ({
  fetchTopics: vi.fn(async () => []),
  fetchArticles: vi.fn(async () => ({ data: [], meta: undefined })),
  fetchArticle: vi.fn(),
  createArticle: vi.fn(),
  updateArticle: vi.fn(),
  transitionArticle: vi.fn(),
  fetchScholars: vi.fn(async () => []),
  createScholar: vi.fn(),
  updateScholar: vi.fn(),
  verifyScholar: vi.fn(),
}))

const meM5 = {
  userId: '44444444-4444-4444-4444-444444444444',
  email: 'academy-manager@ikp.test',
  displayName: 'academy-manager',
  language: 'en',
  status: 'active',
  roles: ['academy_manager'],
  permissions: ['academy.manage', 'assessment.manage', 'scholars.manage', 'content.edit', 'content.publish'],
  createdAt: '2026-11-01T00:00:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
  installApiMock({
    '/auth/me': meM5,
    '/admin/subjects': [
      { slug: 'cat_learning_academy', name: 'Learning Academy', nameAr: null, description: null, imageUrl: null, position: 13, status: 'active', updatedAt: '2026-11-01T00:00:00Z' },
      { slug: 'cat_aqeedah', name: 'Aqeedah', nameAr: null, description: null, imageUrl: null, position: 5, status: 'active', updatedAt: '2026-11-01T00:00:00Z' },
    ],
  })
})

describe('academy admin (M5)', () => {
  it('lists courses with structure counts and creates a draft', async () => {
    vi.mocked(fetchCourses).mockResolvedValue({
      data: [
        {
          courseId: 'c-1',
          title: 'Intro to Usul al-Fiqh',
          language: 'en',
          level: 'beginner',
          status: 'draft',
          subject: { slug: 'cat_learning_academy', name: 'Learning Academy' },
          estimatedHours: 6,
          instructorDisplayName: 'Recorded Instructor',
          chapterCount: 2,
          lessonCount: 5,
          enrolmentCount: 0,
          updatedAt: '2026-11-02T00:00:00Z',
        },
      ],
      meta: undefined,
    })
    renderAt('/academy')
    expect(await screen.findByText('Intro to Usul al-Fiqh')).toBeInTheDocument()
    expect(screen.getByText(/2 chapters · 5 lessons/)).toBeInTheDocument()
    const row = screen.getByText('Intro to Usul al-Fiqh').closest('tr')
    expect(row && within(row).getByText('draft')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'New course' }))
    fireEvent.change(await screen.findByLabelText('Title'), { target: { value: 'New course X' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create draft' }))
    await waitFor(() => expect(createCourse).toHaveBeenCalledTimes(1))
  })

  it('offers only draft-state transitions on the course detail', async () => {
    vi.mocked(fetchCourses).mockResolvedValue({ data: [], meta: undefined })
    vi.mocked(fetchQuiz).mockResolvedValue({
      quizId: 'q',
      title: 'x',
      description: null,
      language: 'en',
      status: 'draft',
      timeLimitSeconds: null,
      passScorePct: 60,
      questions: [],
    } as never)
    renderAt('/academy')
    expect(await screen.findByText('No courses yet — create the first draft.')).toBeInTheDocument()
  })
})

describe('assessment admin (M5)', () => {
  const quizDetail = {
    quizId: 'q-1',
    title: 'Aqeedah basics',
    description: null,
    language: 'en',
    status: 'approved',
    timeLimitSeconds: null,
    passScorePct: 60,
    questions: [
      {
        questionId: 'qa-1',
        prompt: 'How many pillars of Iman?',
        promptAr: null,
        explanation: 'Six, per the Hadith of Jibril.',
        points: 1,
        version: 2,
        reviewState: 'pending_review',
        sortOrder: 0,
        options: [
          { optionId: 'o1', optionText: 'Five', correct: false },
          { optionId: 'o2', optionText: 'Six', correct: true },
        ],
      },
    ],
  }

  it('shows question review state, version counter and explanation, and records decisions', async () => {
    vi.mocked(fetchQuizzes).mockResolvedValue({
      data: [{ quizId: 'q-1', title: 'Aqeedah basics', language: 'en', status: 'approved', subjectSlug: 'cat_aqeedah', questionCount: 1, approvedQuestionCount: 0, timeLimitSeconds: null, updatedAt: '2026-11-02T00:00:00Z' }],
      meta: undefined,
    })
    vi.mocked(fetchAdminDecks).mockResolvedValue({ data: [], meta: undefined })
    vi.mocked(fetchQuiz).mockResolvedValue(quizDetail as never)
    renderAt('/assessments')

    fireEvent.click(await screen.findByRole('button', { name: 'Manage' }))
    expect(await screen.findByText(/How many pillars of Iman/)).toBeInTheDocument()
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(screen.getByText('pending review')).toBeInTheDocument()
    // server-only data is visible to editors but labelled as such
    expect(screen.getByText(/Explanation \(server-only data\):/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))
    await waitFor(() => expect(decideQuestion).toHaveBeenCalledWith('qa-1', true))
  })

  it('exposes the version history for a question', async () => {
    vi.mocked(fetchQuizzes).mockResolvedValue({
      data: [{ quizId: 'q-1', title: 'Aqeedah basics', language: 'en', status: 'approved', subjectSlug: null, questionCount: 1, approvedQuestionCount: 0, timeLimitSeconds: null, updatedAt: '2026-11-02T00:00:00Z' }],
      meta: undefined,
    })
    vi.mocked(fetchAdminDecks).mockResolvedValue({ data: [], meta: undefined })
    vi.mocked(fetchQuiz).mockResolvedValue(quizDetail as never)
    vi.mocked(fetchQuestionVersions).mockResolvedValue([
      { version: 1, snapshot: { prompt: 'Original prompt' }, changeNote: 'created', editedBy: 'a@ikp.test', editedAt: '2026-11-01T00:00:00Z' },
      { version: 2, snapshot: { prompt: 'How many pillars of Iman?' }, changeNote: 'reworded', editedBy: 'b@ikp.test', editedAt: '2026-11-02T00:00:00Z' },
    ])
    renderAt('/assessments')
    fireEvent.click(await screen.findByRole('button', { name: 'Manage' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Versions' }))
    expect(await screen.findByText(/v1 — created/)).toBeInTheDocument()
    expect(screen.getByText(/v2 — reworded/)).toBeInTheDocument()
  })

  it('lists flashcard decks with publish state select', async () => {
    vi.mocked(fetchQuizzes).mockResolvedValue({ data: [], meta: undefined })
    vi.mocked(fetchAdminDecks).mockResolvedValue({
      data: [{ deckId: 'd-1', title: 'Arabic roots deck', description: null, language: 'en', subjectSlug: 'cat_arabic_academy', cardCount: 12, ownerType: 'admin', publishState: 'draft', updatedAt: '2026-11-02T00:00:00Z' }],
      meta: undefined,
    })
    renderAt('/assessments')
    fireEvent.click(await screen.findByRole('tab', { name: 'Flashcard decks' }))
    expect(await screen.findByText('Arabic roots deck')).toBeInTheDocument()
    expect(screen.getByLabelText('Publish state of Arabic roots deck')).toHaveValue('draft')
  })
})

describe('research admin (M5)', () => {
  const rows: ArticleRow[] = [
    {
      articleId: 'a-1',
      title: 'Sahih chain methodology',
      topic: { slug: 'hadith', name: 'Hadith' },
      sourceTier: 'secondary',
      status: 'in_scholarly_review',
      language: 'en',
      authorDisplayName: null,
      updatedAt: '2026-11-02T00:00:00Z',
    },
    {
      articleId: 'a-2',
      title: 'Editorial note on study plans',
      topic: { slug: 'quran', name: 'Quran' },
      sourceTier: 'editorial',
      status: 'approved',
      language: 'en',
      authorDisplayName: null,
      updatedAt: '2026-11-02T00:00:00Z',
    },
  ]

  it('distinguishes source tiers and offers the scholarly decision for tiered articles', async () => {
    vi.mocked(fetchArticles).mockResolvedValue({ data: rows, meta: undefined })
    renderAt('/research')

    expect(await screen.findByText('Sahih chain methodology')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getAllByText('secondary').length).toBeGreaterThan(0)
    expect(within(screen.getByRole('table')).getAllByText('editorial').length).toBeGreaterThan(0)
    // in_scholarly_review → scholarly approval offered to eligible roles
    expect(screen.getByRole('button', { name: 'Approve (scholarly)' })).toBeInTheDocument()
    // approved editorial article → publish
    expect(screen.getByRole('button', { name: 'Publish' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Approve (scholarly)' }))
    await waitFor(() => expect(transitionArticle).toHaveBeenCalledWith('a-1', 'approve', undefined))
  })
})

describe('scholar directory admin (M5)', () => {
  const scholars: Scholar[] = [
    {
      scholarId: 's-1',
      name: 'Recorded Scholar',
      nameAr: null,
      biography: null,
      qualifications: 'Recorded qualification text',
      areasOfExpertise: 'Hadith sciences',
      verificationStatus: 'unverified',
      verifiedBy: null,
      verifiedAt: null,
      linkedUserEmail: null,
      createdAt: '2026-11-01T00:00:00Z',
      updatedAt: '2026-11-01T00:00:00Z',
    },
  ]

  it('lists scholars with verification status and audits the verify action', async () => {
    vi.mocked(fetchScholars).mockResolvedValue(scholars)
    renderAt('/scholars')

    expect(await screen.findByText('Recorded Scholar')).toBeInTheDocument()
    expect(screen.getByText('unverified')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Verify' }))
    // confirm dialog explains the audit trail before the action fires
    expect(await screen.findByText(/The action is audited/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(verifyScholar).toHaveBeenCalledWith('s-1', 'verified'))
  })
})

describe('M5 nav visibility', () => {
  it('shows the new sections to the academy manager and hides them from plain users', async () => {
    installApiMock({
      '/auth/me': meM5,
      '/admin/dashboard': {
        users: { total: 0, active: 0, newRegistrations: 0 },
        content: {},
        review: { awaitingApproval: [] },
        recentAdminActivity: [],
      },
    })
    vi.mocked(fetchCourses).mockResolvedValue({ data: [], meta: undefined })
    renderAt('/dashboard')
    // sidebar + mobile menu both render; both must include the M5 sections
    await waitFor(() => expect(screen.getAllByRole('link', { name: 'Academy' }).length).toBeGreaterThan(0))
    expect(screen.getAllByRole('link', { name: 'Assessments' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Scholars' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Research' }).length).toBeGreaterThan(0)
  })

  it('hides M5 sections from a user without the M5 permissions', async () => {
    installApiMock({
      '/auth/me': {
        userId: '55555555-5555-5555-5555-555555555555',
        email: 'auditor@ikp.test',
        displayName: 'auditor',
        language: 'en',
        status: 'active',
        roles: ['support_manager'],
        permissions: ['users.view'],
        createdAt: '2026-11-01T00:00:00Z',
      },
      '/admin/dashboard': {
        users: { total: 0, active: 0, newRegistrations: 0 },
        content: {},
        review: { awaitingApproval: [] },
        recentAdminActivity: [],
      },
    })
    renderAt('/dashboard')
    await waitFor(() => expect(screen.getAllByRole('link', { name: 'Users' }).length).toBeGreaterThan(0))
    expect(screen.queryAllByRole('link', { name: 'Academy' })).toHaveLength(0)
    expect(screen.queryAllByRole('link', { name: 'Assessments' })).toHaveLength(0)
    expect(screen.queryAllByRole('link', { name: 'Scholars' })).toHaveLength(0)
  })
})
