import { useCallback, useEffect, useState } from 'react'
import {
  addChapter,
  addLesson,
  createCourse,
  fetchCourse,
  fetchCourses,
  transitionCourse,
  type CourseAdminDetail,
  type CourseAdminRow,
  type WorkflowState,
} from '../../api/academy'
import { apiData } from '../../api/client'
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
 * Academy management (M5 Module 10). Courses follow the approved book state
 * machine; structure editing is draft-only and every transition is audited
 * server-side. Instructor fields are display-only recorded text — the CMS
 * never generates qualifications, and certificates stay out of scope (OD-11).
 */

const NEXT_ACTIONS: Record<WorkflowState, { action: string; label: string; needsNotes?: boolean }[]> = {
  draft: [
    { action: 'submit-review', label: 'Submit for review' },
    { action: 'archive', label: 'Archive' },
  ],
  in_editorial_review: [
    { action: 'approve-editorial', label: 'Approve (editorial)' },
    { action: 'reject', label: 'Reject to draft', needsNotes: true },
  ],
  approved: [
    { action: 'publish', label: 'Publish' },
    { action: 'archive', label: 'Archive' },
  ],
  published: [{ action: 'unpublish', label: 'Unpublish' }],
  archived: [],
}

const emptyForm = {
  title: '',
  subjectSlug: 'cat_learning_academy',
  description: '',
  language: 'en',
  level: 'beginner',
  instructorDisplayName: '',
  instructorCredentialsNote: '',
  estimatedHours: '',
}

export default function AcademyPage() {
  const toast = useToast()
  const [rows, setRows] = useState<CourseAdminRow[]>([])
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<CourseAdminDetail | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [subjects, setSubjects] = useState<SubjectView[]>([])
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState<{ action: string; label: string; needsNotes: boolean } | null>(null)
  const [notes, setNotes] = useState('')
  const [chapterForm, setChapterForm] = useState<{ title: string; description: string } | null>(null)
  const [lessonForm, setLessonForm] = useState<{
    chapterId: string
    title: string
    lessonType: string
    bodyText: string
    videoUrl: string
    durationMinutes: string
  } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const page = await fetchCourses(status || undefined)
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
    apiData<SubjectView[]>('/admin/subjects')
      .then(setSubjects)
      .catch(() => setSubjects([]))
  }, [])

  const openDetail = async (courseId: string) => {
    setBusy(true)
    try {
      setDetail(await fetchCourse(courseId))
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Load failed')
    } finally {
      setBusy(false)
    }
  }

  const submitCreate = async () => {
    setBusy(true)
    try {
      await createCourse({
        title: form.title.trim(),
        description: form.description || null,
        language: form.language,
        level: form.level,
        subjectSlug: form.subjectSlug,
        instructorDisplayName: form.instructorDisplayName || null,
        instructorCredentialsNote: form.instructorCredentialsNote || null,
        estimatedHours: form.estimatedHours ? Number(form.estimatedHours) : null,
      })
      toast.push('success', 'Course created as draft (audited)')
      setCreating(false)
      setForm(emptyForm)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Create failed')
    } finally {
      setBusy(false)
    }
  }

  const runTransition = async () => {
    if (!detail || !confirm) return
    setBusy(true)
    try {
      await transitionCourse(detail.courseId, confirm.action, notes || undefined)
      toast.push('success', 'Transition applied (audited)')
      setConfirm(null)
      setNotes('')
      const fresh = await fetchCourse(detail.courseId)
      setDetail(fresh)
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Transition failed')
    } finally {
      setBusy(false)
    }
  }

  const submitChapter = async () => {
    if (!detail || !chapterForm) return
    setBusy(true)
    try {
      await addChapter(detail.courseId, {
        title: chapterForm.title.trim(),
        description: chapterForm.description || null,
      })
      toast.push('success', 'Chapter added (draft-only editing)')
      setChapterForm(null)
      setDetail(await fetchCourse(detail.courseId))
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Add chapter failed')
    } finally {
      setBusy(false)
    }
  }

  const submitLesson = async () => {
    if (!detail || !lessonForm) return
    setBusy(true)
    try {
      await addLesson(lessonForm.chapterId, {
        title: lessonForm.title.trim(),
        lessonType: lessonForm.lessonType,
        bodyText: lessonForm.bodyText || null,
        videoUrl: lessonForm.videoUrl || null,
        durationMinutes: lessonForm.durationMinutes ? Number(lessonForm.durationMinutes) : null,
      })
      toast.push('success', 'Lesson added')
      setLessonForm(null)
      setDetail(await fetchCourse(detail.courseId))
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Add lesson failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading courses…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Academy</h1>
          <p className="text-sm text-gray-500">
            Courses → chapters → lessons (text, video, PDF, quiz, flashcards). Structure is editable in draft only;
            publishing requires the approved workflow. Certificates are not part of the product (OD-11).
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Filter by status" className="w-44">
            <option value="">All statuses</option>
            {['draft', 'in_editorial_review', 'approved', 'published', 'archived'].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
          <Button onClick={() => setCreating(true)}>New course</Button>
        </div>
      </div>

      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[860px] text-left text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
              <th className="px-4 py-3">Course</th>
              <th className="px-4 py-3">Category</th>
              <th className="px-4 py-3">Level</th>
              <th className="px-4 py-3">Structure</th>
              <th className="px-4 py-3">Enrolled</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-400">
                  No courses yet — create the first draft.
                </td>
              </tr>
            )}
            {rows.map((c) => (
              <tr key={c.courseId} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-medium text-gray-800">{c.title}</p>
                  {c.instructorDisplayName && <p className="text-xs text-gray-400">Instructor: {c.instructorDisplayName}</p>}
                </td>
                <td className="px-4 py-3 text-xs text-gray-500">{c.subject.name}</td>
                <td className="px-4 py-3 text-xs text-gray-500">{c.level}</td>
                <td className="px-4 py-3 text-xs text-gray-500">
                  {c.chapterCount} chapters · {c.lessonCount} lessons
                </td>
                <td className="px-4 py-3 text-xs text-gray-500">{c.enrolmentCount}</td>
                <td className="px-4 py-3">
                  <StatusPill status={c.status} />
                </td>
                <td className="px-4 py-3 text-right">
                  <Button variant="ghost" onClick={() => openDetail(c.courseId)}>
                    Manage
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {/* create course */}
      {creating && (
        <Modal title="New course (draft)" onClose={() => setCreating(false)}>
          <CourseForm form={form} setForm={setForm} subjects={subjects} />
          <ModalActions
            busy={busy}
            onCancel={() => setCreating(false)}
            onConfirm={submitCreate}
            confirmLabel="Create draft"
            disabled={!form.title.trim()}
          />
        </Modal>
      )}

      {/* course detail */}
      {detail && (
        <Modal title={detail.title} onClose={() => setDetail(null)} wide>
          <p className="text-sm text-gray-500">
            {detail.subject.name} · {detail.level} · {detail.language} — status <StatusPill status={detail.status} />
          </p>
          {detail.instructorCredentialsNote && (
            <p className="mt-1 text-xs text-gray-400">Credentials (recorded text): {detail.instructorCredentialsNote}</p>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {(NEXT_ACTIONS[detail.status] ?? []).map((a) => (
              <Button
                key={a.action}
                variant={a.action === 'publish' ? 'primary' : 'secondary'}
                onClick={() => setConfirm({ action: a.action, label: a.label, needsNotes: !!a.needsNotes })}
              >
                {a.label}
              </Button>
            ))}
          </div>

          <div className="mt-4 space-y-3">
            {detail.chapters.map((ch) => (
              <div key={ch.chapterId} className="rounded border border-gray-200">
                <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
                  <p className="text-sm font-medium text-gray-800">
                    {ch.sortOrder + 1}. {ch.title}
                  </p>
                  <Button
                    variant="ghost"
                    onClick={() =>
                      setLessonForm({
                        chapterId: ch.chapterId,
                        title: '',
                        lessonType: 'text',
                        bodyText: '',
                        videoUrl: '',
                        durationMinutes: '',
                      })
                    }
                  >
                    + Lesson
                  </Button>
                </div>
                <ul className="divide-y divide-gray-50">
                  {ch.lessons.map((l) => (
                    <li key={l.lessonId} className="flex items-center justify-between px-3 py-2 text-sm">
                      <span className="text-gray-700">{l.title}</span>
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{l.lessonType}</span>
                    </li>
                  ))}
                  {ch.lessons.length === 0 && <li className="px-3 py-2 text-xs text-gray-400">No lessons yet.</li>}
                </ul>
              </div>
            ))}
            <Button variant="secondary" onClick={() => setChapterForm({ title: '', description: '' })}>
              + Chapter
            </Button>
          </div>
        </Modal>
      )}

      {/* workflow confirm */}
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.label ?? ''}
        message="Workflow transitions are recorded in the audit log with actor, before and after states."
        busy={busy}
        requireReason={confirm?.needsNotes}
        onCancel={() => {
          setConfirm(null)
          setNotes('')
        }}
        onConfirm={runTransition}
      />

      {/* chapter dialog */}
      {chapterForm && (
        <Modal title="Add chapter" onClose={() => setChapterForm(null)}>
          <Field label="Title">
            <Input
              value={chapterForm.title}
              onChange={(e) => setChapterForm((f) => (f ? { ...f, title: e.target.value } : f))}
            />
          </Field>
          <Field label="Description">
            <Textarea
              rows={2}
              value={chapterForm.description}
              onChange={(e) => setChapterForm((f) => (f ? { ...f, description: e.target.value } : f))}
            />
          </Field>
          <ModalActions
            busy={busy}
            onCancel={() => setChapterForm(null)}
            onConfirm={submitChapter}
            confirmLabel="Add chapter"
            disabled={!chapterForm.title.trim()}
          />
        </Modal>
      )}

      {/* lesson dialog */}
      {lessonForm && (
        <Modal title="Add lesson" onClose={() => setLessonForm(null)}>
          <Field label="Title">
            <Input
              value={lessonForm.title}
              onChange={(e) => setLessonForm((f) => (f ? { ...f, title: e.target.value } : f))}
            />
          </Field>
          <Field label="Lesson type">
            <Select
              value={lessonForm.lessonType}
              onChange={(e) => setLessonForm((f) => (f ? { ...f, lessonType: e.target.value } : f))}
            >
              {['text', 'video', 'pdf', 'quiz', 'flashcards'].map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
          </Field>
          {lessonForm.lessonType === 'text' && (
            <Field label="Body text">
              <Textarea
                rows={4}
                value={lessonForm.bodyText}
                onChange={(e) => setLessonForm((f) => (f ? { ...f, bodyText: e.target.value } : f))}
              />
            </Field>
          )}
          {lessonForm.lessonType === 'video' && (
            <Field label="Video URL (external reference only — OD-07)">
              <Input
                value={lessonForm.videoUrl}
                onChange={(e) => setLessonForm((f) => (f ? { ...f, videoUrl: e.target.value } : f))}
              />
            </Field>
          )}
          <Field label="Duration (minutes)">
            <Input
              type="number"
              value={lessonForm.durationMinutes}
              onChange={(e) => setLessonForm((f) => (f ? { ...f, durationMinutes: e.target.value } : f))}
            />
          </Field>
          {lessonForm.lessonType === 'quiz' && (
            <p className="text-xs text-gray-400">Create the quiz under Assessments first, then link it here in a later edit.</p>
          )}
          <ModalActions
            busy={busy}
            onCancel={() => setLessonForm(null)}
            onConfirm={submitLesson}
            confirmLabel="Add lesson"
            disabled={!lessonForm.title.trim()}
          />
        </Modal>
      )}
    </div>
  )
}

function CourseForm({
  form,
  setForm,
  subjects,
}: {
  form: typeof emptyForm
  setForm: (f: typeof emptyForm) => void
  subjects: SubjectView[]
}) {
  return (
    <>
      <Field label="Title">
        <Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
      </Field>
      <Field label="Category">
        <Select value={form.subjectSlug} onChange={(e) => setForm({ ...form, subjectSlug: e.target.value })}>
          {subjects.map((s) => (
            <option key={s.slug} value={s.slug}>
              {s.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Description">
        <Textarea rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Language">
          <Select value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })}>
            {['en', 'ar', 'ur', 'hi'].map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Level">
          <Select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })}>
            {['beginner', 'intermediate', 'advanced'].map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <Field label="Instructor display name (recorded text — never generated)">
        <Input
          value={form.instructorDisplayName}
          onChange={(e) => setForm({ ...form, instructorDisplayName: e.target.value })}
        />
      </Field>
      <Field label="Credentials note (verified facts only)">
        <Input
          value={form.instructorCredentialsNote}
          onChange={(e) => setForm({ ...form, instructorCredentialsNote: e.target.value })}
        />
      </Field>
      <Field label="Estimated hours">
        <Input
          type="number"
          value={form.estimatedHours}
          onChange={(e) => setForm({ ...form, estimatedHours: e.target.value })}
        />
      </Field>
    </>
  )
}

export function Modal({
  title,
  onClose,
  children,
  wide,
}: {
  title: string
  onClose: () => void
  children: React.ReactNode
  wide?: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className={`max-h-[85vh] w-full ${wide ? 'max-w-3xl' : 'max-w-lg'} overflow-y-auto rounded-lg bg-white p-6 shadow-xl`}>
        <div className="flex items-start justify-between gap-4">
          <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
          <Button variant="ghost" onClick={onClose} aria-label="Close">
            ✕
          </Button>
        </div>
        <div className="mt-3 space-y-3">{children}</div>
      </div>
    </div>
  )
}

export function ModalActions({
  busy,
  onCancel,
  onConfirm,
  confirmLabel,
  disabled,
}: {
  busy: boolean
  onCancel: () => void
  onConfirm: () => void
  confirmLabel: string
  disabled?: boolean
}) {
  return (
    <div className="flex justify-end gap-2 pt-2">
      <Button variant="secondary" onClick={onCancel} disabled={busy}>
        Cancel
      </Button>
      <Button onClick={onConfirm} disabled={busy || disabled}>
        {busy ? 'Working…' : confirmLabel}
      </Button>
    </div>
  )
}
