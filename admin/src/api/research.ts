import { api, apiData } from './client'

/**
 * M5 Islamic Research (spec Module 11) + Scholar directory (spec Module 14)
 * admin contracts. Research articles carry the mandatory three-way
 * source_tier classification and citations are structurally verified
 * server-side (a citation pointing to nothing is refused). Scholar
 * qualifications and verification are recorded facts only — never generated.
 */

export type SourceTier = 'primary' | 'secondary' | 'editorial'
export type ArticleStatus =
  | 'draft'
  | 'in_editorial_review'
  | 'in_scholarly_review'
  | 'approved'
  | 'published'
  | 'archived'
export type VerificationStatus = 'unverified' | 'verified' | 'revoked'

export interface ResearchTopic {
  slug: string
  name: string
  nameAr: string | null
  description: string | null
  sortOrder: number
  status: string
}

export interface CitationBody {
  citationKind: 'quran_ayah' | 'hadith' | 'book' | 'research_article' | 'external'
  surahId?: number | null
  ayahNumber?: number | null
  hadithId?: number | null
  bookPublicId?: string | null
  articlePublicId?: string | null
  externalRef?: string | null
  quote?: string | null
  note?: string | null
  sortOrder?: number | null
}

export interface CitationView extends CitationBody {
  citationId: number
  citationKind: CitationBody['citationKind']
}

export interface ArticleRow {
  articleId: string
  title: string
  topic: { slug: string; name: string }
  sourceTier: SourceTier
  status: ArticleStatus
  language: string
  authorDisplayName: string | null
  updatedAt: string
}

export interface ArticleDetail {
  articleId: string
  title: string
  titleAr: string | null
  summary: string | null
  body: string
  language: string
  authorDisplayName: string | null
  sourceTier: SourceTier
  status: ArticleStatus
  topic: { slug: string; name: string }
  citations: CitationView[]
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

export interface ArticleRequest {
  topicSlug: string
  title: string
  titleAr?: string | null
  summary?: string | null
  body: string
  language?: string
  authorDisplayName?: string | null
  sourceTier: SourceTier
  citations?: CitationBody[]
}

export interface Paged<T> {
  data: T[]
  meta?: { page: number; size: number; totalElements: number; totalPages: number }
}

export interface Scholar {
  scholarId: string
  name: string
  nameAr: string | null
  biography: string | null
  qualifications: string | null
  areasOfExpertise: string | null
  verificationStatus: VerificationStatus
  verifiedBy: string | null
  verifiedAt: string | null
  linkedUserEmail: string | null
  createdAt: string
  updatedAt: string
}

export interface ScholarRequest {
  name: string
  nameAr?: string | null
  biography?: string | null
  qualifications?: string | null
  areasOfExpertise?: string | null
  photoMediaId?: string | null
  linkedUserPublicId?: string | null
}

// ---------------------------------------------------------------- research

export async function fetchTopics(): Promise<ResearchTopic[]> {
  return apiData<ResearchTopic[]>('/admin/research/topics')
}

export async function fetchArticles(status?: string): Promise<Paged<ArticleRow>> {
  const qs = status ? `?status=${encodeURIComponent(status)}&size=50` : '?size=50'
  return api<ArticleRow[]>(`/admin/research/articles${qs}`, { raw: true }) as Promise<Paged<ArticleRow>>
}

export async function fetchArticle(articleId: string): Promise<ArticleDetail> {
  return apiData<ArticleDetail>(`/admin/research/articles/${encodeURIComponent(articleId)}`)
}

export async function createArticle(body: ArticleRequest): Promise<ArticleDetail> {
  return (await api<ArticleDetail>('/admin/research/articles', { method: 'POST', body })).data
}

export async function updateArticle(articleId: string, body: ArticleRequest): Promise<void> {
  await api(`/admin/research/articles/${encodeURIComponent(articleId)}`, { method: 'PUT', body })
}

/** submit-review | approve-editorial | approve | reject | publish | unpublish | archive */
export async function transitionArticle(articleId: string, action: string, notes?: string): Promise<void> {
  await api(`/admin/research/articles/${encodeURIComponent(articleId)}/${action}`, {
    method: 'POST',
    body: notes ? { notes } : undefined,
  })
}

// ---------------------------------------------------------------- scholars

export async function fetchScholars(): Promise<Scholar[]> {
  return apiData<Scholar[]>('/admin/scholars')
}

export async function createScholar(body: ScholarRequest): Promise<Scholar> {
  return (await api<Scholar>('/admin/scholars', { method: 'POST', body })).data
}

export async function updateScholar(scholarId: string, body: ScholarRequest): Promise<void> {
  await api(`/admin/scholars/${encodeURIComponent(scholarId)}`, { method: 'PUT', body })
}

/** status: verified | unverified | revoked — audited, and never a generated claim. */
export async function verifyScholar(scholarId: string, status: VerificationStatus): Promise<void> {
  await api(`/admin/scholars/${encodeURIComponent(scholarId)}/verify`, { method: 'POST', body: { status } })
}
