import { api, apiData } from './client'

/**
 * M5 academy + assessment admin contracts (spec Modules 10 & 13). All paths
 * are relative to the `/api/v1` base; envelope/problem+json conventions are
 * handled by `client.ts`. Certificates are out of scope (OD-11) and instructor
 * fields are display-only recorded text — the CMS never generates
 * qualifications.
 */

export type WorkflowState = 'draft' | 'in_editorial_review' | 'approved' | 'published' | 'archived'
export type ReviewState = 'draft' | 'pending_review' | 'approved'
export type PublishState = 'draft' | 'approved' | 'published' | 'disabled'

export interface SubjectRef {
  slug: string
  name: string
}

export interface Paged<T> {
  data: T[]
  meta?: { page: number; size: number; totalElements: number; totalPages: number }
}

// ---------------------------------------------------------------- academy

export interface LessonAdminView {
  lessonId: string
  title: string
  lessonType: 'text' | 'video' | 'pdf' | 'quiz' | 'flashcards'
  sortOrder: number
  durationMinutes: number | null
  mediaAssetId: string | null
  quizId: string | null
  flashcardDeckId: string | null
  videoUrl: string | null
}

export interface ChapterAdminView {
  chapterId: string
  title: string
  description: string | null
  sortOrder: number
  lessons: LessonAdminView[]
}

export interface CourseAdminRow {
  courseId: string
  title: string
  language: string
  level: 'beginner' | 'intermediate' | 'advanced'
  status: WorkflowState
  subject: SubjectRef
  estimatedHours: number | null
  instructorDisplayName: string | null
  chapterCount: number
  lessonCount: number
  enrolmentCount: number
  updatedAt: string
}

export interface CourseAdminDetail {
  courseId: string
  title: string
  description: string | null
  language: string
  level: string
  status: WorkflowState
  subject: SubjectRef
  instructorDisplayName: string | null
  instructorCredentialsNote: string | null
  estimatedHours: number | null
  chapters: ChapterAdminView[]
}

export interface CourseRequest {
  title: string
  description?: string | null
  language?: string
  level?: string
  subjectSlug: string
  instructorDisplayName?: string | null
  instructorCredentialsNote?: string | null
  estimatedHours?: number | null
}

export interface ChapterRequest {
  title: string
  description?: string | null
  sortOrder?: number | null
}

export interface LessonRequest {
  title: string
  lessonType: string
  bodyText?: string | null
  videoUrl?: string | null
  mediaId?: string | null
  quizId?: string | null
  flashcardDeckId?: string | null
  durationMinutes?: number | null
  sortOrder?: number | null
}

export async function fetchCourses(status?: string): Promise<Paged<CourseAdminRow>> {
  const qs = status ? `?status=${encodeURIComponent(status)}&size=50` : '?size=50'
  return api<CourseAdminRow[]>(`/admin/academy/courses${qs}`, { raw: true }) as Promise<Paged<CourseAdminRow>>
}

export async function fetchCourse(courseId: string): Promise<CourseAdminDetail> {
  return apiData<CourseAdminDetail>(`/admin/academy/courses/${encodeURIComponent(courseId)}`)
}

export async function createCourse(body: CourseRequest): Promise<CourseAdminDetail> {
  return (await api<CourseAdminDetail>('/admin/academy/courses', { method: 'POST', body })).data
}

export async function updateCourse(courseId: string, body: CourseRequest): Promise<void> {
  await api(`/admin/academy/courses/${encodeURIComponent(courseId)}`, { method: 'PATCH', body })
}

export async function transitionCourse(courseId: string, action: string, notes?: string): Promise<void> {
  await api(`/admin/academy/courses/${encodeURIComponent(courseId)}/${action}`, {
    method: 'POST',
    body: notes ? { notes } : undefined,
  })
}

export async function addChapter(courseId: string, body: ChapterRequest): Promise<void> {
  await api(`/admin/academy/courses/${encodeURIComponent(courseId)}/chapters`, { method: 'POST', body })
}

export async function updateChapter(chapterId: string, body: ChapterRequest): Promise<void> {
  await api(`/admin/academy/chapters/${encodeURIComponent(chapterId)}`, { method: 'PATCH', body })
}

export async function addLesson(chapterId: string, body: LessonRequest): Promise<void> {
  await api(`/admin/academy/chapters/${encodeURIComponent(chapterId)}/lessons`, { method: 'POST', body })
}

export async function updateLesson(lessonId: string, body: LessonRequest): Promise<void> {
  await api(`/admin/academy/lessons/${encodeURIComponent(lessonId)}`, { method: 'PATCH', body })
}

// ---------------------------------------------------------------- assessments

export interface QuestionAdminView {
  questionId: string
  prompt: string
  promptAr: string | null
  explanation: string
  points: number
  version: number
  reviewState: ReviewState
  sortOrder: number
  options: { optionId: string; optionText: string; correct: boolean }[]
}

export interface QuizAdminRow {
  quizId: string
  title: string
  language: string
  status: WorkflowState
  subjectSlug: string | null
  questionCount: number
  approvedQuestionCount: number
  timeLimitSeconds: number | null
  updatedAt: string
}

export interface QuizAdminDetail {
  quizId: string
  title: string
  description: string | null
  language: string
  status: WorkflowState
  timeLimitSeconds: number | null
  passScorePct: number
  questions: QuestionAdminView[]
}

export interface QuizRequest {
  title: string
  description?: string | null
  language?: string
  courseId?: string | null
  subjectSlug?: string | null
  timeLimitSeconds?: number | null
  passScorePct?: number | null
}

export interface OptionRequest {
  optionText: string
  correct: boolean
}

export interface QuestionRequest {
  prompt: string
  promptAr?: string | null
  explanation: string
  points?: number | null
  sortOrder?: number | null
  changeNote?: string | null
  options: OptionRequest[]
}

export interface QuestionVersionView {
  version: number
  snapshot: Record<string, unknown>
  changeNote: string | null
  editedBy: string | null
  editedAt: string
}

export interface DeckRow {
  deckId: string
  title: string
  description: string | null
  language: string
  subjectSlug: string | null
  cardCount: number
  ownerType: 'admin' | 'user'
  publishState: PublishState
  updatedAt: string
}

export interface DeckDetail {
  deckId: string
  title: string
  description: string | null
  language: string
  subjectSlug: string | null
  ownerType: 'admin' | 'user'
  publishState: PublishState
  cards: { cardId: string; frontText: string; backText: string; hint: string | null; sortOrder: number }[]
}

export interface DeckRequest {
  title: string
  description?: string | null
  language?: string
  subjectSlug?: string | null
  cards: { frontText: string; backText: string; hint?: string | null; sortOrder?: number | null }[]
}

export async function fetchQuizzes(status?: string): Promise<Paged<QuizAdminRow>> {
  const qs = status ? `?status=${encodeURIComponent(status)}&size=50` : '?size=50'
  return api<QuizAdminRow[]>(`/admin/assessments/quizzes${qs}`, { raw: true }) as Promise<Paged<QuizAdminRow>>
}

export async function fetchQuiz(quizId: string): Promise<QuizAdminDetail> {
  return apiData<QuizAdminDetail>(`/admin/assessments/quizzes/${encodeURIComponent(quizId)}`)
}

export async function createQuiz(body: QuizRequest): Promise<QuizAdminDetail> {
  return (await api<QuizAdminDetail>('/admin/assessments/quizzes', { method: 'POST', body })).data
}

export async function updateQuiz(quizId: string, body: QuizRequest): Promise<void> {
  await api(`/admin/assessments/quizzes/${encodeURIComponent(quizId)}`, { method: 'PATCH', body })
}

export async function transitionQuiz(quizId: string, action: string, notes?: string): Promise<void> {
  await api(`/admin/assessments/quizzes/${encodeURIComponent(quizId)}/${action}`, {
    method: 'POST',
    body: notes ? { notes } : undefined,
  })
}

export async function createQuestion(quizId: string, body: QuestionRequest): Promise<void> {
  await api(`/admin/assessments/quizzes/${encodeURIComponent(quizId)}/questions`, { method: 'POST', body })
}

export async function updateQuestion(questionId: string, body: QuestionRequest): Promise<void> {
  await api(`/admin/assessments/questions/${encodeURIComponent(questionId)}`, { method: 'PATCH', body })
}

export async function submitQuestionReview(questionId: string): Promise<void> {
  await api(`/admin/assessments/questions/${encodeURIComponent(questionId)}/submit-review`, { method: 'POST' })
}

export async function decideQuestion(questionId: string, approve: boolean, notes?: string): Promise<void> {
  await api(`/admin/assessments/questions/${encodeURIComponent(questionId)}/decision`, {
    method: 'POST',
    body: { approve, notes: notes ?? null },
  })
}

export async function fetchQuestionVersions(questionId: string): Promise<QuestionVersionView[]> {
  return apiData<QuestionVersionView[]>(`/admin/assessments/questions/${encodeURIComponent(questionId)}/versions`)
}

export async function fetchAdminDecks(): Promise<Paged<DeckRow>> {
  return api<DeckRow[]>('/admin/assessments/flashcards/decks?size=50', { raw: true }) as Promise<Paged<DeckRow>>
}

export async function createAdminDeck(body: DeckRequest): Promise<DeckDetail> {
  return (await api<DeckDetail>('/admin/assessments/flashcards/decks', { method: 'POST', body })).data
}

export async function patchAdminDeck(deckId: string, body: Partial<DeckRequest> & { publishState?: string }): Promise<void> {
  await api(`/admin/assessments/flashcards/decks/${encodeURIComponent(deckId)}`, { method: 'PATCH', body })
}
