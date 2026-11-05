import { api, apiData } from './client'

/**
 * M4 admin content CMS contracts (imports, integrity, editions, hadith
 * collections, dictionary, duas, calendar, daily slots, recitations). All
 * paths are relative to the `/api/v1` base and follow the approved envelope +
 * problem+json conventions handled by `client.ts`.
 */

/** Stable hadith collection codes accepted by the import endpoint. */
export const HADITH_COLLECTION_CODES = ['bukhari', 'muslim', 'abudawud', 'tirmidhi', 'nasai', 'ibnmajah'] as const
export type HadithCollectionCode = (typeof HADITH_COLLECTION_CODES)[number]

/** Edition publish states shared by editions and collections. */
export type EditionPublishState = 'draft' | 'approved' | 'published' | 'disabled'

/** Daily-content workflow: draft → in_editorial_review → approved → published → archived. */
export type DailyStatus = 'draft' | 'in_editorial_review' | 'approved' | 'published' | 'archived'

/** Scholarly QA verdict for hadith collections. */
export type ScholarlyQaStatus = 'pending' | 'passed'

// ----- imports & integrity -----

export interface ContentImportRecord {
  id: string
  source_register_id: string
  content_kind: string
  edition_code: string | null
  status: string
  integrity_status: string
  records_imported: number
  imported_at: string
}

export interface IntegrityCheck {
  name: string
  expected: string
  actual: string
  status: string
  detail: string
}

export interface IntegrityReport {
  passed: number
  failed: number
  checks: IntegrityCheck[]
}

/** Post-import integrity summary (full checks come from the dedicated endpoint). */
export interface ImportIntegrity {
  passed?: number
  failed?: number
  checks?: IntegrityCheck[]
}

export interface ContentImportResult {
  batchId: string
  kind: string
  sourceRegisterId: string
  recordsImported: number
  integrity?: ImportIntegrity
}

export interface CollectionsRegistration {
  registered: number
}

// ----- editions -----

export interface QuranEdition {
  id: string
  code: string
  language: string
  displayName: string
  translator: string | null
  sourceRegisterId: string
  licenceId: number | null
  isFixture: boolean
  publishState: string
}

export interface QuranEditionPatch {
  displayName?: string
  translator?: string
  publishState?: EditionPublishState
}

export interface TafsirEdition {
  id: string
  code: string
  nameEn: string
  nameAr: string | null
  author: string | null
  sourceRegisterId: string
  licenceId: number | null
  isFixture: boolean
  publishState: string
}

export interface TafsirEditionPatch {
  nameEn?: string
  author?: string
  publishState?: EditionPublishState
}

// ----- hadith collections -----

export interface HadithCollection {
  publishState?: string
  code: string
  nameEn: string
  nameAr: string
  scholarlyQaStatus: string
  hadithCount: number
  bookCount: number
  editionNote: string | null
  numberingNote: string | null
}

export interface HadithCollectionPatch {
  scholarlyQaStatus?: ScholarlyQaStatus
  publishState?: EditionPublishState
  editionNote?: string
  numberingNote?: string
}

// ----- dictionary / duas / calendar -----

export interface DictionaryEntry {
  id: string
  headwordAr: string
  meaningEn: string | null
  isFixture: boolean
  publishState: string
}

export interface DictionaryEntryPatch {
  meaningEn?: string
  meaningUr?: string
  meaningHi?: string
  publishState?: EditionPublishState
}

export interface DuaEntry {
  id: string
  title: string
  textAr: string
  meaningEn: string | null
  isFixture: boolean
  categoryCode: string
  categoryName: string
  publishState: string
}

export interface DuaPatch {
  title?: string
  meaningEn?: string
  publishState?: EditionPublishState
}

export interface CalendarEvent {
  code: string
  nameEn: string
  hijriMonth: number
  hijriDay: number
  basis: string
  isMajor: boolean
  publishState: string
}

export interface CalendarEventPatch {
  nameEn?: string
  hijriMonth?: number
  hijriDay?: number
  publishState?: EditionPublishState
}

// ----- daily content -----

export interface DailyContentItem {
  id: string
  slotCode: string
  publishDate: string | null
  contentType: string
  status: string
  attribution: string | null
  createdAt: string
}

export interface DailyContentRequest {
  slotCode: string
  contentType: string
  publishDate?: string
  rotationWeight?: number
  ayahSurahId?: number
  ayahNumber?: number
  hadithId?: string
  duaId?: string
  eventCode?: string
  customText?: string
  attribution?: string
}

export interface DailyItemPatch {
  status?: DailyStatus
  customText?: string
  attribution?: string
  publishDate?: string
}

// ----- recitations -----

export interface RecitationRequest {
  code: string
  reciterName: string
  style?: string
}

// ----- endpoints -----

/** Latest 20 import batches with their integrity verdicts. */
export async function fetchImportHistory(): Promise<ContentImportRecord[]> {
  return apiData<ContentImportRecord[]>('/admin/content/imports?page=1&size=20')
}

export async function fetchIntegrityReport(): Promise<IntegrityReport> {
  return apiData<IntegrityReport>('/admin/content/integrity')
}

export async function importQuran(): Promise<ContentImportResult> {
  return (await api<ContentImportResult>('/admin/content/import/quran', { method: 'POST' })).data
}

export async function registerSourceCollections(): Promise<CollectionsRegistration> {
  return (await api<CollectionsRegistration>('/admin/content/collections', { method: 'POST' })).data
}

export async function importHadithCollection(code: HadithCollectionCode): Promise<ContentImportResult> {
  return (await api<ContentImportResult>(`/admin/content/import/hadith/${code}`, { method: 'POST' })).data
}

export async function seedDevFixtures(): Promise<ContentImportResult> {
  return (await api<ContentImportResult>('/admin/content/fixtures', { method: 'POST' })).data
}

export async function fetchQuranEditions(): Promise<QuranEdition[]> {
  return apiData<QuranEdition[]>('/admin/content/quran/editions')
}

export async function updateQuranEdition(id: string, patch: QuranEditionPatch): Promise<void> {
  await api(`/admin/content/quran/editions/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch })
}

export async function fetchTafsirEditions(): Promise<TafsirEdition[]> {
  return apiData<TafsirEdition[]>('/admin/content/tafsir/editions')
}

export async function updateTafsirEdition(id: string, patch: TafsirEditionPatch): Promise<void> {
  await api(`/admin/content/tafsir/editions/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch })
}

export async function fetchHadithCollections(): Promise<HadithCollection[]> {
  return apiData<HadithCollection[]>('/hadith/collections')
}

export async function updateHadithCollection(code: string, patch: HadithCollectionPatch): Promise<void> {
  await api(`/admin/content/hadith-collections/${encodeURIComponent(code)}`, { method: 'PATCH', body: patch })
}

export async function searchDictionary(term: string): Promise<DictionaryEntry[]> {
  return apiData<DictionaryEntry[]>(`/dictionary/search?q=${encodeURIComponent(term)}&limit=20`)
}

export async function updateDictionaryEntry(id: string, patch: DictionaryEntryPatch): Promise<void> {
  await api(`/admin/content/dictionary/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch })
}

export async function fetchDuas(): Promise<DuaEntry[]> {
  return apiData<DuaEntry[]>('/duas?limit=50')
}

export async function updateDua(id: string, patch: DuaPatch): Promise<void> {
  await api(`/admin/content/duas/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch })
}

export async function fetchCalendarEvents(): Promise<CalendarEvent[]> {
  return apiData<CalendarEvent[]>('/calendar/events')
}

export async function updateCalendarEvent(code: string, patch: CalendarEventPatch): Promise<void> {
  await api(`/admin/content/calendar/events/${encodeURIComponent(code)}`, { method: 'PATCH', body: patch })
}

export async function fetchDailyItems(): Promise<DailyContentItem[]> {
  return apiData<DailyContentItem[]>('/admin/content/daily?size=50')
}

export async function createDailyItem(body: DailyContentRequest): Promise<void> {
  await api('/admin/content/daily', { method: 'POST', body })
}

export async function updateDailyItem(id: string, patch: DailyItemPatch): Promise<void> {
  await api(`/admin/content/daily/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch })
}

export async function addRecitation(body: RecitationRequest): Promise<void> {
  await api('/admin/content/recitations', { method: 'POST', body })
}
