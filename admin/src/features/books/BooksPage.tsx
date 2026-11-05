import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, apiData } from '../../api/client'
import type { Envelope, PageMeta } from '../../api/client'
import type { BookAdminView, SubjectView } from '../../api/types'
import { Button, Card, EmptyState, ErrorState, Input, Pagination, Select, Spinner, StatusPill } from '../../components/ui'

const LANGS = [
  ['en', 'English'],
  ['ar', 'العربية (Arabic)'],
  ['ur', 'اردو (Urdu)'],
  ['hi', 'हिन्दी (Hindi)'],
]

export default function BooksPage() {
  const [params, setParams] = useSearchParams()
  const [rows, setRows] = useState<BookAdminView[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [subjects, setSubjects] = useState<SubjectView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [q, setQ] = useState(params.get('q') || '')
  const [page, setPage] = useState(Number(params.get('page') || 1))
  const [subject, setSubject] = useState(params.get('subject') || '')
  const [language, setLanguage] = useState(params.get('language') || '')
  const [status, setStatus] = useState(params.get('status') || '')
  const [format, setFormat] = useState(params.get('format') || '')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams()
      if (q.trim()) query.set('q', q.trim())
      if (subject) query.set('subject', subject)
      if (language) query.set('language', language)
      if (status) query.set('status', status)
      if (format) query.set('format', format)
      query.set('page', String(page))
      query.set('size', '20')
      const res: Envelope<BookAdminView[]> = await api(`/admin/books?${query.toString()}`)
      setRows(res.data)
      setMeta(res.meta || null)
      const next = new URLSearchParams(params)
      next.set('page', String(page))
      if (q.trim()) next.set('q', q.trim())
      else next.delete('q')
      if (subject) next.set('subject', subject)
      else next.delete('subject')
      if (language) next.set('language', language)
      else next.delete('language')
      if (status) next.set('status', status)
      else next.delete('status')
      if (format) next.set('format', format)
      else next.delete('format')
      setParams(next, { replace: true })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, subject, language, status, format, page])

  useEffect(() => {
    apiData<SubjectView[]>('/admin/subjects')
      .then(setSubjects)
      .catch(() => setSubjects([]))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Book management</h1>
          <p className="text-sm text-gray-500">Platform-managed library — metadata, files, licences, publication state</p>
        </div>
        <Link to="/books/new">
          <Button variant="gold">+ Add book</Button>
        </Link>
      </div>

      <Card className="flex flex-wrap items-end gap-2">
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="bq">
            Search title
          </label>
          <Input id="bq" className="mt-1 w-52" value={q} onChange={(e) => { setQ(e.target.value); setPage(1) }} />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="bs">
            Category
          </label>
          <Select id="bs" className="mt-1" value={subject} onChange={(e) => { setSubject(e.target.value); setPage(1) }}>
            <option value="">All categories</option>
            {subjects.map((s) => (
              <option key={s.slug} value={s.slug}>
                {String(s.position).padStart(2, '0')} · {s.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="bl">
            Language
          </label>
          <Select id="bl" className="mt-1" value={language} onChange={(e) => { setLanguage(e.target.value); setPage(1) }}>
            <option value="">All languages</option>
            {LANGS.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="bf">
            Format
          </label>
          <Select id="bf" className="mt-1" value={format} onChange={(e) => { setFormat(e.target.value); setPage(1) }}>
            <option value="">Any</option>
            <option value="pdf">PDF</option>
            <option value="epub">EPUB</option>
          </Select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500" htmlFor="bst">
            Status
          </label>
          <Select id="bst" className="mt-1" value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }}>
            <option value="">All statuses</option>
            {['draft', 'in_editorial_review', 'in_scholarly_review', 'approved', 'published', 'archived'].map((s) => (
              <option key={s} value={s}>
                {s.replaceAll('_', ' ')}
              </option>
            ))}
          </Select>
        </div>
        <Button variant="secondary" onClick={load}>
          Apply
        </Button>
      </Card>

      {loading ? (
        <Spinner label="Loading books…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No books match"
          hint="Create the first book with “Add book”, or relax the filters."
          action={
            <Link to="/books/new" className="mt-2">
              <Button variant="gold">+ Add book</Button>
            </Link>
          }
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Authors</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Lang</th>
                <th className="px-4 py-3">Format</th>
                <th className="px-4 py-3">Publication</th>
                <th className="px-4 py-3">Updated</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((b) => (
                <tr key={b.bookId} className="hover:bg-gray-50">
                  <td className="max-w-xs px-4 py-3">
                    <Link to={`/books/${b.bookId}`} className="font-medium text-emerald-deep hover:underline">
                      {b.title}
                    </Link>
                    {b.subtitle && <p className="truncate text-xs text-gray-500">{b.subtitle}</p>}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {b.authors.map((a) => a.name).join(', ') || '—'}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{b.subject.name}</td>
                  <td className="px-4 py-3 text-xs uppercase text-gray-600">{b.language}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {b.files.map((f) => f.format.toUpperCase()).join(', ') || '—'}
                    {b.cover && <span className="ml-1 text-gray-400">+cover</span>}
                  </td>
                  <td className="px-4 py-3">
                    <StatusPill status={b.status} />
                    {b.scholarlyReviewRequired && (
                      <span className="ml-1 rounded-full bg-purple-50 px-2 py-0.5 text-[10px] text-purple-700">
                        scholarly
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{new Date(b.updatedAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/books/${b.bookId}`}>
                      <Button variant="ghost">Open</Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {meta && (
            <div className="px-4">
              <Pagination page={meta.page} totalPages={meta.totalPages} totalElements={meta.totalElements} onChange={setPage} />
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
