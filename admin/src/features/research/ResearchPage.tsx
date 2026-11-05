import { useCallback, useEffect, useState } from 'react'
import {
  createArticle,
  fetchArticle,
  fetchArticles,
  fetchTopics,
  transitionArticle,
  updateArticle,
  type ArticleDetail,
  type ArticleRow,
  type ArticleStatus,
  type CitationBody,
  type ResearchTopic,
  type SourceTier,
} from '../../api/research'
import { Button, Card, ErrorState, Field, Input, Select, Spinner, StatusPill, Textarea, useToast } from '../../components/ui'
import { Modal, ModalActions } from '../academy/AcademyPage'

/**
 * Islamic Research management (M5 Module 11). Every article carries the
 * mandatory three-way source classification (primary source / secondary
 * research / editorial commentary) which is rendered on every public surface.
 * Primary and secondary articles additionally pass through scholarly review
 * (scholar_reviewer role) before publication; citations are structurally
 * verified server-side — a citation that points to nothing is refused.
 */

const TIER_CAPTION: Record<SourceTier, string> = {
  primary: 'Primary source — Qur’an and hadith text itself; scholarly review required',
  secondary: 'Secondary research — academic analysis of primary sources; scholarly review required',
  editorial: 'Editorial commentary — platform editorial voice; no scholarly review required',
}

const NEXT_ACTIONS: Record<ArticleStatus, { action: string; label: string }[]> = {
  draft: [{ action: 'submit-review', label: 'Submit for review' }],
  in_editorial_review: [{ action: 'approve-editorial', label: 'Approve (editorial)' }],
  in_scholarly_review: [{ action: 'approve', label: 'Approve (scholarly)' }],
  approved: [{ action: 'publish', label: 'Publish' }],
  published: [{ action: 'unpublish', label: 'Unpublish' }],
  archived: [],
}

const emptyCitation = (): CitationBody => ({ citationKind: 'external', externalRef: '', quote: '', note: '' })

const emptyForm = {
  topicSlug: 'quran',
  title: '',
  titleAr: '',
  summary: '',
  body: '',
  language: 'en',
  authorDisplayName: '',
  sourceTier: 'secondary' as SourceTier,
  citations: [] as CitationBody[],
}

export default function ResearchPage() {
  const toast = useToast()
  const [rows, setRows] = useState<ArticleRow[]>([])
  const [topics, setTopics] = useState<ResearchTopic[]>([])
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [detail, setDetail] = useState<ArticleDetail | null>(null)
  const [form, setForm] = useState<typeof emptyForm | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const page = await fetchArticles(status || undefined)
      setRows(page.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    fetchTopics()
      .then(setTopics)
      .catch(() => setTopics([]))
  }, [])

  const openCreate = () => {
    setEditingId(null)
    setForm(emptyForm)
  }

  const openEdit = async (articleId: string) => {
    setBusy(true)
    try {
      const d = await fetchArticle(articleId)
      setDetail(d)
      setEditingId(articleId)
      setForm({
        topicSlug: d.topic.slug,
        title: d.title,
        titleAr: d.titleAr ?? '',
        summary: d.summary ?? '',
        body: d.body,
        language: d.language,
        authorDisplayName: d.authorDisplayName ?? '',
        sourceTier: d.sourceTier,
        citations: d.citations.map((c) => ({ ...c })),
      })
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Load failed')
    } finally {
      setBusy(false)
    }
  }

  const save = async () => {
    if (!form) return
    setBusy(true)
    try {
      const body = {
        topicSlug: form.topicSlug,
        title: form.title.trim(),
        titleAr: form.titleAr || null,
        summary: form.summary || null,
        body: form.body,
        language: form.language,
        authorDisplayName: form.authorDisplayName || null,
        sourceTier: form.sourceTier,
        citations: form.citations.filter((c) => citationFilled(c)),
      }
      if (editingId) {
        await updateArticle(editingId, body)
        toast.push('success', 'Article updated (audited)')
      } else {
        await createArticle(body)
        toast.push('success', 'Article created as draft (audited)')
      }
      setForm(null)
      setEditingId(null)
      setDetail(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  const runTransition = async (articleId: string, action: string) => {
    let notes: string | undefined
    if (action === 'reject') {
      notes = window.prompt('Rejection notes (required)') ?? undefined
      if (!notes) return
    }
    setBusy(true)
    try {
      await transitionArticle(articleId, action, notes)
      toast.push('success', 'Transition applied (audited)')
      await load()
      if (detail && detail.articleId === articleId) setDetail(await fetchArticle(articleId))
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Transition failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading research articles…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Islamic research</h1>
          <p className="text-sm text-gray-500">
            Primary sources, secondary research and editorial commentary are always distinguished. Non-editorial
            articles additionally require scholarly review before publication.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status" className="w-48">
            <option value="">All statuses</option>
            {['draft', 'in_editorial_review', 'in_scholarly_review', 'approved', 'published', 'archived'].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
          <Button onClick={openCreate}>New article</Button>
        </div>
      </div>

      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[860px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Article</th>
              <th className="px-4 py-3">Topic</th>
              <th className="px-4 py-3">Source tier</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-400">
                  No research articles yet.
                </td>
              </tr>
            )}
            {rows.map((a) => (
              <tr key={a.articleId} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-800">{a.title}</td>
                <td className="px-4 py-3 text-xs text-gray-500">{a.topic.name}</td>
                <td className="px-4 py-3">
                  <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">{a.sourceTier}</span>
                </td>
                <td className="px-4 py-3">
                  <StatusPill status={a.status} />
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    {(NEXT_ACTIONS[a.status] ?? []).map((t) => (
                      <Button key={t.action} variant="ghost" disabled={busy} onClick={() => runTransition(a.articleId, t.action)}>
                        {t.label}
                      </Button>
                    ))}
                    <Button variant="ghost" onClick={() => openEdit(a.articleId)}>
                      Edit
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {form && (
        <Modal title={editingId ? 'Edit article' : 'New research article'} onClose={() => setForm(null)} wide>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Topic">
              <Select value={form.topicSlug} onChange={(e) => setForm({ ...form, topicSlug: e.target.value })}>
                {topics.map((t) => (
                  <option key={t.slug} value={t.slug}>
                    {t.name}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Language">
              <Select value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })}>
                {['en', 'ar', 'ur', 'hi'].map((l) => (
                  <option key={l} value={l}>
                    {l}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Title">
            <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </Field>
          <Field label="Title (Arabic, optional)">
            <Input value={form.titleAr} onChange={(e) => setForm({ ...form, titleAr: e.target.value })} />
          </Field>
          <Field label="Summary">
            <Textarea rows={2} value={form.summary} onChange={(e) => setForm({ ...form, summary: e.target.value })} />
          </Field>
          <Field label="Body">
            <Textarea rows={6} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
          </Field>
          <Field label="Author display name (recorded text — never generated)">
            <Input value={form.authorDisplayName} onChange={(e) => setForm({ ...form, authorDisplayName: e.target.value })} />
          </Field>
          <Field label="Source tier">
            <Select value={form.sourceTier} onChange={(e) => setForm({ ...form, sourceTier: e.target.value as SourceTier })}>
              {(['primary', 'secondary', 'editorial'] as SourceTier[]).map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
            <p className="mt-1 text-xs text-gray-400">{TIER_CAPTION[form.sourceTier]}</p>
          </Field>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
                Citations (verified against the referenced record server-side)
              </p>
              <Button variant="ghost" onClick={() => setForm({ ...form, citations: [...form.citations, emptyCitation()] })}>
                + Citation
              </Button>
            </div>
            {form.citations.map((c, i) => (
              <div key={i} className="rounded border border-gray-200 p-2">
                <div className="flex items-center gap-2">
                  <Select
                    aria-label={`Citation ${i + 1} kind`}
                    value={c.citationKind}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        citations: form.citations.map((x, j) =>
                          j === i ? { ...emptyCitation(), ...x, citationKind: e.target.value as CitationBody['citationKind'] } : x,
                        ),
                      })
                    }
                    className="w-44"
                  >
                    {['quran_ayah', 'hadith', 'book', 'research_article', 'external'].map((k) => (
                      <option key={k} value={k}>
                        {k}
                      </option>
                    ))}
                  </Select>
                  <Button
                    variant="ghost"
                    onClick={() => setForm({ ...form, citations: form.citations.filter((_, j) => j !== i) })}
                  >
                    ✕
                  </Button>
                </div>
                <div className="mt-2 grid grid-cols-3 gap-2">
                  {c.citationKind === 'quran_ayah' && (
                    <>
                      <Input
                        placeholder="Surah 1-114"
                        aria-label={`Citation ${i + 1} surah`}
                        value={c.surahId ?? ''}
                        onChange={(e) => upd(i, { surahId: e.target.value ? Number(e.target.value) : null })}
                      />
                      <Input
                        placeholder="Ayah"
                        aria-label={`Citation ${i + 1} ayah`}
                        value={c.ayahNumber ?? ''}
                        onChange={(e) => upd(i, { ayahNumber: e.target.value ? Number(e.target.value) : null })}
                      />
                    </>
                  )}
                  {c.citationKind === 'hadith' && (
                    <Input
                      placeholder="Hadith internal id"
                      aria-label={`Citation ${i + 1} hadith id`}
                      value={c.hadithId ?? ''}
                      onChange={(e) => upd(i, { hadithId: e.target.value ? Number(e.target.value) : null })}
                    />
                  )}
                  {c.citationKind === 'book' && (
                    <Input
                      placeholder="Book public UUID"
                      aria-label={`Citation ${i + 1} book uuid`}
                      value={c.bookPublicId ?? ''}
                      onChange={(e) => upd(i, { bookPublicId: e.target.value || null })}
                    />
                  )}
                  {c.citationKind === 'research_article' && (
                    <Input
                      placeholder="Article public UUID"
                      aria-label={`Citation ${i + 1} article uuid`}
                      value={c.articlePublicId ?? ''}
                      onChange={(e) => upd(i, { articlePublicId: e.target.value || null })}
                    />
                  )}
                  {c.citationKind === 'external' && (
                    <Input
                      placeholder="External reference"
                      aria-label={`Citation ${i + 1} external ref`}
                      value={c.externalRef ?? ''}
                      onChange={(e) => upd(i, { externalRef: e.target.value || null })}
                    />
                  )}
                  <Input
                    placeholder="Quoted text (optional)"
                    aria-label={`Citation ${i + 1} quote`}
                    value={c.quote ?? ''}
                    onChange={(e) => upd(i, { quote: e.target.value || null })}
                  />
                </div>
              </div>
            ))}
          </div>

          <ModalActions
            busy={busy}
            onCancel={() => setForm(null)}
            onConfirm={save}
            confirmLabel={editingId ? 'Save changes' : 'Create draft'}
            disabled={!form.title.trim() || !form.body.trim()}
          />
        </Modal>
      )}

      {detail && !form && (
        <Modal title={detail.title} onClose={() => setDetail(null)} wide>
          <p className="text-sm text-gray-500">
            {detail.topic.name} · tier <span className="rounded bg-gray-100 px-2 py-0.5 text-xs">{detail.sourceTier}</span> ·{' '}
            <StatusPill status={detail.status} />
          </p>
          <p className="mt-2 whitespace-pre-wrap text-sm text-gray-700">{detail.summary}</p>
          <div className="mt-2 space-y-1">
            {detail.citations.map((c, i) => (
              <p key={i} className="text-xs text-gray-500">
                [{i + 1}] {c.citationKind}
                {c.citationKind === 'quran_ayah' && c.surahId != null ? ` ${c.surahId}:${c.ayahNumber}` : ''} —{' '}
                {c.quote ?? c.externalRef ?? ''}
              </p>
            ))}
          </div>
        </Modal>
      )}
    </div>
  )

  function upd(i: number, patch: Partial<CitationBody>) {
    setForm((f) =>
      f ? { ...f, citations: f.citations.map((x, j) => (j === i ? { ...x, ...patch } : x)) } : f,
    )
  }
}

function citationFilled(c: CitationBody): boolean {
  switch (c.citationKind) {
    case 'quran_ayah':
      return c.surahId != null && c.ayahNumber != null
    case 'hadith':
      return c.hadithId != null
    case 'book':
      return !!c.bookPublicId
    case 'research_article':
      return !!c.articlePublicId
    case 'external':
      return !!c.externalRef
    default:
      return false
  }
}
