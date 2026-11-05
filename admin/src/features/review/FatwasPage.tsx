import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, apiData } from '../../api/client'
import type { Envelope, PageMeta } from '../../api/client'
import type { FatwaAuditRow, FatwaView } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import {
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  Input,
  Pagination,
  Select,
  Spinner,
  StatusPill,
  Textarea,
  useToast,
} from '../../components/ui'

/**
 * Fatwa administration (strict family per approved spec §3.17). The UI mirrors
 * the backend separation of duties: only a Scholar Reviewer records decisions;
 * publishing requires the approved state plus a scholar approval by a
 * DIFFERENT user — enforced server-side; the UI merely reflects it.
 */
export default function FatwasPage() {
  const { hasPermission } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const state = params.get('state') || ''
  const [rows, setRows] = useState<FatwaView[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const canCreate = hasPermission('fatwa.create')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams({ page: String(page), size: '20' })
      if (state) query.set('state', state)
      const res: Envelope<FatwaView[]> = await api(`/admin/fatwas?${query.toString()}`)
      setRows(res.data)
      setMeta(res.meta || null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [page, state])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Fatwas</h1>
          <p className="max-w-2xl text-sm text-gray-500">
            Separation of duties is mandatory: the Scholar Reviewer decides; a different authorised administrator
            publishes. No route bypasses a missing scholarly approval — every attempt is refused (WORKFLOW-001) and
            audited.
          </p>
        </div>
        {canCreate && (
          <Button variant="gold" onClick={() => navigate('/fatwas/new')}>
            + New Fatwa draft
          </Button>
        )}
      </div>

      <div className="flex flex-wrap gap-1.5">
        {['', 'draft', 'in_review', 'approved', 'published', 'archived'].map((s) => (
          <button
            key={s || 'all'}
            onClick={() => {
              setPage(1)
              setParams(s ? { state: s } : {}, { replace: true })
            }}
            className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${
              state === s ? 'bg-emerald-deep text-white' : 'bg-white text-gray-600 hover:bg-gray-100'
            }`}
          >
            {s ? s.replaceAll('_', ' ') : 'all'}
          </button>
        ))}
      </div>

      {loading ? (
        <Spinner label="Loading Fatwas…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No Fatwas in this view"
          hint="M2 uses synthetic development fixtures only — real Fatwa content requires the scholar arrangement (OD-gated)."
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Question</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Lang</th>
                <th className="px-4 py-3">State</th>
                <th className="px-4 py-3">Updated</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((f) => (
                <tr key={f.fatwaId} className="hover:bg-gray-50">
                  <td className="max-w-sm px-4 py-3">
                    <Link to={`/fatwas/${f.fatwaId}`} className="font-medium text-emerald-deep hover:underline">
                      {f.question}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{f.category || '—'}</td>
                  <td className="px-4 py-3 text-xs uppercase text-gray-600">{f.language}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={f.state} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{new Date(f.updatedAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/fatwas/${f.fatwaId}`}>
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

// ---------------- detail ----------------

export function FatwaDetailPage() {
  const { fatwaId } = useParams()
  const { hasPermission, hasAnyRole } = useAuth()
  const toast = useToast()
  const [fatwa, setFatwa] = useState<FatwaView | null>(null)
  const [trail, setTrail] = useState<FatwaAuditRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [notes, setNotes] = useState('')
  const [reviewerId, setReviewerId] = useState('')

  const canCreate = hasPermission('fatwa.create')
  const isScholar = hasAnyRole('scholar_reviewer')
  const canPublish = hasPermission('fatwa.publish')
  const canAssign = hasAnyRole('super_admin', 'admin')

  const load = useCallback(async () => {
    setError(null)
    try {
      setFatwa(await apiData<FatwaView>(`/admin/fatwas/${fatwaId}`))
      try {
        setTrail(await apiData<FatwaAuditRow[]>(`/admin/fatwas/${fatwaId}/audit`))
      } catch {
        setTrail([])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [fatwaId])

  useEffect(() => {
    load()
  }, [load])

  if (error) return <ErrorState message={error} onRetry={load} />
  if (!fatwa) return <Spinner label="Loading Fatwa…" />

  const act = async (path: string, body?: unknown, okMsg?: string) => {
    setBusy(true)
    try {
      await api(`/admin/fatwas/${fatwaId}/${path}`, { method: 'POST', body: body || {} })
      toast.push('success', okMsg || 'Action applied (audited)')
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
      setRejecting(false)
      setNotes('')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/fatwas" className="text-xs text-emerald-deep hover:underline">
            ← Fatwas
          </Link>
          <h1 className="text-xl font-semibold text-gray-900">Fatwa record</h1>
          <p className="mt-1 flex items-center gap-2 text-sm">
            <StatusPill status={fatwa.state} />
            <span className="text-xs text-gray-500">version {fatwa.version}</span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canCreate && fatwa.state === 'draft' && (
            <Button onClick={() => act('submit-review', {}, 'Submitted for scholarly review')} disabled={busy}>
              Submit for review
            </Button>
          )}
          {canCreate && fatwa.state === 'in_review' && (
            <Link to={`/fatwas/${fatwaId}/edit`}>
              <Button variant="secondary">Edit draft</Button>
            </Link>
          )}
          {canPublish && fatwa.state === 'approved' && (
            <Button
              variant="gold"
              onClick={() => act('publish', {}, 'Published — separation of duties validated server-side')}
              disabled={busy}
            >
              Publish
            </Button>
          )}
          {canPublish && fatwa.state === 'published' && (
            <Button variant="secondary" onClick={() => act('unpublish', {}, 'Unpublished → archived')} disabled={busy}>
              Unpublish
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="mb-2 font-medium text-gray-900">Question</h2>
          <p className="whitespace-pre-line text-sm text-gray-800">{fatwa.question}</p>
          <h2 className="mb-2 mt-4 font-medium text-gray-900">
            {fatwa.state === 'published' || fatwa.state === 'archived' ? 'Answer (published)' : 'Answer draft'}
          </h2>
          <p className="whitespace-pre-line text-sm text-gray-800">{fatwa.answer || '(draft — not shown publicly)'}</p>
          <p className="mt-3 text-xs text-gray-400">
            Category: {fatwa.category || '—'} · language: {fatwa.language} · updated{' '}
            {new Date(fatwa.updatedAt).toLocaleString()}
          </p>
        </Card>

        <div className="space-y-6">
          {isScholar && fatwa.state === 'in_review' && (
            <Card>
              <h2 className="mb-2 font-medium text-gray-900">Scholarly decision</h2>
              <p className="mb-3 text-xs text-gray-500">
                Only a Scholar Reviewer records the decision (permission fatwa.review.approve). The decision is stored
                with your identity.
              </p>
              <Field label="Decision notes">
                <Textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </Field>
              <div className="flex gap-2">
                <Button onClick={() => act('decision', { decision: 'approve', notes: notes || null }, 'Approved (audited)')} disabled={busy}>
                  Approve
                </Button>
                <Button variant="danger" onClick={() => setRejecting(true)} disabled={busy}>
                  Reject…
                </Button>
              </div>
            </Card>
          )}
          {canAssign && fatwa.state === 'in_review' && (
            <Card>
              <h2 className="mb-2 font-medium text-gray-900">Assign Scholar Reviewer</h2>
              <p className="mb-2 text-xs text-gray-500">
                The assignee must hold the Scholar Reviewer role — the backend refuses anyone else (audited).
              </p>
              <Input
                value={reviewerId}
                onChange={(e) => setReviewerId(e.target.value)}
                placeholder="Reviewer user public id (uuid)"
              />
              <Button
                className="mt-2"
                variant="secondary"
                disabled={busy || !reviewerId.trim()}
                onClick={() => act('assign-reviewer', { reviewerUserId: reviewerId.trim() }, 'Reviewer assigned')}
              >
                Assign
              </Button>
            </Card>
          )}
          <Card className="p-0">
            <h2 className="border-b border-gray-100 px-4 py-3 font-medium text-gray-900">Audit trail</h2>
            {trail.length === 0 ? (
              <p className="px-4 py-3 text-sm text-gray-500">No events recorded yet.</p>
            ) : (
              <ul className="divide-y divide-gray-100 text-sm">
                {trail.map((t, i) => (
                  <li key={i} className="px-4 py-2.5">
                    <p className="font-medium text-gray-800">{t.action}</p>
                    {t.notes && <p className="text-xs text-gray-500">{t.notes}</p>}
                    <p className="text-xs text-gray-400">
                      {t.actor ? `${t.actor.slice(0, 8)}…` : 'system'} · {new Date(t.at).toLocaleString()}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={rejecting}
        title="Reject this Fatwa draft?"
        message="The record returns to draft with your reason; the decision is recorded with your identity."
        confirmLabel="Reject"
        danger
        requireReason
        busy={busy}
        onCancel={() => setRejecting(false)}
        onConfirm={(reason) => act('decision', { decision: 'reject', notes: reason }, 'Rejected (audited)')}
      />
    </div>
  )
}

// ---------------- create / edit form ----------------

export function FatwaFormPage() {
  const { fatwaId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const editing = !!fatwaId
  const [form, setForm] = useState({ question: '', answerDraft: '', category: '', language: 'ar', sourceRefs: '' })
  const [loading, setLoading] = useState(editing)
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (!editing) return
    apiData<FatwaView>(`/admin/fatwas/${fatwaId}`)
      .then((f) => setForm((prev) => ({ ...prev, question: f.question, category: f.category || '', language: f.language })))
      .catch((e) => toast.push('error', e instanceof Error ? e.message : 'Load failed'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fatwaId])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!form.question.trim()) errs.question = 'Question is required'
    if (!form.answerDraft.trim()) errs.answerDraft = 'Answer draft is required'
    setErrors(errs)
    if (Object.keys(errs).length > 0) return
    setBusy(true)
    const body = {
      question: form.question.trim(),
      answerDraft: form.answerDraft.trim(),
      category: form.category || undefined,
      language: form.language,
      sourceRefs: form.sourceRefs
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean),
    }
    try {
      if (editing) {
        await api(`/admin/fatwas/${fatwaId}`, { method: 'PATCH', body })
        toast.push('success', 'Fatwa updated — an approved record resets to draft')
      } else {
        const created = await apiData<FatwaView>('/admin/fatwas', { method: 'POST', body })
        toast.push('success', 'Fatwa draft created (synthetic dev fixture)')
        navigate(`/fatwas/${created.fatwaId}`)
        return
      }
      navigate(`/fatwas/${fatwaId}`)
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading Fatwa…" />

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <Link to="/fatwas" className="text-xs text-emerald-deep hover:underline">
          ← Fatwas
        </Link>
        <h1 className="text-xl font-semibold text-gray-900">{editing ? 'Edit Fatwa draft' : 'New Fatwa draft'}</h1>
        <p className="text-sm text-gray-500">
          M2 uses clearly-labelled synthetic fixtures. Real Fatwa content requires the qualified scholar arrangement
          and stays OD-gated. Editing an approved record resets it to draft (approval invalidated) — enforced
          server-side.
        </p>
      </div>
      <Card>
        <form onSubmit={submit}>
          <Field label="Question *" error={errors.question}>
            <Textarea rows={3} value={form.question} onChange={(e) => setForm((f) => ({ ...f, question: e.target.value }))} />
          </Field>
          <Field label="Answer draft *" error={errors.answerDraft}>
            <Textarea rows={6} value={form.answerDraft} onChange={(e) => setForm((f) => ({ ...f, answerDraft: e.target.value }))} />
          </Field>
          <div className="grid gap-x-4 sm:grid-cols-2">
            <Field label="Category">
              <Input value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} />
            </Field>
            <Field label="Language">
              <Select value={form.language} onChange={(e) => setForm((f) => ({ ...f, language: e.target.value }))}>
                <option value="ar">العربية (Arabic)</option>
                <option value="en">English</option>
                <option value="ur">اردو (Urdu)</option>
                <option value="hi">हिन्दी (Hindi)</option>
              </Select>
            </Field>
          </div>
          <Field label="Source references (one per line — preserved verbatim)">
            <Textarea
              rows={3}
              value={form.sourceRefs}
              onChange={(e) => setForm((f) => ({ ...f, sourceRefs: e.target.value }))}
              placeholder="e.g. SYNTHETIC-REF-001 (dev fixture)"
            />
          </Field>
          <div className="flex justify-end gap-2">
            <Link to="/fatwas">
              <Button type="button" variant="secondary">
                Cancel
              </Button>
            </Link>
            <Button type="submit" disabled={busy}>
              {busy ? 'Saving…' : 'Save draft'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
