import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, apiData } from '../../api/client'
import type { BookAdminView, ReviewRecordRow } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import {
  Button,
  Card,
  ConfirmDialog,
  ErrorState,
  Spinner,
  StatusPill,
  useToast,
} from '../../components/ui'

/**
 * Book detail (Module D): complete metadata + files + the authorised workflow
 * controls. Every action mirrors a backend state transition; the server — not
 * this UI — is the authority (WORKFLOW-001 otherwise).
 */
export default function BookDetailPage() {
  const { bookId } = useParams()
  const { hasPermission, hasAnyRole } = useAuth()
  const toast = useToast()
  const [book, setBook] = useState<BookAdminView | null>(null)
  const [reviews, setReviews] = useState<ReviewRecordRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [action, setAction] = useState<{ kind: 'reject' } | null>(null)
  const [, setNotes] = useState('')

  const canEdit = hasPermission('content.edit')
  const canPublish = hasPermission('content.publish')
  const canUpload = hasPermission('files.upload')
  const isEditorial = hasAnyRole('super_admin', 'admin', 'content_manager')
  const isScholar = hasAnyRole('scholar_reviewer')

  const load = useCallback(async () => {
    setError(null)
    try {
      setBook(await apiData<BookAdminView>(`/admin/books/${bookId}`))
      try {
        setReviews(await apiData<ReviewRecordRow[]>(`/admin/books/${bookId}/reviews`))
      } catch {
        setReviews([])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [bookId])

  useEffect(() => {
    load()
  }, [load])

  const transition = async (act: string, reasonNotes?: string) => {
    setBusy(true)
    try {
      const body = reasonNotes !== undefined ? { notes: reasonNotes } : {}
      await api(`/admin/books/${bookId}/${act}`, { method: 'POST', body })
      toast.push('success', `Action “${act}” applied (audited)`)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
      setAction(null)
      setNotes('')
    }
  }

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!book) return <Spinner label="Loading book…" />

  const s = book.status

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/books" className="text-xs text-emerald-deep hover:underline">
            ← Book management
          </Link>
          <h1 className="text-xl font-semibold text-gray-900">{book.title}</h1>
          {book.subtitle && <p className="text-sm text-gray-500">{book.subtitle}</p>}
          <p className="mt-1 flex items-center gap-2 text-sm">
            <StatusPill status={book.status} />
            {book.scholarlyReviewRequired && <StatusPill status="scholarly required" />}
            {book.downloadAllowed && <span className="text-xs text-gray-500">downloads permitted</span>}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canEdit && s === 'draft' && (
            <Link to={`/books/${book.bookId}/edit`}>
              <Button variant="secondary">Edit metadata</Button>
            </Link>
          )}
          {canEdit && s === 'draft' && (
            <Button onClick={() => transition('submit-review')} disabled={busy}>
              Submit for review
            </Button>
          )}
          {isEditorial && s === 'in_editorial_review' && (
            <>
              <Button onClick={() => transition('approve-editorial')} disabled={busy}>
                Approve (editorial)
              </Button>
              <Button variant="danger" onClick={() => setAction({ kind: 'reject' })} disabled={busy}>
                Reject…
              </Button>
            </>
          )}
          {isScholar && s === 'in_scholarly_review' && (
            <>
              <Button onClick={() => transition('approve')} disabled={busy}>
                Scholarly approval
              </Button>
              <Button variant="danger" onClick={() => setAction({ kind: 'reject' })} disabled={busy}>
                Reject…
              </Button>
            </>
          )}
          {canPublish && s === 'approved' && (
            <Button variant="gold" onClick={() => transition('publish')} disabled={busy}>
              Publish
            </Button>
          )}
          {canPublish && s === 'published' && (
            <Button variant="secondary" onClick={() => transition('unpublish')} disabled={busy}>
              Unpublish
            </Button>
          )}
          {canPublish && (s === 'draft' || s === 'approved') && (
            <Button variant="secondary" onClick={() => transition('archive')} disabled={busy}>
              Archive
            </Button>
          )}
        </div>
      </div>

      {s === 'draft' && (
        <div className="rounded border border-gray-200 bg-white px-4 py-3 text-sm text-gray-600">
          This book is a private draft. It will not appear in the public library until it passes review and an
          authorised publisher publishes it.
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-3 font-medium text-gray-900">Metadata</h2>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <dt className="text-gray-500">Category</dt>
            <dd>{book.subject.name}</dd>
            <dt className="text-gray-500">Authors</dt>
            <dd>{book.authors.map((a) => a.name).join(', ') || '—'}</dd>
            <dt className="text-gray-500">Language</dt>
            <dd className="uppercase">{book.language}</dd>
            <dt className="text-gray-500">Publisher</dt>
            <dd>{book.publisher || '—'}</dd>
            <dt className="text-gray-500">Edition</dt>
            <dd>{book.edition || '—'}</dd>
            <dt className="text-gray-500">Publication year</dt>
            <dd>{book.publicationYear || '—'}</dd>
            <dt className="text-gray-500">Pages</dt>
            <dd>{book.pageCount || '—'}</dd>
            <dt className="text-gray-500">ISBN</dt>
            <dd className="font-mono text-xs">{book.isbn || '—'}</dd>
            <dt className="text-gray-500">Copyright</dt>
            <dd>{book.copyrightInfo || '—'}</dd>
            <dt className="text-gray-500">Licence</dt>
            <dd>{book.licence ? `${book.licence.code} — ${book.licence.title}` : '—'}</dd>
            <dt className="text-gray-500">Created / updated</dt>
            <dd className="text-xs text-gray-500">
              {new Date(book.createdAt).toLocaleString()} · {new Date(book.updatedAt).toLocaleString()}
            </dd>
            {book.publishedAt && (
              <>
                <dt className="text-gray-500">Published at</dt>
                <dd>{new Date(book.publishedAt).toLocaleString()}</dd>
              </>
            )}
          </dl>
          {book.description && <p className="mt-4 whitespace-pre-line text-sm text-gray-700">{book.description}</p>}
        </Card>

        <div className="space-y-6">
          <Card>
            <div className="mb-2 flex items-center justify-between">
              <h2 className="font-medium text-gray-900">Files</h2>
              {canUpload && <Link to="/files" className="text-xs text-emerald-deep hover:underline">Upload →</Link>}
            </div>
            {book.cover && (
              <div className="mb-3 rounded border border-gray-200 p-2 text-sm">
                <p className="font-medium text-gray-800">Cover</p>
                <p className="text-xs text-gray-500">
                  {book.cover.mime} · {(book.cover.sizeBytes / 1024).toFixed(0)} KB · scan: {book.cover.scanStatus}
                </p>
              </div>
            )}
            {book.files.length === 0 ? (
              <p className="text-sm text-gray-500">No PDF/EPUB attached yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {book.files.map((f) => (
                  <li key={f.fileId} className="rounded border border-gray-200 p-2">
                    <p className="font-medium text-gray-800">{f.format.toUpperCase()}</p>
                    <p className="text-xs text-gray-500">
                      {f.mime} · {(f.sizeBytes / 1024).toFixed(0)} KB ·{' '}
                      <span className={f.scanStatus === 'clean' ? 'text-emerald-700' : 'text-amber-700'}>
                        scan: {f.scanStatus}
                      </span>
                      {f.downloadAllowed && ' · downloadable'}
                    </p>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-3 text-xs text-gray-400">
              Attachments come from the scan-clean upload inventory only — quarantined files can never be attached
              or published.
            </p>
          </Card>

          <Card className="p-0">
            <h2 className="border-b border-gray-100 px-4 py-3 font-medium text-gray-900">Review history</h2>
            {reviews.length === 0 ? (
              <p className="px-4 py-3 text-sm text-gray-500">No review decisions recorded.</p>
            ) : (
              <ul className="divide-y divide-gray-100 text-sm">
                {reviews.map((r, i) => (
                  <li key={i} className="px-4 py-2.5">
                    <p className="font-medium text-gray-800">
                      {r.stage.replaceAll('_', ' ')} — {r.decision}
                    </p>
                    {r.notes && <p className="text-xs text-gray-500">{r.notes}</p>}
                    <p className="text-xs text-gray-400">{new Date(r.decidedAt).toLocaleString()}</p>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={!!action}
        title="Reject this submission?"
        message="The book returns to draft with your reason. The rejection is recorded with your identity in the review history and the audit log."
        confirmLabel="Reject submission"
        danger
        requireReason
        busy={busy}
        onCancel={() => setAction(null)}
        onConfirm={(reason) => transition('reject', reason)}
      />
    </div>
  )
}
