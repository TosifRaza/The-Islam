import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, apiData } from '../../api/client'
import type { AuthorView, BookAdminView, BookRequest, Licence, MediaView, SubjectView } from '../../api/types'
import { ApiError } from '../../api/client'
import { Button, Card, ErrorState, Field, Input, Select, Spinner, Textarea, useToast } from '../../components/ui'

const LANGS = [
  ['en', 'English'],
  ['ar', 'العربية (Arabic)'],
  ['ur', 'اردو (Urdu)'],
  ['hi', 'हिन्दी (Hindi)'],
]

const SCHOLARLY_SUBJECTS = ['cat_quran_tafsir', 'cat_hadith', 'cat_fiqh', 'cat_aqeedah']

/** Create/edit book metadata (Module D). Files are attached on the detail page after upload. */
export default function BookFormPage() {
  const { bookId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const editing = !!bookId

  const [subjects, setSubjects] = useState<SubjectView[]>([])
  const [authors, setAuthors] = useState<AuthorView[]>([])
  const [licences, setLicences] = useState<Licence[]>([])
  const [covers, setCovers] = useState<MediaView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const [form, setForm] = useState({
    title: '',
    subtitle: '',
    description: '',
    language: 'en',
    subjectId: '',
    authorIds: [] as string[],
    publisher: '',
    edition: '',
    publicationYear: '',
    pageCount: '',
    isbn: '',
    copyrightInfo: '',
    licenceId: '',
    downloadAllowed: false,
    scholarlyReviewRequired: false,
    coverMediaId: '',
  })
  const [newAuthor, setNewAuthor] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [subs, auths, lics] = await Promise.all([
        apiData<SubjectView[]>('/admin/subjects'),
        apiData<AuthorView[]>('/admin/authors').catch(() => [] as AuthorView[]),
        apiData<Licence[]>('/licences').catch(() => [] as Licence[]),
      ])
      setSubjects(subs)
      setAuthors(auths)
      setLicences(lics)
      try {
        const media = await apiData<MediaView[]>('/admin/files?purpose=cover&size=100')
        setCovers((media || []).filter((m) => m.scanStatus === 'clean'))
      } catch {
        setCovers([])
      }
      if (bookId) {
        const book = await apiData<BookAdminView>(`/admin/books/${bookId}`)
        setForm({
          title: book.title,
          subtitle: book.subtitle || '',
          description: book.description || '',
          language: book.language,
          subjectId: book.subject.slug,
          authorIds: book.authors.map((a) => a.authorId),
          publisher: book.publisher || '',
          edition: book.edition || '',
          publicationYear: book.publicationYear ? String(book.publicationYear) : '',
          pageCount: book.pageCount ? String(book.pageCount) : '',
          isbn: book.isbn || '',
          copyrightInfo: book.copyrightInfo || '',
          licenceId: book.licence ? String(book.licence.licenceId) : '',
          downloadAllowed: book.downloadAllowed,
          scholarlyReviewRequired: book.scholarlyReviewRequired,
          coverMediaId: book.cover?.fileId || '',
        })
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [bookId])

  useEffect(() => {
    load()
  }, [load])

  const validate = (): boolean => {
    const errs: Record<string, string> = {}
    if (!form.title.trim()) errs.title = 'Title is required'
    else if (form.title.length > 300) errs.title = 'Max 300 characters'
    if (!form.subjectId) errs.subjectId = 'Choose a category'
    if (!['en', 'ar', 'ur', 'hi'].includes(form.language)) errs.language = 'Choose a language'
    if (form.publicationYear && !/^\d{4}$/.test(form.publicationYear)) errs.publicationYear = 'Year must be 4 digits (1000–2100)'
    if (form.pageCount && (!/^\d+$/.test(form.pageCount) || Number(form.pageCount) < 1)) errs.pageCount = 'Page count must be a positive number'
    if (form.isbn && form.isbn.length > 32) errs.isbn = 'Max 32 characters'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return
    setBusy(true)
    const body: BookRequest = {
      title: form.title.trim(),
      subtitle: form.subtitle || undefined,
      description: form.description || undefined,
      language: form.language,
      subjectId: form.subjectId,
      authorIds: form.authorIds.length > 0 ? form.authorIds : undefined,
      publisher: form.publisher || undefined,
      edition: form.edition || undefined,
      publicationYear: form.publicationYear ? Number(form.publicationYear) : undefined,
      pageCount: form.pageCount ? Number(form.pageCount) : undefined,
      isbn: form.isbn || undefined,
      copyrightInfo: form.copyrightInfo || undefined,
      licenceId: form.licenceId ? Number(form.licenceId) : null,
      downloadAllowed: form.downloadAllowed,
      scholarlyReviewRequired: form.scholarlyReviewRequired,
      coverMediaId: form.coverMediaId || undefined,
    }
    try {
      if (editing) {
        await api(`/admin/books/${bookId}`, { method: 'PATCH', body })
        toast.push('success', 'Book updated (audited)')
        navigate(`/books/${bookId}`)
      } else {
        const created = await apiData<BookAdminView>('/admin/books', { method: 'POST', body })
        toast.push('success', 'Book created as a draft — it is not public until published')
        navigate(`/books/${created.bookId}`)
      }
    } catch (err) {
      if (err instanceof ApiError) toast.push('error', `${err.code}: ${err.detail}`)
      else toast.push('error', 'Save failed — is the backend reachable?')
    } finally {
      setBusy(false)
    }
  }

  const addAuthor = async () => {
    if (!newAuthor.trim()) return
    try {
      const created = await apiData<AuthorView>('/admin/authors', { method: 'POST', body: { name: newAuthor.trim() } })
      setAuthors((a) => [...a, created])
      setForm((f) => ({ ...f, authorIds: [...f.authorIds, created.authorId] }))
      setNewAuthor('')
      toast.push('success', 'Author added')
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Author creation failed')
    }
  }

  if (loading) return <Spinner label="Loading form…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  const autoScholarly = SCHOLARLY_SUBJECTS.includes(form.subjectId)

  return (
    <div className="space-y-4">
      <div>
        <Link to="/books" className="text-xs text-emerald-deep hover:underline">
          ← Book management
        </Link>
        <h1 className="text-xl font-semibold text-gray-900">{editing ? 'Edit book' : 'Add book'}</h1>
        <p className="text-sm text-gray-500">
          Saved books start as drafts — never public merely because they were created.
        </p>
      </div>

      <form onSubmit={submit} className="space-y-6">
        <Card>
          <h2 className="mb-3 font-medium text-gray-900">Essential metadata</h2>
          <Field label="Title *" error={errors.title}>
            <Input value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
          </Field>
          <Field label="Subtitle">
            <Input value={form.subtitle} onChange={(e) => setForm((f) => ({ ...f, subtitle: e.target.value }))} />
          </Field>
          <div className="grid gap-x-4 sm:grid-cols-2">
            <Field label="Category *" error={errors.subjectId}>
              <Select value={form.subjectId} onChange={(e) => setForm((f) => ({ ...f, subjectId: e.target.value }))}>
                <option value="">Choose…</option>
                {subjects.map((s) => (
                  <option key={s.slug} value={s.slug}>
                    {String(s.position).padStart(2, '0')} · {s.name}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Language *" error={errors.language}>
              <Select value={form.language} onChange={(e) => setForm((f) => ({ ...f, language: e.target.value }))}>
                {LANGS.map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Description">
            <Textarea rows={4} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
          </Field>
          <div>
            <span className="mb-1 block text-sm font-medium text-gray-700">Authors</span>
            <div className="mb-2 flex flex-wrap gap-1">
              {form.authorIds.length === 0 && <span className="text-xs text-gray-400">No authors selected</span>}
              {form.authorIds.map((id) => {
                const a = authors.find((x) => x.authorId === id)
                return (
                  <span key={id} className="flex items-center gap-1 rounded-full bg-emerald-deep/10 px-2 py-0.5 text-xs text-emerald-deep">
                    {a?.name || id.slice(0, 8)}
                    <button
                      type="button"
                      aria-label={`Remove author ${a?.name || ''}`}
                      onClick={() => setForm((f) => ({ ...f, authorIds: f.authorIds.filter((x) => x !== id) }))}
                    >
                      ×
                    </button>
                  </span>
                )
              })}
            </div>
            <div className="flex gap-2">
              <Input
                value={newAuthor}
                onChange={(e) => setNewAuthor(e.target.value)}
                placeholder="New author name…"
                aria-label="New author name"
              />
              <Button type="button" variant="secondary" onClick={addAuthor}>
                Add author
              </Button>
            </div>
          </div>
        </Card>

        <Card>
          <h2 className="mb-3 font-medium text-gray-900">Publication details</h2>
          <div className="grid gap-x-4 sm:grid-cols-2">
            <Field label="Publisher">
              <Input value={form.publisher} onChange={(e) => setForm((f) => ({ ...f, publisher: e.target.value }))} />
            </Field>
            <Field label="Edition">
              <Input value={form.edition} onChange={(e) => setForm((f) => ({ ...f, edition: e.target.value }))} />
            </Field>
            <Field label="Publication year" error={errors.publicationYear}>
              <Input
                inputMode="numeric"
                value={form.publicationYear}
                onChange={(e) => setForm((f) => ({ ...f, publicationYear: e.target.value }))}
                placeholder="e.g. 1445 or 2024"
              />
            </Field>
            <Field label="Page count" error={errors.pageCount}>
              <Input
                inputMode="numeric"
                value={form.pageCount}
                onChange={(e) => setForm((f) => ({ ...f, pageCount: e.target.value }))}
              />
            </Field>
            <Field label="ISBN / identifier" error={errors.isbn}>
              <Input value={form.isbn} onChange={(e) => setForm((f) => ({ ...f, isbn: e.target.value }))} />
            </Field>
            <Field label="Cover image (uploaded, scan-clean)">
              <Select value={form.coverMediaId} onChange={(e) => setForm((f) => ({ ...f, coverMediaId: e.target.value }))}>
                <option value="">No cover yet</option>
                {covers.map((m) => (
                  <option key={m.mediaId} value={m.mediaId}>
                    {m.originalFilename || m.mediaId.slice(0, 8)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Copyright information">
            <Input value={form.copyrightInfo} onChange={(e) => setForm((f) => ({ ...f, copyrightInfo: e.target.value }))} />
          </Field>
          <div className="grid gap-x-4 sm:grid-cols-2">
            <Field label="Licence">
              <Select value={form.licenceId} onChange={(e) => setForm((f) => ({ ...f, licenceId: e.target.value }))}>
                <option value="">Not specified</option>
                {licences.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.code} — {l.title}
                  </option>
                ))}
              </Select>
            </Field>
            <fieldset className="mb-3 mt-1 space-y-2 text-sm">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={form.downloadAllowed}
                  onChange={(e) => setForm((f) => ({ ...f, downloadAllowed: e.target.checked }))}
                />
                Downloads permitted (licence must allow it — enforced again at download time)
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={form.scholarlyReviewRequired || autoScholarly}
                  disabled={autoScholarly}
                  onChange={(e) => setForm((f) => ({ ...f, scholarlyReviewRequired: e.target.checked }))}
                />
                Requires scholarly review
                {autoScholarly && <span className="text-xs text-purple-700">(mandatory for Quran/Tafsir, Hadith, Fiqh, Aqeedah)</span>}
              </label>
            </fieldset>
          </div>
        </Card>

        <div className="flex justify-end gap-2">
          <Link to="/books">
            <Button type="button" variant="secondary">
              Cancel
            </Button>
          </Link>
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving…' : editing ? 'Save changes' : 'Create draft book'}
          </Button>
        </div>
      </form>
    </div>
  )
}
