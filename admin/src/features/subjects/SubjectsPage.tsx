import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, apiData } from '../../api/client'
import type { SubjectView } from '../../api/types'
import {
  Button,
  Card,
  ConfirmDialog,
  ErrorState,
  Field,
  Input,
  Select,
  Spinner,
  StatusPill,
  Textarea,
  useToast,
} from '../../components/ui'

/**
 * Subject/category management (Module C): the fixed 15-position client
 * reference. Category 06 is locked in the UI exactly as the backend enforces:
 * no rename, no visibility change, no publish/unpublish.
 */
export default function SubjectsPage() {
  const toast = useToast()
  const [subjects, setSubjects] = useState<SubjectView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<SubjectView | null>(null)
  const [form, setForm] = useState({ name: '', nameAr: '', description: '', imageUrl: '' })
  const [reordering, setReordering] = useState(false)
  const [order, setOrder] = useState<{ slug: string; name: string; position: number }[]>([])
  const [busy, setBusy] = useState(false)
  const [confirmPublish, setConfirmPublish] = useState<{ subject: SubjectView; publish: boolean } | null>(null)

  const reserved = useMemo(() => subjects.find((s) => s.slug === 'cat_unconfirmed_06'), [subjects])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setSubjects(await apiData<SubjectView[]>('/admin/subjects'))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openEdit = (s: SubjectView) => {
    setEditing(s)
    setForm({ name: s.name, nameAr: s.nameAr || '', description: s.description || '', imageUrl: s.imageUrl || '' })
  }

  const saveEdit = async () => {
    if (!editing) return
    setBusy(true)
    try {
      const body: Record<string, unknown> = {
        description: form.description || null,
        image: form.imageUrl || null,
      }
      if (editing.slug !== 'cat_unconfirmed_06') {
        body.name = form.name.trim()
        body.nameAr = form.nameAr || null
      }
      await api(`/admin/subjects/${editing.slug}`, { method: 'PATCH', body })
      toast.push('success', 'Subject updated (audited)')
      setEditing(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Update failed')
    } finally {
      setBusy(false)
    }
  }

  const doPublishToggle = async (publish: boolean) => {
    if (!confirmPublish) return
    setBusy(true)
    try {
      await api(`/admin/subjects/${confirmPublish.subject.slug}/${publish ? 'publish' : 'unpublish'}`, { method: 'POST' })
      toast.push('success', publish ? 'Subject published' : 'Subject unpublished (audited)')
      setConfirmPublish(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const openReorder = () => {
    setOrder(subjects.map((s) => ({ slug: s.slug, name: s.name, position: s.position })))
    setReordering(true)
  }

  const saveOrder = async () => {
    setBusy(true)
    try {
      const positions = order.map((o) => o.position).sort((a, b) => a - b)
      const complete = positions.every((p, i) => p === i + 1)
      if (!complete) {
        toast.push('error', 'Positions must be exactly 1..15 without gaps or duplicates')
        setBusy(false)
        return
      }
      await api('/admin/subjects/reorder', {
        method: 'POST',
        body: { order: order.map((o) => ({ subjectId: o.slug, position: o.position })) },
      })
      toast.push('success', 'Display order saved (audited)')
      setReordering(false)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Reorder failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading subjects…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Subjects &amp; categories</h1>
          <p className="text-sm text-gray-500">
            Fixed 15-position client reference — slugs are the stable IDs, display order is presentation only.
            Category 06 stays hidden until the client resolves it (OD-03).
          </p>
        </div>
        <Button variant="secondary" onClick={openReorder}>
          Change display order
        </Button>
      </div>

      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">#</th>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3">Stable ID (slug)</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {subjects.map((s) => {
              const locked = s.slug === 'cat_unconfirmed_06'
              return (
                <tr key={s.slug} className={locked ? 'bg-gray-50/60' : 'hover:bg-gray-50'}>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{String(s.position).padStart(2, '0')}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-800">
                      {locked ? 'Unidentified category — unresolved' : s.name}
                      {s.nameAr && <span className="ml-2 text-xs text-gray-400">{s.nameAr}</span>}
                    </p>
                    {s.description && <p className="max-w-md truncate text-xs text-gray-500">{s.description}</p>}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{s.slug}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={s.status} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    {locked ? (
                      <span className="text-xs italic text-gray-400">locked (OD-03)</span>
                    ) : (
                      <div className="flex justify-end gap-2">
                        <Button variant="ghost" onClick={() => openEdit(s)}>
                          Edit
                        </Button>
                        {s.status === 'active' ? (
                          <Button variant="secondary" onClick={() => setConfirmPublish({ subject: s, publish: false })}>
                            Unpublish
                          </Button>
                        ) : (
                          <Button variant="secondary" onClick={() => setConfirmPublish({ subject: s, publish: true })}>
                            Publish
                          </Button>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </Card>

      {reserved && (
        <p className="text-xs text-gray-400">
          Reserved position {String(reserved.position).padStart(2, '0')} ({reserved.slug}) is hidden from all public
          surfaces and can never be renamed or published from the CMS — the backend refuses it (WORKFLOW-001).
        </p>
      )}

      {/* edit dialog */}
      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
          <div className="w-full max-w-lg rounded-lg bg-white p-6 shadow-xl">
            <h3 className="text-lg font-semibold text-gray-900">Edit “{editing.name}”</h3>
            <p className="mt-1 text-xs text-gray-500">
              Stable ID: <span className="font-mono">{editing.slug}</span> — renaming an identified category also
              requires care: it is display metadata, not a join key.
            </p>
            <div className="mt-4">
              <Field label="Name">
                <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
              </Field>
              <Field label="Name (Arabic)">
                <Input value={form.nameAr} onChange={(e) => setForm((f) => ({ ...f, nameAr: e.target.value }))} />
              </Field>
              <Field label="Description">
                <Textarea
                  rows={3}
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="What this category contains (shown to users; no code change needed)"
                />
              </Field>
              <Field label="Image URL">
                <Input
                  value={form.imageUrl}
                  onChange={(e) => setForm((f) => ({ ...f, imageUrl: e.target.value }))}
                  placeholder="https://… or a media reference"
                />
              </Field>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setEditing(null)} disabled={busy}>
                Cancel
              </Button>
              <Button onClick={saveEdit} disabled={busy || !form.name.trim()}>
                {busy ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* reorder dialog */}
      {reordering && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
          <div className="max-h-[85vh] w-full max-w-xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl">
            <h3 className="text-lg font-semibold text-gray-900">Display order (1–15)</h3>
            <p className="mt-1 text-xs text-gray-500">
              The complete set is required — the backend validates exactly 1..15. Slugs never change.
            </p>
            <div className="mt-4 space-y-2">
              {order.map((o) => (
                <div key={o.slug} className="flex items-center justify-between gap-3 rounded border border-gray-200 px-3 py-2">
                  <span className="text-sm text-gray-700">{o.name}</span>
                  <Select
                    value={String(o.position)}
                    aria-label={`Position of ${o.name}`}
                    onChange={(e) =>
                      setOrder((list) =>
                        list.map((x) => (x.slug === o.slug ? { ...x, position: Number(e.target.value) } : x)),
                      )
                    }
                    className="w-20"
                  >
                    {Array.from({ length: 15 }, (_, i) => i + 1).map((p) => (
                      <option key={p} value={p}>
                        {String(p).padStart(2, '0')}
                      </option>
                    ))}
                  </Select>
                </div>
              ))}
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setReordering(false)} disabled={busy}>
                Cancel
              </Button>
              <Button onClick={saveOrder} disabled={busy}>
                {busy ? 'Saving…' : 'Save order'}
              </Button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={!!confirmPublish}
        title={confirmPublish?.publish ? 'Publish this category?' : 'Unpublish this category?'}
        message={
          confirmPublish?.publish
            ? `“${confirmPublish.subject.name}” becomes visible in the public category list.`
            : `“${confirmPublish?.subject.name}” disappears from the public list (archived). Category 06 can never be published.`
        }
        busy={busy}
        onCancel={() => setConfirmPublish(null)}
        onConfirm={() => doPublishToggle(!!confirmPublish?.publish)}
      />
    </div>
  )
}
