import { useCallback, useEffect, useRef, useState } from 'react'
import { api, apiUpload } from '../../api/client'
import type { Envelope, PageMeta } from '../../api/client'
import type { MediaView } from '../../api/types'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Pagination,
  Select,
  Spinner,
  StatusPill,
  useToast,
} from '../../components/ui'

const PURPOSES = [
  ['book_pdf', 'Book PDF'],
  ['book_epub', 'Book EPUB'],
  ['cover', 'Book cover (image)'],
  ['subject_image', 'Subject image (image)'],
]

/** Uploads + inventory (Module E). Private by default; scan pipeline is fail-closed. */
export default function FilesPage() {
  const toast = useToast()
  const fileRef = useRef<HTMLInputElement>(null)
  const [rows, setRows] = useState<MediaView[]>([])
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [purpose, setPurpose] = useState('book_pdf')
  const [filterPurpose, setFilterPurpose] = useState('')
  const [filterScan, setFilterScan] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [dragOver, setDragOver] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const query = new URLSearchParams({ page: String(page), size: '20' })
      if (filterPurpose) query.set('purpose', filterPurpose)
      if (filterScan) query.set('scanStatus', filterScan)
      const res: Envelope<MediaView[]> = await api(`/admin/files?${query.toString()}`)
      setRows(res.data)
      setMeta(res.meta || null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [page, filterPurpose, filterScan])

  useEffect(() => {
    load()
  }, [load])

  const upload = useCallback(
    async (file: File) => {
      const isImage = purpose === 'cover' || purpose === 'subject_image'
      const ext = file.name.toLowerCase().split('.').pop() || ''
      const okExt = isImage ? ['jpg', 'jpeg', 'png'].includes(ext) : purpose === 'book_pdf' ? ext === 'pdf' : purpose === 'book_epub' ? ext === 'epub' : true
      if (!okExt) {
        toast.push('error', `Extension “.${ext}” does not match the selected purpose`)
        return
      }
      const form = new FormData()
      form.append('file', file)
      form.append('purpose', purpose)
      setProgress(0)
      try {
        const res = await apiUpload<MediaView>('/admin/files', form, setProgress)
        const scan = res.data.scanStatus
        toast.push(
          scan === 'clean' ? 'success' : 'error',
          scan === 'clean'
            ? `Uploaded ${file.name} — scan clean`
            : `Uploaded ${file.name} — scan verdict: ${scan} (kept in quarantine, never served)`,
        )
        await load()
      } catch (e) {
        toast.push('error', e instanceof Error ? e.message : 'Upload failed')
      } finally {
        setProgress(null)
        if (fileRef.current) fileRef.current.value = ''
      }
    },
    [purpose, toast, load],
  )

  const downloadToken = async (m: MediaView) => {
    try {
      const res = await api<{ url: string; expiresAt: string }>(`/admin/files/${m.mediaId}/download-token`, { method: 'POST' })
      window.open(res.data.url, '_blank', 'noopener')
      toast.push('success', 'Signed URL opened (expires ≤ 15 min)')
    } catch (e) {
      toast.push('error', e instanceof Error ? e.message : 'Download refused')
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Files &amp; uploads</h1>
        <p className="text-sm text-gray-500">
          Every upload is validated (extension, magic bytes, size, quota), stored privately, scanned, and only then
          promoted. Quarantined files are never exposed.
        </p>
      </div>

      <Card>
        <div
          className={`rounded-lg border-2 border-dashed p-6 text-center transition ${
            dragOver ? 'border-emerald-deep bg-emerald-deep/5' : 'border-gray-300'
          }`}
          onDragOver={(e) => {
            e.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragOver(false)
            const f = e.dataTransfer.files?.[0]
            if (f) upload(f)
          }}
        >
          <p className="text-sm text-gray-600">Drop a file here, or</p>
          <div className="mt-3 flex flex-wrap items-center justify-center gap-2">
            <Select value={purpose} onChange={(e) => setPurpose(e.target.value)} aria-label="Upload purpose">
              {PURPOSES.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </Select>
            <Button variant="gold" onClick={() => fileRef.current?.click()} disabled={progress !== null}>
              Choose file…
            </Button>
            <input
              ref={fileRef}
              type="file"
              className="hidden"
              accept={purpose === 'cover' || purpose === 'subject_image' ? 'image/png,image/jpeg' : purpose === 'book_pdf' ? '.pdf' : '.epub'}
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) upload(f)
              }}
            />
          </div>
          {progress !== null && (
            <div className="mx-auto mt-4 max-w-xs">
              <div className="h-2 overflow-hidden rounded bg-gray-200">
                <div className="h-full bg-emerald-deep transition-all" style={{ width: `${progress}%` }} />
              </div>
              <p className="mt-1 text-xs text-gray-500">Uploading… {progress}%</p>
            </div>
          )}
          <p className="mt-3 text-xs text-gray-400">
            Dev scanner: “test-only-magic-scanner” — verifies magic bytes and the EICAR test string only. A real AV
            engine is an OD-06 production requirement; publishing stays fail-closed either way.
          </p>
        </div>
      </Card>

      <Card className="flex flex-wrap items-end gap-2">
        <Field label="Filter purpose">
          <Select value={filterPurpose} onChange={(e) => { setFilterPurpose(e.target.value); setPage(1) }}>
            <option value="">All purposes</option>
            {PURPOSES.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Filter scan state">
          <Select value={filterScan} onChange={(e) => { setFilterScan(e.target.value); setPage(1) }}>
            <option value="">Any</option>
            <option value="clean">clean</option>
            <option value="pending">pending</option>
            <option value="infected">infected</option>
            <option value="rejected">rejected</option>
          </Select>
        </Field>
      </Card>

      {loading ? (
        <Spinner label="Loading files…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : rows.length === 0 ? (
        <EmptyState title="No uploads yet" hint="Upload the first cover or book file above." />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">File</th>
                <th className="px-4 py-3">Purpose</th>
                <th className="px-4 py-3">Size</th>
                <th className="px-4 py-3">Scan</th>
                <th className="px-4 py-3">Scanner</th>
                <th className="px-4 py-3">Uploaded</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((m) => (
                <tr key={m.mediaId} className="hover:bg-gray-50">
                  <td className="max-w-xs px-4 py-3">
                    <p className="truncate font-medium text-gray-800">{m.originalFilename || m.mediaId}</p>
                    <p className="text-xs text-gray-500">{m.mime}</p>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{m.purpose || '—'}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {m.sizeBytes ? `${(m.sizeBytes / 1024).toFixed(0)} KB` : '—'}
                  </td>
                  <td className="px-4 py-3">
                    <StatusPill status={m.scanStatus} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">{m.scanner || '—'}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{new Date(m.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3 text-right">
                    {m.scanStatus === 'clean' ? (
                      <Button variant="ghost" onClick={() => downloadToken(m)}>
                        Signed URL
                      </Button>
                    ) : (
                      <span className="text-xs italic text-gray-400">quarantined</span>
                    )}
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
