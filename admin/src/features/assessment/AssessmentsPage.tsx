import { useCallback, useEffect, useState } from 'react'
import {
  createQuestion,
  createQuiz,
  decideQuestion,
  fetchAdminDecks,
  fetchQuestionVersions,
  fetchQuiz,
  fetchQuizzes,
  patchAdminDeck,
  submitQuestionReview,
  transitionQuiz,
  updateQuestion,
  type DeckRow,
  type OptionRequest,
  type QuizAdminDetail,
  type QuizAdminRow,
  type QuestionVersionView,
  type WorkflowState,
} from '../../api/academy'
import { apiData } from '../../api/client'
import type { SubjectView } from '../../api/types'
import { Button, Card, ErrorState, Field, Input, Select, Spinner, StatusPill, Textarea, useToast } from '../../components/ui'
import { Modal, ModalActions } from '../academy/AcademyPage'

/**
 * Assessment management (M5 Module 13). Safety-critical CMS surface:
 *  - questions are versioned (every edit appends an immutable snapshot and
 *    resets the question's approval),
 *  - a quiz can only publish when EVERY question is editorially approved by
 *    a reviewer other than its author (enforced server-side; the UI mirrors it),
 *  - correct answers/explanations are server-only data — the CMS shows them to
 *    editors, but no public payload ever carries them.
 */

const NEXT_ACTIONS: Record<WorkflowState, { action: string; label: string; needsNotes?: boolean }[]> = {
  draft: [{ action: 'submit-review', label: 'Submit for review' }],
  in_editorial_review: [
    { action: 'approve-editorial', label: 'Approve (editorial)' },
    { action: 'reject', label: 'Reject to draft', needsNotes: true },
  ],
  approved: [{ action: 'publish', label: 'Publish' }],
  published: [{ action: 'unpublish', label: 'Unpublish' }],
  archived: [],
}

const REVIEW_ACTIONS: Record<string, { label: string; run: 'submit' | 'approve' | 'reject' }[]> = {
  draft: [{ label: 'Submit for review', run: 'submit' }],
  pending_review: [
    { label: 'Approve', run: 'approve' },
    { label: 'Reject', run: 'reject' },
  ],
  approved: [],
}

type Tab = 'quizzes' | 'decks'

export default function AssessmentsPage() {
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('quizzes')
  const [rows, setRows] = useState<QuizAdminRow[]>([])
  const [decks, setDecks] = useState<DeckRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [detail, setDetail] = useState<QuizAdminDetail | null>(null)
  const [creatingQuiz, setCreatingQuiz] = useState(false)
  const [quizForm, setQuizForm] = useState({ title: '', subjectSlug: 'cat_aqeedah', passScorePct: '60', timeLimitSeconds: '' })
  const [subjects, setSubjects] = useState<SubjectView[]>([])
  const [questionForm, setQuestionForm] = useState<null | {
    questionId: string | null
    prompt: string
    promptAr: string
    explanation: string
    changeNote: string
    options: OptionRequest[]
  }>(null)
  const [versions, setVersions] = useState<{ questionId: string; data: QuestionVersionView[] } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [quizPage, deckPage] = await Promise.all([fetchQuizzes(), fetchAdminDecks()])
      setRows(quizPage.data)
      setDecks(deckPage.data)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    apiData<SubjectView[]>('/admin/subjects')
      .then(setSubjects)
      .catch(() => setSubjects([]))
  }, [])

  const openDetail = async (quizId: string) => {
    setBusy(true)
    try {
      setDetail(await fetchQuiz(quizId))
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Load failed')
    } finally {
      setBusy(false)
    }
  }

  const submitQuiz = async () => {
    setBusy(true)
    try {
      await createQuiz({
        title: quizForm.title.trim(),
        subjectSlug: quizForm.subjectSlug,
        passScorePct: quizForm.passScorePct ? Number(quizForm.passScorePct) : null,
        timeLimitSeconds: quizForm.timeLimitSeconds ? Number(quizForm.timeLimitSeconds) : null,
      })
      toast.push('success', 'Quiz created as draft (audited)')
      setCreatingQuiz(false)
      setQuizForm({ title: '', subjectSlug: 'cat_aqeedah', passScorePct: '60', timeLimitSeconds: '' })
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Create failed')
    } finally {
      setBusy(false)
    }
  }

  const runQuizTransition = async (action: string, notes?: string) => {
    if (!detail) return
    setBusy(true)
    try {
      await transitionQuiz(detail.quizId, action, notes)
      toast.push('success', 'Transition applied (audited)')
      setDetail(await fetchQuiz(detail.quizId))
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Transition failed')
    } finally {
      setBusy(false)
    }
  }

  const runQuestionAction = async (questionId: string, run: 'submit' | 'approve' | 'reject') => {
    setBusy(true)
    try {
      if (run === 'submit') await submitQuestionReview(questionId)
      else await decideQuestion(questionId, run === 'approve')
      toast.push('success', run === 'submit' ? 'Sent for accuracy review' : 'Decision recorded (audited)')
      if (detail) setDetail(await fetchQuiz(detail.quizId))
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const openVersions = async (questionId: string) => {
    setBusy(true)
    try {
      setVersions({ questionId, data: await fetchQuestionVersions(questionId) })
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Load failed')
    } finally {
      setBusy(false)
    }
  }

  const saveQuestion = async () => {
    if (!detail || !questionForm) return
    setBusy(true)
    try {
      const body = {
        prompt: questionForm.prompt.trim(),
        promptAr: questionForm.promptAr || null,
        explanation: questionForm.explanation.trim(),
        changeNote: questionForm.changeNote || null,
        options: questionForm.options,
      }
      if (questionForm.questionId) {
        await updateQuestion(questionForm.questionId, body)
        toast.push('success', 'Question edited — approval was reset to draft (audited)')
      } else {
        await createQuestion(detail.quizId, body)
        toast.push('success', 'Question created (v1, draft)')
      }
      setQuestionForm(null)
      setDetail(await fetchQuiz(detail.quizId))
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  const setDeckState = async (deck: DeckRow, publishState: string) => {
    setBusy(true)
    try {
      await patchAdminDeck(deck.deckId, { publishState })
      toast.push('success', 'Deck state updated (audited)')
      await load()
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Update failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading assessments…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Knowledge assessment</h1>
          <p className="text-sm text-gray-500">
            Versioned question bank with mandatory accuracy review before publication. Correct answers and explanations
            are served only inside a submitted attempt — never in public payloads.
          </p>
        </div>
        {tab === 'quizzes' && <Button onClick={() => setCreatingQuiz(true)}>New quiz</Button>}
      </div>

      <div className="flex gap-2" role="tablist" aria-label="Assessment sections">
        {(['quizzes', 'decks'] as Tab[]).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`rounded px-3 py-1.5 text-sm ${tab === t ? 'bg-emerald-deep text-white' : 'bg-white text-gray-600 ring-1 ring-gray-200'}`}
          >
            {t === 'quizzes' ? 'Quizzes' : 'Flashcard decks'}
          </button>
        ))}
      </div>

      {tab === 'quizzes' ? (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Quiz</th>
                <th className="px-4 py-3">Questions</th>
                <th className="px-4 py-3">Time limit</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-400">
                    No quizzes yet.
                  </td>
                </tr>
              )}
              {rows.map((q) => (
                <tr key={q.quizId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">{q.title}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {q.approvedQuestionCount}/{q.questionCount} approved
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{q.timeLimitSeconds ? `${q.timeLimitSeconds}s` : '—'}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={q.status} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="ghost" onClick={() => openDetail(q.quizId)}>
                      Manage
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">Deck</th>
                <th className="px-4 py-3">Cards</th>
                <th className="px-4 py-3">Owner</th>
                <th className="px-4 py-3">State</th>
                <th className="px-4 py-3 text-right">Transition</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {decks.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-400">
                    No admin decks yet — create one via the API or seed data.
                  </td>
                </tr>
              )}
              {decks.map((d) => (
                <tr key={d.deckId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">{d.title}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{d.cardCount}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{d.ownerType}</td>
                  <td className="px-4 py-3">
                    <StatusPill status={d.publishState} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Select
                      aria-label={`Publish state of ${d.title}`}
                      value={d.publishState}
                      onChange={(e) => setDeckState(d, e.target.value)}
                      className="w-36"
                    >
                      {['draft', 'approved', 'published', 'disabled'].map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </Select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {/* create quiz */}
      {creatingQuiz && (
        <Modal title="New quiz (draft)" onClose={() => setCreatingQuiz(false)}>
          <Field label="Title">
            <Input value={quizForm.title} onChange={(e) => setQuizForm({ ...quizForm, title: e.target.value })} />
          </Field>
          <Field label="Category">
            <Select value={quizForm.subjectSlug} onChange={(e) => setQuizForm({ ...quizForm, subjectSlug: e.target.value })}>
              {subjects.map((s) => (
                <option key={s.slug} value={s.slug}>
                  {s.name}
                </option>
              ))}
            </Select>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Pass score (%)">
              <Input
                type="number"
                value={quizForm.passScorePct}
                onChange={(e) => setQuizForm({ ...quizForm, passScorePct: e.target.value })}
              />
            </Field>
            <Field label="Time limit (seconds, optional)">
              <Input
                type="number"
                value={quizForm.timeLimitSeconds}
                onChange={(e) => setQuizForm({ ...quizForm, timeLimitSeconds: e.target.value })}
              />
            </Field>
          </div>
          <ModalActions
            busy={busy}
            onCancel={() => setCreatingQuiz(false)}
            onConfirm={submitQuiz}
            confirmLabel="Create draft"
            disabled={!quizForm.title.trim()}
          />
        </Modal>
      )}

      {/* quiz detail */}
      {detail && (
        <Modal title={detail.title} onClose={() => setDetail(null)} wide>
          <p className="text-sm text-gray-500">
            Pass score {detail.passScorePct}% · status <StatusPill status={detail.status} />
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {(NEXT_ACTIONS[detail.status] ?? []).map((a) => (
              <Button
                key={a.action}
                variant={a.action === 'publish' ? 'primary' : 'secondary'}
                onClick={() =>
                  runQuizTransition(a.action, a.needsNotes ? window.prompt('Rejection notes (required)') ?? undefined : undefined)
                }
              >
                {a.label}
              </Button>
            ))}
            <Button variant="ghost" onClick={() => setQuestionForm(emptyQuestion())}>
              + Question
            </Button>
          </div>

          <div className="mt-4 space-y-2">
            {detail.questions.map((q, i) => (
              <div key={q.questionId} className="rounded border border-gray-200 px-3 py-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium text-gray-800">
                    {i + 1}. {q.prompt}
                  </p>
                  <div className="flex items-center gap-2">
                    <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">v{q.version}</span>
                    <StatusPill status={q.reviewState} />
                  </div>
                </div>
                <p className="mt-1 text-xs text-gray-500">Explanation (server-only data): {q.explanation}</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {(REVIEW_ACTIONS[q.reviewState] ?? []).map((a) => (
                    <Button
                      key={a.run}
                      variant="secondary"
                      disabled={busy}
                      onClick={() => {
                        if (a.run === 'reject' && !window.confirm('Reject this question back to draft?')) return
                        void runQuestionAction(q.questionId, a.run)
                      }}
                    >
                      {a.label}
                    </Button>
                  ))}
                  <Button variant="ghost" disabled={busy} onClick={() => openVersions(q.questionId)}>
                    Versions
                  </Button>
                  <Button
                    variant="ghost"
                    disabled={busy}
                    onClick={() =>
                      setQuestionForm({
                        questionId: q.questionId,
                        prompt: q.prompt,
                        promptAr: q.promptAr ?? '',
                        explanation: q.explanation,
                        changeNote: '',
                        options: q.options.map((o) => ({ optionText: o.optionText, correct: o.correct })),
                      })
                    }
                  >
                    Edit (new version)
                  </Button>
                </div>
              </div>
            ))}
            {detail.questions.length === 0 && (
              <p className="text-sm text-gray-400">No questions yet. A quiz cannot publish without approved questions.</p>
            )}
          </div>
        </Modal>
      )}

      {/* question editor */}
      {questionForm && (
        <Modal
          title={questionForm.questionId ? 'Edit question (creates a new version)' : 'New question'}
          onClose={() => setQuestionForm(null)}
          wide
        >
          <Field label="Prompt">
            <Textarea rows={2} value={questionForm.prompt} onChange={(e) => setQuestionForm({ ...questionForm, prompt: e.target.value })} />
          </Field>
          <Field label="Prompt (Arabic, optional)">
            <Input value={questionForm.promptAr} onChange={(e) => setQuestionForm({ ...questionForm, promptAr: e.target.value })} />
          </Field>
          <Field label="Answer explanation (served only after submission)">
            <Textarea
              rows={2}
              value={questionForm.explanation}
              onChange={(e) => setQuestionForm({ ...questionForm, explanation: e.target.value })}
            />
          </Field>
          <div className="space-y-2">
            <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Options (mark exactly one correct)</p>
            {questionForm.options.map((o, i) => (
              <div key={i} className="flex items-center gap-2">
                <input
                  type="radio"
                  name="correct-option"
                  aria-label={`Option ${i + 1} is correct`}
                  checked={o.correct}
                  onChange={() =>
                    setQuestionForm({
                      ...questionForm,
                      options: questionForm.options.map((x, j) => ({ ...x, correct: j === i })),
                    })
                  }
                />
                <Input
                  value={o.optionText}
                  aria-label={`Option ${i + 1} text`}
                  onChange={(e) =>
                    setQuestionForm({
                      ...questionForm,
                      options: questionForm.options.map((x, j) => (j === i ? { ...x, optionText: e.target.value } : x)),
                    })
                  }
                />
                {questionForm.options.length > 2 && (
                  <Button variant="ghost" onClick={() => setQuestionForm({ ...questionForm, options: questionForm.options.filter((_, j) => j !== i) })}>
                    ✕
                  </Button>
                )}
              </div>
            ))}
            {questionForm.options.length < 6 && (
              <Button variant="secondary" onClick={() => setQuestionForm({ ...questionForm, options: [...questionForm.options, { optionText: '', correct: false }] })}>
                + Option
              </Button>
            )}
          </div>
          {questionForm.questionId && (
            <Field label="Change note (recorded in the version history)">
              <Input value={questionForm.changeNote} onChange={(e) => setQuestionForm({ ...questionForm, changeNote: e.target.value })} />
            </Field>
          )}
          <ModalActions
            busy={busy}
            onCancel={() => setQuestionForm(null)}
            onConfirm={saveQuestion}
            confirmLabel={questionForm.questionId ? 'Save new version' : 'Create question'}
            disabled={
              !questionForm.prompt.trim() ||
              !questionForm.explanation.trim() ||
              questionForm.options.some((o) => !o.optionText.trim()) ||
              questionForm.options.filter((o) => o.correct).length !== 1
            }
          />
        </Modal>
      )}

      {/* version history */}
      {versions && (
        <Modal title={`Version history (${versions.data.length})`} onClose={() => setVersions(null)} wide>
          <div className="space-y-2">
            {versions.data.map((v) => (
              <div key={v.version} className="rounded border border-gray-200 px-3 py-2 text-sm">
                <p className="font-medium text-gray-800">
                  v{v.version} — {v.changeNote ?? '(no note)'}
                </p>
                <p className="text-xs text-gray-500">
                  {v.editedBy ?? 'unknown'} · {v.editedAt}
                </p>
                <p className="mt-1 text-xs text-gray-600">{String((v.snapshot as { prompt?: string }).prompt ?? '')}</p>
              </div>
            ))}
          </div>
        </Modal>
      )}
    </div>
  )
}

function emptyQuestion() {
  return {
    questionId: null as string | null,
    prompt: '',
    promptAr: '',
    explanation: '',
    changeNote: '',
    options: [
      { optionText: '', correct: true },
      { optionText: '', correct: false },
    ] as OptionRequest[],
  }
}
