import { useEffect, useState } from 'react'
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { createContext, useCallback, useContext } from 'react'

// ---------- primitives ----------

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'gold'

export function Button({
  variant = 'primary',
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  const styles: Record<ButtonVariant, string> = {
    primary: 'bg-emerald-deep text-white hover:bg-emerald-900 disabled:bg-emerald-deep/50',
    secondary: 'bg-white text-emerald-deep border border-emerald-deep/30 hover:border-emerald-deep',
    danger: 'bg-red-700 text-white hover:bg-red-800 disabled:bg-red-700/50',
    ghost: 'bg-transparent text-emerald-deep hover:bg-emerald-deep/5',
    gold: 'bg-gold-islamic text-emerald-deep hover:brightness-95 font-medium',
  }
  return (
    <button
      {...rest}
      className={`inline-flex items-center justify-center gap-1.5 rounded px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${styles[variant]} ${className}`}
    />
  )
}

export function Input({ className = '', ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...rest}
      className={`w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder:text-gray-400 focus:border-emerald-deep focus:outline-none focus:ring-1 focus:ring-emerald-deep ${className}`}
    />
  )
}

export function Select({ className = '', ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...rest}
      className={`rounded border border-gray-300 bg-white px-2.5 py-2 text-sm text-gray-900 focus:border-emerald-deep focus:outline-none ${className}`}
    />
  )
}

export function Textarea({ className = '', ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...rest}
      className={`w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-emerald-deep focus:outline-none ${className}`}
    />
  )
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="mb-3 block text-sm">
      <span className="mb-1 block font-medium text-gray-700">{label}</span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-700">{error}</span>}
    </label>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-lg border border-gray-200 bg-white p-5 shadow-sm ${className}`}>{children}</div>
}

export function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    draft: 'bg-gray-100 text-gray-700',
    in_editorial_review: 'bg-amber-100 text-amber-800',
    in_editorial: 'bg-amber-100 text-amber-800',
    in_scholarly_review: 'bg-purple-100 text-purple-800',
    in_scholarly: 'bg-purple-100 text-purple-800',
    in_review: 'bg-amber-100 text-amber-800',
    approved: 'bg-sky-100 text-sky-800',
    published: 'bg-emerald-100 text-emerald-800',
    active: 'bg-emerald-100 text-emerald-800',
    archived: 'bg-gray-200 text-gray-600',
    suspended: 'bg-red-100 text-red-800',
    hidden_unconfirmed: 'bg-gray-200 text-gray-500',
    clean: 'bg-emerald-100 text-emerald-800',
    infected: 'bg-red-100 text-red-800',
    rejected: 'bg-amber-100 text-amber-800',
    pending: 'bg-gray-100 text-gray-600',
    scheduled: 'bg-sky-100 text-sky-800',
    pending_review: 'bg-amber-100 text-amber-800',
    requested: 'bg-amber-100 text-amber-800',
    verified: 'bg-emerald-100 text-emerald-800',
    unverified: 'bg-gray-100 text-gray-600',
    revoked: 'bg-red-100 text-red-800',
    disabled: 'bg-gray-200 text-gray-600',
  }
  const label = status.replaceAll('_', ' ')
  return (
    <span className={`inline-block whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${map[status] || 'bg-gray-100 text-gray-700'}`}>
      {label}
    </span>
  )
}

// ---------- states ----------

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-sm text-gray-500" role="status">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-emerald-deep border-t-transparent" />
      {label}
    </div>
  )
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-gray-300 bg-white px-6 py-12 text-center">
      <div className="text-2xl" aria-hidden>
        ◌
      </div>
      <p className="font-medium text-gray-800">{title}</p>
      {hint && <p className="max-w-md text-sm text-gray-500">{hint}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-800">
      <p className="font-medium">Something went wrong</p>
      <p className="mt-1">{message}</p>
      {onRetry && (
        <div className="mt-3">
          <Button variant="secondary" onClick={onRetry}>
            Retry
          </Button>
        </div>
      )}
    </div>
  )
}

export function PermissionDenied({ needed }: { needed?: string }) {
  return (
    <div role="alert" className="mx-auto mt-16 max-w-md rounded-lg border border-amber-200 bg-amber-50 p-6 text-center">
      <div className="text-2xl" aria-hidden>
        ⚠
      </div>
      <h2 className="mt-2 font-semibold text-amber-900">Permission required</h2>
      <p className="mt-1 text-sm text-amber-800">
        Your account does not hold the permission needed for this area
        {needed ? ` (${needed})` : ''}. Ask a Super Admin if you believe this is an error.
      </p>
    </div>
  )
}

// ---------- confirm dialog ----------

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  danger,
  requireReason,
  busy,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  requireReason?: boolean
  busy?: boolean
  onConfirm: (reason?: string) => void
  onCancel: () => void
}) {
  const [reason, setReason] = useState('')
  useEffect(() => {
    if (open) setReason('')
  }, [open])
  if (!open) return null
  const reasonTooShort = requireReason && reason.trim().length < 10
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h3 className={`text-lg font-semibold ${danger ? 'text-red-800' : 'text-gray-900'}`}>{title}</h3>
        <p className="mt-2 text-sm text-gray-600">{message}</p>
        {requireReason && (
          <label className="mt-4 block text-sm">
            <span className="mb-1 block font-medium text-gray-700">Reason (min 10 characters, recorded in the audit log)</span>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
          </label>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            disabled={busy || reasonTooShort}
            onClick={() => onConfirm(requireReason ? reason.trim() : undefined)}
          >
            {busy ? 'Working…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}

// ---------- toasts ----------

interface Toast {
  id: number
  kind: 'success' | 'error'
  text: string
}

const ToastContext = createContext<{ push: (kind: Toast['kind'], text: string) => void } | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const push = useCallback((kind: Toast['kind'], text: string) => {
    const id = Date.now() + Math.random()
    setToasts((t) => [...t, { id, kind, text }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 5000)
  }, [])
  return (
    <ToastContext.Provider value={{ push }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[60] flex w-80 flex-col gap-2" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            className={`rounded px-4 py-3 text-sm shadow-lg ${
              t.kind === 'success' ? 'bg-emerald-deep text-white' : 'bg-red-700 text-white'
            }`}
          >
            {t.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}

// ---------- pagination ----------

export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements?: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return totalElements !== undefined ? (
      <p className="py-3 text-xs text-gray-500">{totalElements} record(s)</p>
    ) : null
  }
  return (
    <div className="flex items-center justify-between py-3 text-sm text-gray-600">
      <span>
        Page {page} of {totalPages}
        {totalElements !== undefined ? ` · ${totalElements} records` : ''}
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page <= 1} onClick={() => onChange(page - 1)}>
          ← Prev
        </Button>
        <Button variant="secondary" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
          Next →
        </Button>
      </div>
    </div>
  )
}
