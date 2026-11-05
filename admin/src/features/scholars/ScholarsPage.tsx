import { useCallback, useEffect, useState } from 'react'
import {
  createScholar,
  fetchScholars,
  updateScholar,
  verifyScholar,
  type Scholar,
  type VerificationStatus,
} from '../../api/research'
import { Button, Card, ConfirmDialog, ErrorState, Field, Input, Spinner, StatusPill, Textarea, useToast } from '../../components/ui'
import { Modal, ModalActions } from '../academy/AcademyPage'

/**
 * Scholar directory management (M5 Module 14). Qualifications, credentials and
 * verification are RECORDED FACTS with an audit trail — the platform never
 * generates or endorses a qualification. Verification actions are confirmed
 * in the UI exactly as they are audited server-side.
 */

const emptyForm = {
  name: '',
  nameAr: '',
  biography: '',
  qualifications: '',
  areasOfExpertise: '',
}

export default function ScholarsPage() {
  const toast = useToast()
  const [rows, setRows] = useState<Scholar[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<Scholar | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [confirmVerify, setConfirmVerify] = useState<{ scholar: Scholar; next: VerificationStatus } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setRows(await fetchScholars())
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openEdit = (s: Scholar) => {
    setEditing(s)
    setForm({
      name: s.name,
      nameAr: s.nameAr ?? '',
      biography: s.biography ?? '',
      qualifications: s.qualifications ?? '',
      areasOfExpertise: s.areasOfExpertise ?? '',
    })
  }

  const save = async () => {
    setBusy(true)
    try {
      const body = {
        name: form.name.trim(),
        nameAr: form.nameAr || null,
        biography: form.biography || null,
        qualifications: form.qualifications || null,
        areasOfExpertise: form.areasOfExpertise || null,
      }
      if (editing) {
        await updateScholar(editing.scholarId, body)
        toast.push('success', 'Scholar profile updated (audited)')
      } else {
        await createScholar(body)
        toast.push('success', 'Scholar profile created (unverified by default)')
      }
      setEditing(null)
      setCreating(false)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  const runVerify = async () => {
    if (!confirmVerify) return
    setBusy(true)
    try {
      await verifyScholar(confirmVerify.scholar.scholarId, confirmVerify.next)
      toast.push('success', `Verification set to ${confirmVerify.next} (audited)`)
      setConfirmVerify(null)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Verification failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading scholars…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Scholar directory</h1>
          <p className="text-sm text-gray-500">
            Qualifications are recorded facts only — the platform never generates credentials. Verification is audited
            and feeds the Fatwa review trail (OD-08).
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>New scholar</Button>
      </div>

      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[860px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Scholar</th>
              <th className="px-4 py-3">Areas of expertise</th>
              <th className="px-4 py-3">Qualifications</th>
              <th className="px-4 py-3">Verification</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-400">
                  No scholars recorded yet.
                </td>
              </tr>
            )}
            {rows.map((s) => (
              <tr key={s.scholarId} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-medium text-gray-800">{s.name}</p>
                  {s.nameAr && <p className="text-xs text-gray-400">{s.nameAr}</p>}
                </td>
                <td className="px-4 py-3 text-xs text-gray-500">{s.areasOfExpertise ?? '—'}</td>
                <td className="px-4 py-3 max-w-[240px] truncate text-xs text-gray-500">{s.qualifications ?? '—'}</td>
                <td className="px-4 py-3">
                  <StatusPill status={s.verificationStatus} />
                  {s.verifiedAt && <p className="mt-1 text-xs text-gray-400">at {s.verifiedAt.slice(0, 10)}</p>}
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    {s.verificationStatus !== 'verified' && (
                      <Button variant="secondary" disabled={busy} onClick={() => setConfirmVerify({ scholar: s, next: 'verified' })}>
                        Verify
                      </Button>
                    )}
                    {s.verificationStatus === 'verified' && (
                      <Button variant="secondary" disabled={busy} onClick={() => setConfirmVerify({ scholar: s, next: 'revoked' })}>
                        Revoke
                      </Button>
                    )}
                    <Button variant="ghost" onClick={() => openEdit(s)}>
                      Edit
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {(creating || editing) && (
        <Modal title={editing ? `Edit ${editing.name}` : 'New scholar profile'} onClose={() => { setCreating(false); setEditing(null) }}>
          <Field label="Name">
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </Field>
          <Field label="Name (Arabic)">
            <Input value={form.nameAr} onChange={(e) => setForm({ ...form, nameAr: e.target.value })} />
          </Field>
          <Field label="Biography">
            <Textarea rows={3} value={form.biography} onChange={(e) => setForm({ ...form, biography: e.target.value })} />
          </Field>
          <Field label="Qualifications (verified facts only — never generated)">
            <Textarea rows={2} value={form.qualifications} onChange={(e) => setForm({ ...form, qualifications: e.target.value })} />
          </Field>
          <Field label="Areas of expertise">
            <Input value={form.areasOfExpertise} onChange={(e) => setForm({ ...form, areasOfExpertise: e.target.value })} />
          </Field>
          <ModalActions
            busy={busy}
            onCancel={() => {
              setCreating(false)
              setEditing(null)
            }}
            onConfirm={save}
            confirmLabel={editing ? 'Save changes' : 'Create (unverified)'}
            disabled={!form.name.trim()}
          />
        </Modal>
      )}

      <ConfirmDialog
        open={!!confirmVerify}
        title={confirmVerify?.next === 'verified' ? 'Verify this scholar?' : 'Revoke verification?'}
        message={
          confirmVerify?.next === 'verified'
            ? `“${confirmVerify.scholar.name}” will be marked verified and may be linked to Fatwa review records. The action is audited.`
            : `“${confirmVerify?.scholar.name}” will no longer count as verified for future review assignments. The action is audited.`
        }
        busy={busy}
        onCancel={() => setConfirmVerify(null)}
        onConfirm={runVerify}
      />
    </div>
  )
}
