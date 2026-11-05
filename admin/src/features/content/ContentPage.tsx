import { useCallback, useEffect, useState } from 'react'
import {
  fetchCalendarEvents,
  fetchDailyItems,
  fetchDuas,
  fetchHadithCollections,
  fetchImportHistory,
  fetchIntegrityReport,
  fetchQuranEditions,
  fetchTafsirEditions,
  importHadithCollection,
  importQuran,
  registerSourceCollections,
  searchDictionary,
  seedDevFixtures,
  updateCalendarEvent,
  updateDailyItem,
  updateDictionaryEntry,
  updateDua,
  updateHadithCollection,
  updateQuranEdition,
  updateTafsirEdition,
  createDailyItem,
} from '../../api/content'
import type {
  CalendarEvent,
  ContentImportRecord,
  ContentImportResult,
  DailyContentItem,
  DailyContentRequest,
  DailyStatus,
  DictionaryEntry,
  EditionPublishState,
  HadithCollectionCode,
  HadithCollectionPatch,
  IntegrityReport,
  QuranEdition,
  ScholarlyQaStatus,
  TafsirEdition,
} from '../../api/content'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Select,
  Spinner,
  StatusPill,
  useToast,
} from '../../components/ui'

/**
 * M4 content CMS: imports & integrity, Quran/tafsir editions, hadith
 * collections, dictionary, duas, calendar events and daily slots. The server
 * stays the authority for every state change; 403s surface a "content.edit
 * required" hint instead of being swallowed.
 */

const PUBLISH_STATES: EditionPublishState[] = ['draft', 'approved', 'published', 'disabled']

const HADITH_IMPORTS: { code: HadithCollectionCode; label: string }[] = [
  { code: 'bukhari', label: 'Import Bukhari' },
  { code: 'muslim', label: 'Import Muslim' },
  { code: 'abudawud', label: 'Import Abu Dawud' },
  { code: 'tirmidhi', label: 'Import Tirmidhi' },
  { code: 'nasai', label: 'Import Nasai' },
  { code: 'ibnmajah', label: 'Import Ibn Majah' },
]

const DAILY_SLOTS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha', 'event']

const DAILY_CONTENT_TYPES = ['ayah', 'hadith', 'dua', 'event', 'custom']

/** Only the next allowed workflow step is offered per row (disabled otherwise). */
const DAILY_NEXT: Partial<Record<string, { status: DailyStatus; label: string }>> = {
  draft: { status: 'in_editorial_review', label: 'Start review' },
  in_editorial_review: { status: 'approved', label: 'Approve' },
  approved: { status: 'published', label: 'Publish' },
}

const TABS = [
  { key: 'imports', label: 'Imports & Integrity' },
  { key: 'quran', label: 'Quran editions' },
  { key: 'tafsir', label: 'Tafsir editions' },
  { key: 'hadith', label: 'Hadith collections' },
  { key: 'dictionary', label: 'Dictionary' },
  { key: 'duas', label: 'Duas' },
  { key: 'calendar', label: 'Calendar' },
  { key: 'daily', label: 'Daily content' },
] as const

type TabKey = (typeof TABS)[number]['key']

// ---------- shared tab plumbing ----------

/** HTTP status from an ApiError-like error (client.ts throws ApiError with .status). */
function statusOf(e: unknown): number | null {
  if (typeof e === 'object' && e !== null && 'status' in e) {
    const s = (e as { status?: unknown }).status
    if (typeof s === 'number') return s
  }
  return null
}

function errorMessage(fallback: string, e: unknown): string {
  return e instanceof Error ? e.message : fallback
}

/** Fetch-state helper shared by the tabs: loading, error and 403 handling. */
function useResource<T>(loader: () => Promise<T>, deps: readonly unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [errorStatus, setErrorStatus] = useState<number | null>(null)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    setErrorStatus(null)
    loader()
      .then((v) => {
        if (!cancelled) setData(v)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        setError(errorMessage(String(e), e))
        setErrorStatus(statusOf(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  return { data, loading, error, errorStatus, reload }
}

function ForbiddenHint() {
  return (
    <div role="alert" className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      <span className="font-medium">content.edit required</span> — the server refused this request (HTTP 403). Ask a
      Super Admin to grant your account the content.edit permission.
    </div>
  )
}

function TabError({ message, status, onRetry }: { message: string; status: number | null; onRetry: () => void }) {
  return (
    <div className="space-y-3">
      {status === 403 && <ForbiddenHint />}
      <ErrorState message={message} onRetry={onRetry} />
    </div>
  )
}

/** Amber badge for development-placeholder rows (never production data). */
function FixtureBadge() {
  return (
    <span className="inline-block whitespace-nowrap rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold tracking-wide text-amber-800">
      FIXTURE
    </span>
  )
}

function IntegrityStatusChip({ status }: { status: string }) {
  const s = status.toLowerCase()
  const tone =
    s === 'pass' || s === 'passed' || s === 'verified' || s === 'clean'
      ? 'bg-emerald-100 text-emerald-800'
      : s === 'fail' || s === 'failed' || s === 'mismatch' || s === 'broken'
        ? 'bg-red-100 text-red-800'
        : 'bg-gray-100 text-gray-600'
  return (
    <span className={`inline-block whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${tone}`}>
      {status}
    </span>
  )
}

function PublishStateSelect({
  value,
  label,
  busy,
  onChange,
}: {
  value: string
  label: string
  busy?: boolean
  onChange: (next: EditionPublishState) => void
}) {
  return (
    <Select
      aria-label={label}
      className="text-xs"
      value={value}
      disabled={busy}
      onChange={(e) => onChange(e.target.value as EditionPublishState)}
    >
      {PUBLISH_STATES.map((s) => (
        <option key={s} value={s}>
          {s}
        </option>
      ))}
    </Select>
  )
}

/** Surfaces mutation errors as toasts; 403 gets the dedicated permission hint. */
function useMutationError() {
  const toast = useToast()
  return useCallback(
    (fallback: string, e: unknown) => {
      if (statusOf(e) === 403) toast.push('error', 'content.edit required — the change was refused (HTTP 403)')
      else toast.push('error', errorMessage(fallback, e))
    },
    [toast],
  )
}

// ---------- a) Imports & Integrity ----------

function ImportsTab() {
  const toast = useToast()
  const onError = useMutationError()
  const [report, setReport] = useState<IntegrityReport | null>(null)
  const [history, setHistory] = useState<ContentImportRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [errorStatus, setErrorStatus] = useState<number | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setErrorStatus(null)
    try {
      const [integrity, imports] = await Promise.all([fetchIntegrityReport(), fetchImportHistory()])
      setReport(integrity)
      setHistory(Array.isArray(imports) ? imports : [])
    } catch (e) {
      setError(errorMessage(String(e), e))
      setErrorStatus(statusOf(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const runImport = async (key: string, label: string, run: () => Promise<ContentImportResult>) => {
    setBusy(key)
    try {
      const res = await run()
      const integrityNote = res.integrity
        ? ` · integrity ${res.integrity.passed ?? '?'} passed / ${res.integrity.failed ?? '?'} failed`
        : ''
      toast.push('success', `${label}: ${res.recordsImported} records imported${integrityNote}`)
      await load()
    } catch (e) {
      onError(`${label} failed`, e)
    } finally {
      setBusy(null)
    }
  }

  const registerCollections = async () => {
    setBusy('collections')
    try {
      await registerSourceCollections()
      toast.push('success', 'Source collections registered (audited)')
      await load()
    } catch (e) {
      onError('Register collections failed', e)
    } finally {
      setBusy(null)
    }
  }

  if (loading) return <Spinner label="Loading imports…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={load} />

  return (
    <div className="space-y-5">
      <Card>
        <h2 className="font-medium text-gray-900">Run imports</h2>
        <p className="mt-1 text-xs text-gray-500">
          Imports run from the approved source registers and land in the history below with an integrity verdict.
          Every action is audited.
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button disabled={busy !== null} onClick={() => runImport('quran', 'Import Quran (SR-01)', importQuran)}>
            {busy === 'quran' ? 'Importing…' : 'Import Quran (SR-01)'}
          </Button>
          <Button variant="secondary" disabled={busy !== null} onClick={registerCollections}>
            {busy === 'collections' ? 'Registering…' : 'Register collections'}
          </Button>
          {HADITH_IMPORTS.map((h) => (
            <Button
              key={h.code}
              variant="secondary"
              disabled={busy !== null}
              onClick={() => runImport(h.code, h.label, () => importHadithCollection(h.code))}
            >
              {busy === h.code ? 'Importing…' : h.label}
            </Button>
          ))}
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
          <Button
            variant="danger"
            disabled={busy !== null}
            onClick={() => runImport('fixtures', 'Seed dev fixtures', seedDevFixtures)}
          >
            {busy === 'fixtures' ? 'Seeding…' : 'Seed dev fixtures'}
          </Button>
          <span className="text-xs font-medium text-red-700">
            Fixtures are labelled development placeholders — they must never be presented as production data.
          </span>
        </div>
      </Card>

      <div>
        <h2 className="mb-2 font-medium text-gray-900">Integrity report</h2>
        {report && (
          <p className="mb-2 text-xs text-gray-500">
            {report.passed} passed · {report.failed} failed
          </p>
        )}
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Check</th>
                <th className="px-4 py-3">Expected</th>
                <th className="px-4 py-3">Actual</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Detail</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(report?.checks ?? []).map((c) => (
                <tr key={c.name} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">{c.name}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{c.expected}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{c.actual}</td>
                  <td className="px-4 py-3">
                    <IntegrityStatusChip status={c.status} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{c.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {report && report.checks.length === 0 && (
            <p className="px-4 py-3 text-sm text-gray-500">No integrity checks registered.</p>
          )}
        </Card>
      </div>

      <div>
        <h2 className="mb-2 font-medium text-gray-900">Import history (latest 20)</h2>
        {history.length === 0 ? (
          <p className="text-sm text-gray-500">No imports recorded yet.</p>
        ) : (
          <Card className="overflow-x-auto p-0">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                  <th className="px-4 py-3">Batch</th>
                  <th className="px-4 py-3">Kind</th>
                  <th className="px-4 py-3">Edition</th>
                  <th className="px-4 py-3">Source register</th>
                  <th className="px-4 py-3">Records</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Integrity</th>
                  <th className="px-4 py-3">Imported at</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {history.map((r) => (
                  <tr key={r.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{r.id}</td>
                    <td className="px-4 py-3 text-xs text-gray-600">{r.content_kind}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">{r.edition_code || '—'}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{r.source_register_id}</td>
                    <td className="px-4 py-3 text-xs text-gray-600">{r.records_imported}</td>
                    <td className="px-4 py-3">
                      <StatusPill status={r.status} />
                    </td>
                    <td className="px-4 py-3">
                      <IntegrityStatusChip status={r.integrity_status} />
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">{new Date(r.imported_at).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}
      </div>
    </div>
  )
}

// ---------- b) Quran editions ----------

function QuranEditionsTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchQuranEditions, [])
  const [busyId, setBusyId] = useState<string | null>(null)
  const editions = data ?? []

  const changeState = async (edition: QuranEdition, next: EditionPublishState) => {
    if (next === edition.publishState) return
    setBusyId(edition.id)
    try {
      await updateQuranEdition(edition.id, { publishState: next })
      toast.push('success', `Publish state of “${edition.code}” set to ${next} (audited)`)
      reload()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <Spinner label="Loading Quran editions…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-3">
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Code</th>
              <th className="px-4 py-3">Language</th>
              <th className="px-4 py-3">Display name</th>
              <th className="px-4 py-3">Translator</th>
              <th className="px-4 py-3">Source</th>
              <th className="px-4 py-3">Publish state</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {editions.map((ed) => (
              <tr key={ed.id} className="hover:bg-gray-50">
                <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-gray-700">
                  {ed.code} {ed.isFixture && <FixtureBadge />}
                </td>
                <td className="px-4 py-3 text-xs uppercase text-gray-600">{ed.language}</td>
                <td className="px-4 py-3 text-sm text-gray-800">{ed.displayName}</td>
                <td className="px-4 py-3 text-xs text-gray-600">{ed.translator || '—'}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{ed.sourceRegisterId}</td>
                <td className="px-4 py-3">
                  <PublishStateSelect
                    value={ed.publishState}
                    label={`Publish state of ${ed.code}`}
                    busy={busyId === ed.id}
                    onChange={(next) => changeState(ed, next)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {editions.length === 0 && <p className="px-4 py-3 text-sm text-gray-500">No Quran editions registered.</p>}
      </Card>
      <p className="text-xs text-gray-400">
        Rows marked FIXTURE are development placeholders. Publishing a fixture is blocked by the server whenever the
        platform runs with allow-fixtures=false.
      </p>
    </div>
  )
}

// ---------- c) Tafsir editions ----------

function TafsirEditionsTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchTafsirEditions, [])
  const [busyId, setBusyId] = useState<string | null>(null)
  const editions = data ?? []

  const changeState = async (edition: TafsirEdition, next: EditionPublishState) => {
    if (next === edition.publishState) return
    setBusyId(edition.id)
    try {
      await updateTafsirEdition(edition.id, { publishState: next })
      toast.push('success', `Publish state of “${edition.code}” set to ${next} (audited)`)
      reload()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <Spinner label="Loading tafsir editions…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-3">
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Code</th>
              <th className="px-4 py-3">Name</th>
              <th className="px-4 py-3">Author</th>
              <th className="px-4 py-3">Source</th>
              <th className="px-4 py-3">Publish state</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {editions.map((ed) => (
              <tr key={ed.id} className="hover:bg-gray-50">
                <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-gray-700">
                  {ed.code} {ed.isFixture && <FixtureBadge />}
                </td>
                <td className="px-4 py-3">
                  <p className="text-sm text-gray-800">{ed.nameEn}</p>
                  {ed.nameAr && <p className="text-xs text-gray-400">{ed.nameAr}</p>}
                </td>
                <td className="px-4 py-3 text-xs text-gray-600">{ed.author || '—'}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{ed.sourceRegisterId}</td>
                <td className="px-4 py-3">
                  <PublishStateSelect
                    value={ed.publishState}
                    label={`Publish state of ${ed.code}`}
                    busy={busyId === ed.id}
                    onChange={(next) => changeState(ed, next)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {editions.length === 0 && <p className="px-4 py-3 text-sm text-gray-500">No tafsir editions registered.</p>}
      </Card>
      <p className="text-xs text-gray-400">
        Rows marked FIXTURE are development placeholders. Publishing a fixture is blocked by the server whenever the
        platform runs with allow-fixtures=false.
      </p>
    </div>
  )
}

// ---------- d) Hadith collections ----------

function HadithCollectionsTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchHadithCollections, [])
  const [busyCode, setBusyCode] = useState<string | null>(null)
  const collections = data ?? []

  const patch = async (code: string, body: HadithCollectionPatch, okMessage: string) => {
    setBusyCode(code)
    try {
      await updateHadithCollection(code, body)
      toast.push('success', okMessage)
      reload()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyCode(null)
    }
  }

  if (loading) return <Spinner label="Loading hadith collections…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-3">
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[860px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Collection</th>
              <th className="px-4 py-3">Size</th>
              <th className="px-4 py-3">Scholarly QA</th>
              <th className="px-4 py-3">Publish state</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {collections.map((c) => (
              <tr key={c.code} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="text-sm text-gray-800">
                    <span className="font-mono text-xs text-gray-500">{c.code}</span> · {c.nameEn}
                    <span className="ml-2 text-xs text-gray-400">{c.nameAr}</span>
                  </p>
                  {c.editionNote && <p className="mt-0.5 text-xs text-gray-500">Edition: {c.editionNote}</p>}
                  {c.numberingNote && <p className="text-xs text-gray-500">Numbering: {c.numberingNote}</p>}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-600">
                  {c.hadithCount} hadiths · {c.bookCount} books
                </td>
                <td className="px-4 py-3">
                  <Select
                    aria-label={`Scholarly QA status of ${c.code}`}
                    className="text-xs"
                    value={c.scholarlyQaStatus}
                    disabled={busyCode === c.code}
                    onChange={(e) =>
                      patch(
                        c.code,
                        { scholarlyQaStatus: e.target.value as ScholarlyQaStatus },
                        `Scholarly QA of ${c.code} set to ${e.target.value} (audited)`,
                      )
                    }
                  >
                    <option value="pending">pending</option>
                    <option value="passed">passed</option>
                  </Select>
                </td>
                <td className="px-4 py-3">
                  <PublishStateSelect
                    value={c.publishState ?? 'draft'}
                    label={`Publish state of ${c.code}`}
                    busy={busyCode === c.code}
                    onChange={(next) =>
                      patch(c.code, { publishState: next }, `Publish state of ${c.code} set to ${next} (audited)`)
                    }
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {collections.length === 0 && <p className="px-4 py-3 text-sm text-gray-500">No collections registered.</p>}
      </Card>
      <p className="text-xs text-gray-400">
        Edition and numbering notes are read-only reference metadata; the scholarly QA verdict gates publication.
      </p>
    </div>
  )
}

// ---------- e) Dictionary ----------

function DictionaryTab() {
  const toast = useToast()
  const onError = useMutationError()
  const [term, setTerm] = useState('')
  const [rows, setRows] = useState<DictionaryEntry[]>([])
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errorStatus, setErrorStatus] = useState<number | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const runSearch = useCallback(async () => {
    const q = term.trim()
    if (!q) return
    setLoading(true)
    setError(null)
    setErrorStatus(null)
    try {
      setRows(await searchDictionary(q))
      setSearched(true)
    } catch (e) {
      setError(errorMessage(String(e), e))
      setErrorStatus(statusOf(e))
    } finally {
      setLoading(false)
    }
  }, [term])

  const changeState = async (entry: DictionaryEntry, next: EditionPublishState) => {
    if (next === entry.publishState) return
    setBusyId(entry.id)
    try {
      await updateDictionaryEntry(entry.id, { publishState: next })
      toast.push('success', `Publish state of “${entry.headwordAr}” set to ${next} (audited)`)
      await runSearch()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-4">
      <Card className="flex flex-wrap items-end gap-2">
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="dict-q">
            Search headword
          </label>
          <Input
            id="dict-q"
            className="mt-1 w-72"
            value={term}
            placeholder="Arabic headword or transliteration"
            onChange={(e) => setTerm(e.target.value)}
          />
        </div>
        <Button variant="secondary" onClick={runSearch} disabled={loading || !term.trim()}>
          Search
        </Button>
      </Card>

      {loading ? (
        <Spinner label="Searching dictionary…" />
      ) : error ? (
        <TabError message={error} status={errorStatus} onRetry={runSearch} />
      ) : !searched ? (
        <p className="text-sm text-gray-500">Search the public dictionary to review and publish entries.</p>
      ) : rows.length === 0 ? (
        <EmptyState title="No entries match" hint="Try a different headword." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Headword</th>
                <th className="px-4 py-3">Meaning (EN)</th>
                <th className="px-4 py-3">Publish state</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((entry) => (
                <tr key={entry.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm text-gray-800">
                    {entry.headwordAr} {entry.isFixture && <FixtureBadge />}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{entry.meaningEn || '—'}</td>
                  <td className="px-4 py-3">
                    <PublishStateSelect
                      value={entry.publishState}
                      label={`Publish state of ${entry.headwordAr}`}
                      busy={busyId === entry.id}
                      onChange={(next) => changeState(entry, next)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}

// ---------- f) Duas ----------

function DuasTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchDuas, [])
  const [busyId, setBusyId] = useState<string | null>(null)
  const duas = data ?? []

  const changeState = async (dua: { id: string; title: string; publishState: string }, next: EditionPublishState) => {
    if (next === dua.publishState) return
    setBusyId(dua.id)
    try {
      await updateDua(dua.id, { publishState: next })
      toast.push('success', `Publish state of “${dua.title}” set to ${next} (audited)`)
      reload()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <Spinner label="Loading duas…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-3">
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Title</th>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3">Meaning (EN)</th>
              <th className="px-4 py-3">Publish state</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {duas.map((dua) => (
              <tr key={dua.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="text-sm text-gray-800">
                    {dua.title} {dua.isFixture && <FixtureBadge />}
                  </p>
                  <p className="text-xs text-gray-400" dir="rtl" lang="ar">
                    {dua.textAr}
                  </p>
                </td>
                <td className="px-4 py-3 text-xs text-gray-600">{dua.categoryName || dua.categoryCode || '—'}</td>
                <td className="px-4 py-3 text-xs text-gray-600">{dua.meaningEn || '—'}</td>
                <td className="px-4 py-3">
                  <PublishStateSelect
                    value={dua.publishState}
                    label={`Publish state of ${dua.title}`}
                    busy={busyId === dua.id}
                    onChange={(next) => changeState(dua, next)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {duas.length === 0 && <p className="px-4 py-3 text-sm text-gray-500">No duas available.</p>}
      </Card>
    </div>
  )
}

// ---------- g) Calendar ----------

function CalendarTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchCalendarEvents, [])
  const [busyCode, setBusyCode] = useState<string | null>(null)
  const events = data ?? []

  const changeState = async (event: CalendarEvent, next: EditionPublishState) => {
    if (next === event.publishState) return
    setBusyCode(event.code)
    try {
      await updateCalendarEvent(event.code, { publishState: next })
      toast.push('success', `Publish state of “${event.nameEn}” set to ${next} (audited)`)
      reload()
    } catch (e) {
      onError('Update failed', e)
    } finally {
      setBusyCode(null)
    }
  }

  if (loading) return <Spinner label="Loading calendar events…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-3">
      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Event</th>
              <th className="px-4 py-3">Hijri date</th>
              <th className="px-4 py-3">Basis</th>
              <th className="px-4 py-3">Publish state</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {events.map((ev) => (
              <tr key={ev.code} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="text-sm text-gray-800">
                    {ev.nameEn}
                    {ev.isMajor && (
                      <span className="ml-2 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700">
                        major
                      </span>
                    )}
                  </p>
                  <p className="font-mono text-xs text-gray-500">{ev.code}</p>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-xs text-gray-600">
                  month {ev.hijriMonth} · day {ev.hijriDay}
                </td>
                <td className="px-4 py-3 text-xs text-gray-600">{ev.basis || '—'}</td>
                <td className="px-4 py-3">
                  <PublishStateSelect
                    value={ev.publishState}
                    label={`Publish state of ${ev.nameEn}`}
                    busy={busyCode === ev.code}
                    onChange={(next) => changeState(ev, next)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {events.length === 0 && <p className="px-4 py-3 text-sm text-gray-500">No calendar events registered.</p>}
      </Card>
    </div>
  )
}

// ---------- h) Daily content ----------

function DailyTab() {
  const toast = useToast()
  const onError = useMutationError()
  const { data, loading, error, errorStatus, reload } = useResource(fetchDailyItems, [])
  const [form, setForm] = useState({
    slotCode: DAILY_SLOTS[0],
    contentType: DAILY_CONTENT_TYPES[0],
    attribution: '',
    customText: '',
  })
  const [creating, setCreating] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const items = data ?? []

  const create = async () => {
    setCreating(true)
    try {
      const body: DailyContentRequest = { slotCode: form.slotCode, contentType: form.contentType }
      if (form.attribution.trim()) body.attribution = form.attribution.trim()
      if (form.customText.trim()) body.customText = form.customText.trim()
      await createDailyItem(body)
      toast.push('success', 'Daily item created (audited)')
      setForm((f) => ({ ...f, attribution: '', customText: '' }))
      reload()
    } catch (e) {
      onError('Create failed', e)
    } finally {
      setCreating(false)
    }
  }

  const transition = async (item: DailyContentItem) => {
    const next = DAILY_NEXT[item.status]
    if (!next) return
    setBusyId(item.id)
    try {
      await updateDailyItem(item.id, { status: next.status })
      toast.push('success', `“${item.slotCode}” moved to ${next.status} (audited)`)
      reload()
    } catch (e) {
      onError('Transition failed', e)
    } finally {
      setBusyId(null)
    }
  }

  if (loading) return <Spinner label="Loading daily content…" />
  if (error) return <TabError message={error} status={errorStatus} onRetry={reload} />

  return (
    <div className="space-y-4">
      <Card>
        <h2 className="font-medium text-gray-900">New item</h2>
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <div>
            <label className="block text-xs font-medium text-gray-500" htmlFor="daily-slot">
              Slot
            </label>
            <Select
              id="daily-slot"
              className="mt-1"
              value={form.slotCode}
              onChange={(e) => setForm((f) => ({ ...f, slotCode: e.target.value }))}
            >
              {DAILY_SLOTS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500" htmlFor="daily-type">
              Content type
            </label>
            <Select
              id="daily-type"
              className="mt-1"
              value={form.contentType}
              onChange={(e) => setForm((f) => ({ ...f, contentType: e.target.value }))}
            >
              {DAILY_CONTENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500" htmlFor="daily-attribution">
              Attribution
            </label>
            <Input
              id="daily-attribution"
              className="mt-1 w-56"
              value={form.attribution}
              placeholder="e.g. Sahih al-Bukhari 1"
              onChange={(e) => setForm((f) => ({ ...f, attribution: e.target.value }))}
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500" htmlFor="daily-custom-text">
              Custom text
            </label>
            <Input
              id="daily-custom-text"
              className="mt-1 w-72"
              value={form.customText}
              placeholder="Optional custom copy"
              onChange={(e) => setForm((f) => ({ ...f, customText: e.target.value }))}
            />
          </div>
          <Button onClick={create} disabled={creating}>
            {creating ? 'Creating…' : 'Add item'}
          </Button>
        </div>
      </Card>

      {items.length === 0 ? (
        <EmptyState title="No daily items yet" hint="Create the first item with the form above." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Slot</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Publish date</th>
                <th className="px-4 py-3">Attribution</th>
                <th className="px-4 py-3">Next action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {items.map((item) => {
                const next = DAILY_NEXT[item.status]
                return (
                  <tr key={item.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-xs text-gray-700">{item.slotCode}</td>
                    <td className="px-4 py-3 text-xs text-gray-600">{item.contentType}</td>
                    <td className="px-4 py-3">
                      <StatusPill status={item.status} />
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">
                      {item.publishDate ? new Date(item.publishDate).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500">{item.attribution || '—'}</td>
                    <td className="px-4 py-3">
                      {next ? (
                        <Button
                          variant="secondary"
                          disabled={busyId === item.id}
                          onClick={() => transition(item)}
                        >
                          {next.label}
                        </Button>
                      ) : (
                        <span className="text-xs text-gray-400">—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Card>
      )}
      <p className="text-xs text-gray-400">
        Workflow: draft → in editorial review → approved → published (then archived). Only the next allowed
        transition is offered; every transition is audited.
      </p>
    </div>
  )
}

// ---------- page ----------

/** M4 content CMS — one page, eight content sections behind a tab bar. */
export default function ContentPage() {
  const [tab, setTab] = useState<TabKey>('imports')

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Content management</h1>
        <p className="text-sm text-gray-500">
          Imports and integrity, Quran/tafsir editions, hadith collections, dictionary, duas, calendar events and
          daily slots — every change is audited.
        </p>
      </div>

      <div role="tablist" aria-label="Content sections" className="flex flex-wrap gap-1.5">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            type="button"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${
              tab === t.key ? 'bg-emerald-deep text-white' : 'bg-white text-gray-600 hover:bg-gray-100'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'imports' && <ImportsTab />}
      {tab === 'quran' && <QuranEditionsTab />}
      {tab === 'tafsir' && <TafsirEditionsTab />}
      {tab === 'hadith' && <HadithCollectionsTab />}
      {tab === 'dictionary' && <DictionaryTab />}
      {tab === 'duas' && <DuasTab />}
      {tab === 'calendar' && <CalendarTab />}
      {tab === 'daily' && <DailyTab />}
    </div>
  )
}
