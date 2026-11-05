/** Shared API payload types (mirrors the approved M2 REST contracts). */

export interface MeInfo {
  userId: string
  email: string
  displayName: string
  language: string
  status: string
  roles: string[]
  permissions: string[]
  createdAt: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: { userId: string; displayName: string; roles: string[] }
}

export interface SessionInfo {
  sessionId: number
  deviceName: string | null
  createdAt: string
  expiresAt: string
}

// ----- dashboard (Module A) -----

export interface DashboardMetrics {
  users: { total: number; active: number; newRegistrations: number }
  content: {
    subjects: number
    books: number
    publishedBooks: number
    draftBooks: number
    publishedContent: number
    uploads: number
    courses: number
    researchArticles: number
    quranStats: { imported: boolean; note: string }
    hadithStats: { imported: boolean; note: string }
  }
  review: {
    pendingBookEditorial: number
    pendingBookScholarly: number
    pendingFatwaReview: number
    awaitingApproval: QueueItem[]
  }
  recentAdminActivity: ActivityRow[]
}

export interface QueueItem {
  entity: string
  entityId: string
  title: string
  state: string
  waitingDays: number
}

export interface ActivityRow {
  at: string
  action: string
  entity: string
  entityId: string | null
  actor: string | null
}

// ----- users (Module B) -----

export interface UserSummary {
  userId: string
  email: string
  displayName: string
  status: string
  roles: string[]
  registeredAt: string
  lastSeenAt: string | null
  deletionState: string
}

export interface DeletionRequestView {
  requestId: string
  state: string
  reason: string | null
  requestedAt: string
  purgeAt: string | null
}

export interface UserDetail extends UserSummary {
  deletionRequests: DeletionRequestView[]
}

export interface ActivityEntry {
  at: string
  action: string
  entityType: string
  entityId: string
}

// ----- subjects (Module C) -----

export interface SubjectView {
  subjectId: string
  slug: string
  name: string
  nameAr: string | null
  description: string | null
  imageUrl: string | null
  position: number
  status: string
  updatedAt: string
}

// ----- books (Module D) -----

export interface AuthorRef {
  authorId: string
  name: string
}

export interface AuthorView {
  authorId: string
  name: string
  nameAr: string | null
  bio: string | null
}

export interface BookFileRef {
  fileId: string
  format: string
  mime: string
  sizeBytes: number
  downloadAllowed: boolean
  scanStatus: string
}

export interface BookAdminView {
  bookId: string
  title: string
  subtitle: string | null
  description: string | null
  language: string
  subject: { slug: string; name: string }
  authors: AuthorRef[]
  publisher: string | null
  edition: string | null
  publicationYear: number | null
  pageCount: number | null
  isbn: string | null
  copyrightInfo: string | null
  licence: { licenceId: number; code: string; title: string; downloadAllowedByLicence: boolean } | null
  downloadAllowed: boolean
  scholarlyReviewRequired: boolean
  status: string
  cover: BookFileRef | null
  files: BookFileRef[]
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

export interface BookRequest {
  title: string
  subtitle?: string
  description?: string
  language: string
  subjectId: string
  authorIds?: string[]
  publisher?: string
  edition?: string
  publicationYear?: number
  pageCount?: number
  isbn?: string
  copyrightInfo?: string
  licenceId?: number | null
  downloadAllowed?: boolean
  scholarlyReviewRequired?: boolean
  coverMediaId?: string | null
}

export interface Licence {
  id: number
  code: string
  title: string
  downloadAllowedByLicence: boolean
}

// ----- files (Module E/uploads) -----

export interface MediaView {
  mediaId: string
  purpose: string | null
  originalFilename: string | null
  mime: string
  sizeBytes: number | null
  scanStatus: string
  scanner: string | null
  createdAt: string
}

// ----- review workflow (Module F) -----

export interface ReviewQueueRow {
  entity: string
  entityId: string
  title: string
  state: string
  subject?: string
  waitingDays?: number
  note?: string
  stage?: string
  decidedAt?: string
}

export interface ReviewRecordRow {
  stage: string
  decision: string
  notes: string
  decidedAt: string
}

// ----- fatwas -----

export interface FatwaView {
  fatwaId: string
  question: string
  category: string | null
  language: string
  state: string
  version: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  answer?: string
}

export interface FatwaAuditRow {
  at: string
  action: string
  actor: string | null
  notes?: string
}

// ----- audit (Module G) -----

export interface AuditRow {
  id: number
  at: string
  actorId: number | null
  action: string
  entityType: string
  entityId: number | null
  beforeHash: string | null
  afterHash: string | null
  ip: string | null
  userAgent: string | null
}
