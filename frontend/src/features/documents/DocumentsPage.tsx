import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useFocusTrap } from '../../lib/useFocusTrap'
import { api, apiDownload, del, patch, post, put } from '../../lib/api'
import { date, dateTime, humanise, num, text } from '../../lib/format'
import {
  Badge,
  Button,
  Field,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextArea,
  TextInput,
} from '../../components/ui'
import { Pager, PageHeader } from '../../components/DataTable'
import { useAuth } from '../../auth/AuthProvider'
import {
  RELATED_TYPES,
  type AccessGrant,
  type AccessLevel,
  type AccessLevelFor,
  type DirectoryUser,
  type DocumentDetail,
  type DocumentOverview,
  type DocumentPage,
  type DocumentStatus,
  type DocumentVersion,
  type EditMetadata,
  type GrantAccess,
  type NewVersion,
  type PrincipalType,
  type RelatedType,
  type RoleSummary,
  type StoredDocument,
  type UploadDocument,
  type VerificationStatus,
} from './types'

const PAGE_SIZE = 20

const STATUSES: DocumentStatus[] = ['DRAFT', 'ACTIVE', 'SUPERSEDED', 'ARCHIVED']
const VERIFICATIONS: VerificationStatus[] = ['UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED']
const ACCESS_LEVELS: AccessLevel[] = ['VIEW', 'EDIT', 'MANAGE']
const PRINCIPAL_TYPES: PrincipalType[] = ['ROLE', 'USER']

const TH = 'px-4 py-3 font-medium text-ink-subtle'
const TDR = 'px-4 py-3'

/** The document types the institution already uses, offered but not imposed. */
const COMMON_TYPES = [
  'CERTIFICATE',
  'POLICY',
  'CONTRACT',
  'MINUTES',
  'NOTICE',
  'IDENTITY',
  'REPORT',
  'FORM',
  'OTHER',
]

function say(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

/** File sizes, because "fileSize: 1048576" helps nobody. */
function bytes(value: number | null | undefined): string {
  if (!value) return '—'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size >= 10 || unit === 0 ? Math.round(size) : size.toFixed(1)} ${units[unit]}`
}

/* ---------------------------------------------------------------------- shared */

/** Every document query hangs off one key, so a write can refresh all of it at once. */
function useRefreshDocuments() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['documents'] })
}

/**
 * Multipart bodies for the two endpoints that take a file.
 *
 * The metadata has to travel as a JSON part rather than as form fields, because the backend
 * validates it as a request object. `api` leaves FormData's content type alone so the browser
 * can set the boundary itself.
 */
function fileBody(file: File, metadata: object): FormData {
  const body = new FormData()
  body.append('file', file)
  body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }))
  return body
}

/* -------------------------------------------------------------------- the page */

export default function DocumentsPage() {
  const { can } = useAuth()
  const mayUpload = can('DOCUMENT_UPLOAD')
  const mayVerify = can('DOCUMENT_VERIFY')
  const [tab, setTab] = useState<'library' | 'review' | 'expiring'>('library')
  const [selected, setSelected] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  const overview = useQuery({
    queryKey: ['documents', 'overview'],
    queryFn: () => api<DocumentOverview>('/api/v1/documents/overview'),
  })

  const tabs = [
    { key: 'library' as const, label: 'Library' },
    ...(mayVerify ? [{ key: 'review' as const, label: 'To review' }] : []),
    { key: 'expiring' as const, label: 'Expiring' },
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Documents"
        description="Every file the institution holds, with its versions, its checksum and who may read it."
        actions={
          mayUpload ? (
            // This used to switch to the library and stop there, which looked like a dead
            // button: the upload form sat below the filters, unopened, on the same tab.
            <Button
              onClick={() => {
                setTab('library')
                setUploading(true)
              }}
            >
              Upload a document
            </Button>
          ) : null
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="On file" value={overview.data ? num(overview.data.totalDocuments) : null} />
        <Stat label="In circulation" value={overview.data ? num(overview.data.active) : null} />
        <Stat label="Verified" value={overview.data ? num(overview.data.verified) : null} />
        <Stat
          label="Expiring in 90 days"
          value={overview.data ? num(overview.data.expiringSoon) : null}
        />
      </div>

      <div role="tablist" aria-label="Document views" className="flex flex-wrap gap-1 border-b border-border">
        {tabs.map((entry) => (
          <button
            key={entry.key}
            role="tab"
            type="button"
            aria-selected={tab === entry.key}
            onClick={() => setTab(entry.key)}
            className={
              tab === entry.key
                ? 'border-b-2 border-primary px-4 py-2 text-sm font-medium text-primary'
                : 'border-b-2 border-transparent px-4 py-2 text-sm text-ink-muted hover:text-ink'
            }
          >
            {entry.label}
          </button>
        ))}
      </div>

      {tab === 'library' && (
        <Library
          onOpen={setSelected}
          mayUpload={mayUpload}
          uploading={uploading}
          onUploadingChange={setUploading}
        />
      )}
      {tab === 'review' && <Review onOpen={setSelected} />}
      {tab === 'expiring' && <Expiring onOpen={setSelected} />}

      {selected && (
        <DocumentDrawer
          documentId={selected}
          onClose={() => setSelected(null)}
          mayUpload={mayUpload}
          mayVerify={mayVerify}
          canDelete={can('DOCUMENT_DELETE')}
        />
      )}
    </div>
  )
}

/* -------------------------------------------------------------------- library */

function Library({
  onOpen,
  mayUpload,
  uploading,
  onUploadingChange,
}: {
  onOpen: (id: string) => void
  mayUpload: boolean
  uploading: boolean
  onUploadingChange: (open: boolean) => void
}) {
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')
  const [search, setSearch] = useState('')
  const [documentType, setDocumentType] = useState('')
  const [status, setStatus] = useState('')
  const [verificationStatus, setVerificationStatus] = useState('')

  const documents = useQuery({
    queryKey: ['documents', 'list', page, search, documentType, status, verificationStatus],
    queryFn: () =>
      api<DocumentPage>('/api/v1/documents', {
        query: {
          page,
          size: PAGE_SIZE,
          sort: 'createdAt,desc',
          term: search || undefined,
          documentType: documentType || undefined,
          status: status || undefined,
          verificationStatus: verificationStatus || undefined,
        },
      }),
  })

  const filtered = Boolean(search || documentType || status || verificationStatus)

  return (
    <>
      <Panel
        title="Search the library"
        padded={false}
        actions={
          mayUpload ? (
            <Button size="sm" onClick={() => onUploadingChange(!uploading)}>
              {uploading ? 'Cancel upload' : 'Upload a document'}
            </Button>
          ) : null
        }
      >
        <form
          className="grid gap-3 border-b border-border p-4 sm:grid-cols-2 lg:grid-cols-5"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setSearch(term.trim())
          }}
        >
          <Field label="Title or number" htmlFor="document-term">
            <TextInput
              id="document-term"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              placeholder="Birth certificate"
            />
          </Field>
          <Field label="Type" htmlFor="document-type">
            <Select
              id="document-type"
              value={documentType}
              onChange={(event) => {
                setDocumentType(event.target.value)
                setPage(0)
              }}
            >
              <option value="">Any type</option>
              {COMMON_TYPES.map((entry) => (
                <option key={entry} value={entry}>{humanise(entry)}</option>
              ))}
            </Select>
          </Field>
          <Field label="Status" htmlFor="document-status">
            <Select
              id="document-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value)
                setPage(0)
              }}
            >
              <option value="">Any status</option>
              {STATUSES.map((entry) => (
                <option key={entry} value={entry}>{humanise(entry)}</option>
              ))}
            </Select>
          </Field>
          <Field label="Checked" htmlFor="document-verification">
            <Select
              id="document-verification"
              value={verificationStatus}
              onChange={(event) => {
                setVerificationStatus(event.target.value)
                setPage(0)
              }}
            >
              <option value="">Any</option>
              {VERIFICATIONS.map((entry) => (
                <option key={entry} value={entry}>{humanise(entry)}</option>
              ))}
            </Select>
          </Field>
          <div className="flex items-end gap-2">
            <Button type="submit">Search</Button>
            {(filtered || term) && (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setTerm('')
                  setSearch('')
                  setDocumentType('')
                  setStatus('')
                  setVerificationStatus('')
                  setPage(0)
                }}
              >
                Clear
              </Button>
            )}
          </div>
        </form>

        {uploading && mayUpload && (
          <div className="border-b border-border p-5">
            <UploadForm onDone={() => { onUploadingChange(false); setPage(0) }} />
          </div>
        )}

        <QueryBoundary
          isLoading={documents.isLoading}
          error={documents.error}
          data={documents.data}
          onRetry={() => void documents.refetch()}
          loadingRows={4}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">
                {filtered
                  ? 'No documents match those filters.'
                  : 'No documents yet. Upload one to start the library.'}
              </p>
            </div>
          }
        >
          {(page_) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <th className={TH}>Document</th>
                      <th className={TH}>Type</th>
                      <th className={`${TH} hidden md:table-cell`}>Owner</th>
                      <th className={`${TH} text-right`}>Size</th>
                      <th className={`${TH} hidden sm:table-cell text-right`}>Version</th>
                      <th className={TH}>Checked</th>
                      <th className={TH}>Status</th>
                      <th className={`${TH} hidden lg:table-cell`}>Expires</th>
                      <th className={TH} />
                    </tr>
                  </thead>
                  <tbody>
                    {page_.data.map((row) => (
                      <tr key={row.id} className="border-b border-border last:border-0">
                        <td className={TDR}>
                          <p className="font-medium text-ink">{row.title}</p>
                          <p className="text-xs text-ink-muted">
                            {row.documentNumber} · {humanise(row.documentType)}
                          </p>
                        </td>
                        <td className={TDR}>{text(row.category)}</td>
                        <td className={`${TDR} hidden md:table-cell`}>
                          {text(row.ownerName ?? row.ownerDepartmentName)}
                        </td>
                        <td className={`${TDR} nums text-right`}>{bytes(row.fileSize)}</td>
                        <td className={`${TDR} nums hidden sm:table-cell text-right`}>
                          v{row.currentVersion}
                        </td>
                        <td className={TDR}>
                          <VerificationBadge row={row} />
                        </td>
                        <td className={TDR}><StatusBadge status={row.status} /></td>
                        <td className={`${TDR} hidden lg:table-cell`}>
                          <ExpiryCell expiresOn={row.expiresOn} expired={row.expired} />
                        </td>
                        <td className={`${TDR} text-right`}>
                          <Button size="sm" variant="ghost" onClick={() => onOpen(row.id)}>Open</Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={page_} onChange={setPage} />
            </>
          )}
        </QueryBoundary>
      </Panel>
    </>
  )
}

/* --------------------------------------------------------------------- review */

function Review({ onOpen }: { onOpen: (id: string) => void }) {
  const [pageIndex, setPageIndex] = useState(0)
  const queue = useQuery({
    queryKey: ['documents', 'review', pageIndex],
    queryFn: () =>
      api<DocumentPage>('/api/v1/documents', {
        query: {
          page: pageIndex,
          size: PAGE_SIZE,
          sort: 'createdAt,asc',
          verificationStatus: 'UNVERIFIED',
        },
      }),
  })

  return (
    <Panel
      title="Waiting to be checked"
      description="Anything uploaded but not yet approved or rejected."
      padded={false}
    >
      <QueryBoundary
        isLoading={queue.isLoading}
        error={queue.error}
        data={queue.data}
        onRetry={() => void queue.refetch()}
        loadingRows={3}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">Nothing is waiting to be checked.</p>
          </div>
        }
      >
        {(page) => (
          <>
            <ul className="divide-y divide-border">
              {page.data.map((row) => (
                <li key={row.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                  <div>
                    <p className="text-sm font-medium text-ink">{row.title}</p>
                    <p className="text-xs text-ink-muted">
                      {row.documentNumber} · {humanise(row.documentType)} · uploaded{' '}
                      {dateTime(row.createdAt)}
                    </p>
                  </div>
                  <Button size="sm" variant="secondary" onClick={() => onOpen(row.id)}>
                    Review
                  </Button>
                </li>
              ))}
            </ul>
            <Pager page={page} onChange={setPageIndex} />
          </>
        )}
      </QueryBoundary>
    </Panel>
  )
}

/* ------------------------------------------------------------------- expiring */

function Expiring({ onOpen }: { onOpen: (id: string) => void }) {
  const [days, setDays] = useState('90')

  const expiring = useQuery({
    queryKey: ['documents', 'expiring', days],
    queryFn: () =>
      api<StoredDocument[]>('/api/v1/documents/expiring', { query: { days } }),
  })

  return (
    <Panel
      title="Going out of date"
      description="Documents with an expiry date coming up. Anything past its date is already useless."
      padded={false}
      actions={
        <Field label="Within" htmlFor="expiry-days">
          <Select
            id="expiry-days"
            value={days}
            onChange={(event) => setDays(event.target.value)}
          >
            <option value="30">30 days</option>
            <option value="90">90 days</option>
            <option value="180">180 days</option>
            <option value="365">A year</option>
            <option value="730">Two years</option>
          </Select>
        </Field>
      }
    >
      <QueryBoundary
        isLoading={expiring.isLoading}
        error={expiring.error}
        data={expiring.data}
        onRetry={() => void expiring.refetch()}
        loadingRows={3}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">Nothing expires in that window.</p>
          </div>
        }
      >
        {(list) => (
          <ul className="divide-y divide-border">
            {list.map((row) => (
              <li key={row.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div>
                  <p className="text-sm font-medium text-ink">{row.title}</p>
                  <p className="text-xs text-ink-muted">
                    {row.documentNumber} · {text(row.ownerName ?? row.ownerDepartmentName)}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <ExpiryCell expiresOn={row.expiresOn} expired={row.expired} />
                  <Button size="sm" variant="secondary" onClick={() => onOpen(row.id)}>Open</Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </QueryBoundary>
    </Panel>
  )
}

/* -------------------------------------------------------------- upload a file */

function UploadForm({ onDone }: { onDone: () => void }) {
  const refresh = useRefreshDocuments()
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [documentType, setDocumentType] = useState('CERTIFICATE')
  const [category, setCategory] = useState('')
  const [description, setDescription] = useState('')
  const [issuedOn, setIssuedOn] = useState('')
  const [expiresOn, setExpiresOn] = useState('')
  const [retentionUntil, setRetentionUntil] = useState('')
  const [relatedType, setRelatedType] = useState('')
  const [relatedId, setRelatedId] = useState('')
  const [feedback, setFeedback] = useState('')

  const upload = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Choose a file to upload.')
      const metadata: UploadDocument = {
        title: title.trim(),
        documentType: documentType.trim().toUpperCase(),
        description: description.trim() || null,
        category: category.trim() || null,
        issuedOn: issuedOn || null,
        expiresOn: expiresOn || null,
        retentionUntil: retentionUntil || null,
        relatedType: relatedType || null,
        relatedId: relatedType && relatedId.trim() ? relatedId.trim() : null,
      }
      return api<StoredDocument>('/api/v1/documents', {
        method: 'POST',
        body: fileBody(file, metadata),
      })
    },
    onSuccess: (row) => {
      setFeedback(`Uploaded as ${row.documentNumber}. It starts as a draft until it is checked.`)
      setFile(null)
      setTitle('')
      setDescription('')
      setIssuedOn('')
      setExpiresOn('')
      setRetentionUntil('')
      setRelatedId('')
      refresh()
      onDone()
    },
    onError: (error) => setFeedback(say(error)),
  })

  return (
    <form
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
      onSubmit={(event) => {
        event.preventDefault()
        upload.mutate()
      }}
    >
      <Field label="File" htmlFor="upload-file" required hint="The bytes and the details travel together.">
        <input
          id="upload-file"
          type="file"
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink file:mr-3 file:rounded file:border-0 file:bg-surface-soft file:px-3 file:py-1 file:text-sm file:text-ink"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </Field>
      <Field label="Title" htmlFor="upload-title" required>
        <TextInput
          id="upload-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Birth certificate"
        />
      </Field>
      <Field label="Kind" htmlFor="upload-type" required>
        <Select
          id="upload-type"
          value={documentType}
          onChange={(event) => setDocumentType(event.target.value)}
        >
          {COMMON_TYPES.map((entry) => (
            <option key={entry} value={entry}>{humanise(entry)}</option>
          ))}
        </Select>
      </Field>
      <Field label="Category" htmlFor="upload-category" hint="Free text, for grouping on your own terms.">
        <TextInput
          id="upload-category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
          placeholder="Identity"
        />
      </Field>
      <Field label="Issued" htmlFor="upload-issued">
        <TextInput
          id="upload-issued"
          type="date"
          value={issuedOn}
          onChange={(event) => setIssuedOn(event.target.value)}
        />
      </Field>
      <Field label="Expires" htmlFor="upload-expires">
        <TextInput
          id="upload-expires"
          type="date"
          value={expiresOn}
          onChange={(event) => setExpiresOn(event.target.value)}
        />
      </Field>
      <Field
        label="Keep until"
        htmlFor="upload-retention"
        hint="Bytes are kept past a delete until this date."
      >
        <TextInput
          id="upload-retention"
          type="date"
          value={retentionUntil}
          onChange={(event) => setRetentionUntil(event.target.value)}
        />
      </Field>
      <Field label="About" htmlFor="upload-related-type" hint="Link it to a record, if it belongs to one.">
        <Select
          id="upload-related-type"
          value={relatedType}
          onChange={(event) => setRelatedType(event.target.value as RelatedType | '')}
        >
          <option value="">Not linked</option>
          {RELATED_TYPES.map((entry) => (
            <option key={entry} value={entry}>{humanise(entry)}</option>
          ))}
        </Select>
      </Field>
      {relatedType && (
        <Field label="Record id" htmlFor="upload-related-id" hint="Paste the id of the linked record.">
          <TextInput
            id="upload-related-id"
            value={relatedId}
            onChange={(event) => setRelatedId(event.target.value)}
          />
        </Field>
      )}
      <Field label="Description" htmlFor="upload-description">
        <TextArea
          id="upload-description"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </Field>
      <div className="flex items-end gap-3 sm:col-span-2 lg:col-span-4">
        <Button type="submit" loading={upload.isPending}>Upload</Button>
        {feedback && <p className="text-sm text-ink-subtle">{feedback}</p>}
      </div>
    </form>
  )
}

/* -------------------------------------------------------------------- the drawer */

function DocumentDrawer({
  documentId,
  onClose,
  mayUpload,
  mayVerify,
  canDelete,
}: {
  documentId: string
  onClose: () => void
  mayUpload: boolean
  mayVerify: boolean
  canDelete: boolean
}) {
  const panel = useRef<HTMLDivElement>(null)
  // Escape closes, and tabbing stays inside; the overlay said it was modal but was not.
  useFocusTrap(panel, true, onClose)

  const refresh = useRefreshDocuments()
  const detail = useQuery({
    queryKey: ['documents', 'detail', documentId],
    queryFn: () => api<DocumentDetail>(`/api/v1/documents/${documentId}`),
  })

  const level = useQuery({
    queryKey: ['documents', 'access-level', documentId],
    queryFn: () => api<AccessLevelFor>(`/api/v1/documents/${documentId}/access-level`),
  })

  const [feedback, setFeedback] = useState('')

  const verify = useMutation({
    mutationFn: (approved: boolean) =>
      patch<StoredDocument>(`/api/v1/documents/${documentId}/verification`, { approved }),
    onSuccess: (row, approved) => {
      setFeedback(
        approved
          ? `${row.documentNumber} is verified and in circulation.`
          : `${row.documentNumber} is rejected. Upload a corrected version.`,
      )
      refresh()
    },
    onError: (error) => setFeedback(say(error)),
  })

  const remove = useMutation({
    mutationFn: (reason: string) =>
      del<StoredDocument>(`/api/v1/documents/${documentId}?reason=${encodeURIComponent(reason)}`),
    onSuccess: () => {
      setFeedback('Taken out of circulation.')
      refresh()
      onClose()
    },
    onError: (error) => setFeedback(say(error)),
  })

  const row = detail.data?.document

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-ink/40" role="dialog" aria-modal="true">
      <div
        ref={panel}
        tabIndex={-1}
        className="flex h-full w-full max-w-3xl flex-col overflow-y-auto bg-surface shadow-xl"
      >
        <div className="flex items-start justify-between border-b border-border p-5">
          <div>
            <h2 className="text-lg font-semibold text-ink">{row?.title ?? 'Document'}</h2>
            <p className="text-sm text-ink-muted">
              {row?.documentNumber}
              {row ? ` · ${humanise(row.documentType)}` : ''}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose}>Close</Button>
        </div>

        <QueryBoundary
          isLoading={detail.isLoading}
          error={detail.error}
          data={detail.data}
          onRetry={() => void detail.refetch()}
        >
          {(data) => (
            <>
              <dl className="grid grid-cols-2 gap-4 border-b border-border p-5 sm:grid-cols-4">
                <Detail label="Status" value={<StatusBadge status={data.document.status} />} />
                <Detail
                  label="Checked"
                  value={<VerificationBadge row={data.document} />}
                />
                <Detail label="Size" value={bytes(data.document.fileSize)} />
                <Detail label="Version" value={`v${data.document.currentVersion}`} />
                <Detail label="Owner" value={text(data.document.ownerName)} />
                <Detail label="Department" value={text(data.document.ownerDepartmentName)} />
                <Detail label="Issued" value={date(data.document.issuedOn)} />
                <Detail
                  label="Expires"
                  value={
                    <ExpiryCell
                      expiresOn={data.document.expiresOn}
                      expired={data.document.expired}
                    />
                  }
                />
                <Detail label="Keep until" value={date(data.document.retentionUntil)} />
                <Detail label="Stored on" value={text(data.document.storageProvider)} />
                <Detail label="Pages" value={num(data.document.pageCount)} />
                <Detail label="Uploaded" value={dateTime(data.document.createdAt)} />
              </dl>

              <section className="border-b border-border p-5">
                <h3 className="mb-3 text-sm font-semibold text-ink">The file</h3>
                <dl className="grid gap-3 sm:grid-cols-2">
                  <Detail label="Filename" value={text(data.document.originalFilename)} />
                  <Detail label="Content type" value={text(data.document.contentType)} />
                </dl>
                <p className="mt-3 break-all text-xs text-ink-muted">
                  SHA-256 {data.document.checksumSha256}
                </p>
                <DownloadRow document={data.document} versions={data.versions} />
              </section>

              {mayVerify && (
                <section className="border-b border-border p-5">
                  <h3 className="mb-2 text-sm font-semibold text-ink">Check this document</h3>
                  <p className="mb-3 text-sm text-ink-subtle">
                    Approving puts a draft into circulation. Rejecting leaves it on file so a
                    corrected version can replace it.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      loading={verify.isPending}
                      onClick={() => verify.mutate(true)}
                    >
                      Approve
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={verify.isPending}
                      onClick={() => verify.mutate(false)}
                    >
                      Reject
                    </Button>
                  </div>
                </section>
              )}

              {mayUpload && level.data?.canEdit && (
                <AddVersion documentId={documentId} currentVersion={data.document.currentVersion} />
              )}

              {mayUpload && level.data?.canManage && (
                <EditMetadataForm document={data.document} />
              )}

              {level.data?.canManage && <AccessPanel documentId={documentId} grants={data.access} />}

              <section className="border-b border-border p-5">
                <h3 className="mb-3 text-sm font-semibold text-ink">Versions</h3>
                <ul className="divide-y divide-border">
                  {data.versions.map((version) => (
                    <li key={version.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
                      <div>
                        <p className="text-sm text-ink">
                          v{version.versionNumber} · {version.originalFilename}
                        </p>
                        <p className="text-xs text-ink-muted">
                          {bytes(version.fileSize)} · {dateTime(version.createdAt)} ·{' '}
                          {text(version.changeNote)}
                        </p>
                      </div>
                      {version.versionNumber !== data.document.currentVersion && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            void download(version.id, `?version=${version.versionNumber}`, 'inline')
                          }}
                        >
                          Open this one
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              </section>

              {canDelete && (
                <section className="p-5">
                  <h3 className="mb-2 text-sm font-semibold text-ink">Take out of circulation</h3>
                  <p className="mb-3 text-sm text-ink-subtle">
                    The record stays, and the bytes are kept if a retention date is still in the
                    future.
                  </p>
                  <RemoveForm
                    busy={remove.isPending}
                    onConfirm={(reason) => remove.mutate(reason)}
                  />
                </section>
              )}
            </>
          )}
        </QueryBoundary>

        {feedback && (
          <p className="border-t border-border p-5 text-sm text-ink-subtle">{feedback}</p>
        )}
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------- download */

function DownloadRow({
  document,
  versions,
}: {
  document: StoredDocument
  versions: DocumentVersion[]
}) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState('')

  const run = async (suffix: string, disposition: 'save' | 'inline', name: string) => {
    setBusy(true)
    setProblem('')
    try {
      const blob = await apiDownload(`/api/v1/documents/${document.id}/download${suffix}`)
      const url = URL.createObjectURL(blob)
      const link = window.document.createElement('a')
      link.href = url
      link.download = name
      link.rel = 'noopener'
      link.target = disposition === 'inline' ? '_blank' : '_self'
      window.document.body.appendChild(link)
      link.click()
      link.remove()
      // Revoking immediately can cancel the download in some browsers, so it waits a tick.
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
    } catch (error) {
      setProblem(say(error))
    } finally {
      setBusy(false)
    }
  }

  const name = document.originalFilename || document.documentNumber

  return (
    <div className="mt-4 flex flex-wrap items-center gap-2">
      <Button size="sm" loading={busy} onClick={() => void run('', 'save', name)}>
        Download
      </Button>
      {/\.pdf$/i.test(document.originalFilename) && (
        <Button size="sm" variant="secondary" loading={busy} onClick={() => void run('?inline=true', 'inline', name)}>
          View in a new tab
        </Button>
      )}
      {problem && <p className="text-sm text-danger">{problem}</p>}
      {versions.length > 1 && (
        <span className="text-xs text-ink-muted">{versions.length} versions on file</span>
      )}
    </div>
  )
}

/** Saved under a different name so an older version does not overwrite the current download. */
async function download(documentId: string, suffix: string, disposition: 'save' | 'inline') {
  const blob = await apiDownload(`/api/v1/documents/${documentId}/download${suffix}`)
  const url = URL.createObjectURL(blob)
  const link = window.document.createElement('a')
  link.href = url
  link.download = `document${suffix.split('=')[1] ?? ''}`
  link.target = disposition === 'inline' ? '_blank' : '_self'
  window.document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

/* -------------------------------------------------------------- new versions */

function AddVersion({
  documentId,
  currentVersion,
}: {
  documentId: string
  currentVersion: number
}) {
  const refresh = useRefreshDocuments()
  const [file, setFile] = useState<File | null>(null)
  const [changeNote, setChangeNote] = useState('')
  const [feedback, setFeedback] = useState('')

  const add = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Choose the replacement file.')
      const metadata: NewVersion = { changeNote: changeNote.trim() || null }
      return api<StoredDocument>(`/api/v1/documents/${documentId}/versions`, {
        method: 'POST',
        body: fileBody(file, metadata),
      })
    },
    onSuccess: (row) => {
      setFeedback(`Saved as v${row.currentVersion}. The earlier file is still on record.`)
      setFile(null)
      setChangeNote('')
      refresh()
    },
    onError: (error) => setFeedback(say(error)),
  })

  return (
    <section className="border-b border-border p-5">
      <h3 className="mb-2 text-sm font-semibold text-ink">
        Add a version (now at v{currentVersion})
      </h3>
      <p className="mb-3 text-sm text-ink-subtle">
        Nothing is overwritten. The old file stays on record so it is always answerable which
        copy was sent when.
      </p>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          add.mutate()
        }}
      >
        <Field label="Replacement file" htmlFor="version-file" required>
          <input
            id="version-file"
            type="file"
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink file:mr-3 file:rounded file:border-0 file:bg-surface-soft file:px-3 file:py-1 file:text-sm file:text-ink"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </Field>
        <Field label="What changed" htmlFor="version-note">
          <TextInput
            id="version-note"
            value={changeNote}
            onChange={(event) => setChangeNote(event.target.value)}
            placeholder="Notarised copy"
          />
        </Field>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" size="sm" loading={add.isPending}>Save version</Button>
          {feedback && <p className="text-sm text-ink-subtle">{feedback}</p>}
        </div>
      </form>
    </section>
  )
}

/* ------------------------------------------------------------- metadata edits */

function EditMetadataForm({ document }: { document: StoredDocument }) {
  const refresh = useRefreshDocuments()
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(document.title)
  const [documentType, setDocumentType] = useState(document.documentType)
  const [category, setCategory] = useState(document.category ?? '')
  const [description, setDescription] = useState(document.description ?? '')
  const [status, setStatus] = useState<DocumentStatus>(document.status)
  const [issuedOn, setIssuedOn] = useState(document.issuedOn ?? '')
  const [expiresOn, setExpiresOn] = useState(document.expiresOn ?? '')
  const [retentionUntil, setRetentionUntil] = useState(document.retentionUntil ?? '')
  const [feedback, setFeedback] = useState('')

  const save = useMutation({
    mutationFn: () => {
      const body: EditMetadata = {
        title: title.trim(),
        documentType: documentType.trim().toUpperCase(),
        category: category.trim() || null,
        description: description.trim() || null,
        status,
        issuedOn: issuedOn || null,
        expiresOn: expiresOn || null,
        retentionUntil: retentionUntil || null,
      }
      return put<StoredDocument>(`/api/v1/documents/${document.id}/metadata`, body)
    },
    onSuccess: () => {
      setFeedback('Details saved.')
      setEditing(false)
      refresh()
    },
    onError: (error) => setFeedback(say(error)),
  })

  if (!editing) {
    return (
      <section className="border-b border-border p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-ink">Details</h3>
            <p className="text-sm text-ink-subtle">
              {text(document.description) === '—' ? 'No description.' : document.description}
            </p>
          </div>
          <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
            Edit details
          </Button>
        </div>
      </section>
    )
  }

  return (
    <section className="border-b border-border p-5">
      <h3 className="mb-3 text-sm font-semibold text-ink">Edit details</h3>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate()
        }}
      >
        <Field label="Title" htmlFor="edit-title" required>
          <TextInput id="edit-title" value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <Field label="Kind" htmlFor="edit-type" required>
          <Select id="edit-type" value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
            {COMMON_TYPES.map((entry) => (
              <option key={entry} value={entry}>{humanise(entry)}</option>
            ))}
          </Select>
        </Field>
        <Field label="Category" htmlFor="edit-category">
          <TextInput id="edit-category" value={category} onChange={(e) => setCategory(e.target.value)} />
        </Field>
        <Field label="Status" htmlFor="edit-status">
          <Select
            id="edit-status"
            value={status}
            onChange={(e) => setStatus(e.target.value as DocumentStatus)}
          >
            {STATUSES.map((entry) => (
              <option key={entry} value={entry}>{humanise(entry)}</option>
            ))}
          </Select>
        </Field>
        <Field label="Issued" htmlFor="edit-issued">
          <TextInput id="edit-issued" type="date" value={issuedOn} onChange={(e) => setIssuedOn(e.target.value)} />
        </Field>
        <Field label="Expires" htmlFor="edit-expires">
          <TextInput id="edit-expires" type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />
        </Field>
        <Field label="Keep until" htmlFor="edit-retention">
          <TextInput
            id="edit-retention"
            type="date"
            value={retentionUntil}
            onChange={(e) => setRetentionUntil(e.target.value)}
          />
        </Field>
        <Field label="Description" htmlFor="edit-description">
          <TextArea
            id="edit-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" size="sm" loading={save.isPending}>Save</Button>
          <Button type="button" size="sm" variant="ghost" onClick={() => setEditing(false)}>
            Cancel
          </Button>
          {feedback && <p className="text-sm text-ink-subtle">{feedback}</p>}
        </div>
      </form>
    </section>
  )
}

/* ------------------------------------------------------------------- access */

function AccessPanel({
  documentId,
  grants,
}: {
  documentId: string
  grants: AccessGrant[]
}) {
  const refresh = useRefreshDocuments()
  const [principalType, setPrincipalType] = useState<PrincipalType>('ROLE')
  const [principalRole, setPrincipalRole] = useState('')
  const [principalUserId, setPrincipalUserId] = useState('')
  const [accessLevel, setAccessLevel] = useState<AccessLevel>('VIEW')
  const [expiresAt, setExpiresAt] = useState('')
  const [feedback, setFeedback] = useState('')

  const roles = useQuery({
    queryKey: ['documents', 'roles'],
    queryFn: () => api<RoleSummary[]>('/api/v1/admin/roles'),
  })

  // Reading the user directory needs USER_READ, which does not follow from DOCUMENT_UPLOAD.
  // A caller without it still needs a way to grant by user, so the directory failing is not
  // treated as an error worth shouting about.
  const users = useQuery({
    queryKey: ['documents', 'directory'],
    queryFn: () => api<DirectoryUser[]>('/api/v1/admin/users', { query: { size: 200, sort: 'displayName,asc' } }),
    retry: false,
  })

  const grant = useMutation({
    mutationFn: () => {
      const body: GrantAccess = {
        principalType,
        accessLevel,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
        principalRole: principalType === 'ROLE' ? principalRole : null,
        principalUserId: principalType === 'USER' ? principalUserId : null,
      }
      return post<AccessGrant>(`/api/v1/documents/${documentId}/access`, body)
    },
    onSuccess: () => {
      setFeedback('Access granted.')
      setPrincipalRole('')
      setPrincipalUserId('')
      refresh()
    },
    onError: (error) => setFeedback(say(error)),
  })

  const revoke = useMutation({
    mutationFn: (grantId: string) => api<void>(`/api/v1/documents/${documentId}/access/${grantId}`, {
      method: 'DELETE',
    }),
    onSuccess: () => {
      setFeedback('Access taken back.')
      refresh()
    },
    onError: (error) => setFeedback(say(error)),
  })

  return (
    <section className="border-b border-border p-5">
      <h3 className="mb-2 text-sm font-semibold text-ink">Who may read this</h3>
      <p className="mb-3 text-sm text-ink-subtle">
        Without a grant, this document is invisible to everyone but the people who uploaded it.
      </p>

      {grants.length === 0 ? (
        <p className="mb-3 text-sm text-ink-muted">Nobody has been given access.</p>
      ) : (
        <ul className="mb-4 divide-y divide-border">
          {grants.map((entry) => (
            <li key={entry.id} className="flex flex-wrap items-center justify-between gap-3 py-2">
              <div>
                <p className="text-sm text-ink">
                  {entry.principalType === 'ROLE' ? humanise(entry.principalRole) : text(entry.principalName)}
                </p>
                <p className="text-xs text-ink-muted">
                  {humanise(entry.accessLevel)} · granted {dateTime(entry.grantedAt)}
                  {entry.expiresAt ? ` · ends ${date(entry.expiresAt)}` : ''}
                  {entry.expired ? ' · expired' : ''}
                </p>
              </div>
              <Button
                size="sm"
                variant="ghost"
                loading={revoke.isPending}
                onClick={() => revoke.mutate(entry.id)}
              >
                Take back
              </Button>
            </li>
          ))}
        </ul>
      )}

      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          grant.mutate()
        }}
      >
        <Field label="Who" htmlFor="grant-principal-type">
          <Select
            id="grant-principal-type"
            value={principalType}
            onChange={(event) => setPrincipalType(event.target.value as PrincipalType)}
          >
            {PRINCIPAL_TYPES.map((entry) => (
              <option key={entry} value={entry}>{entry === 'ROLE' ? 'A role' : 'One person'}</option>
            ))}
          </Select>
        </Field>
        {principalType === 'ROLE' ? (
          <Field label="Role" htmlFor="grant-role" required>
            <Select
              id="grant-role"
              value={principalRole}
              onChange={(event) => setPrincipalRole(event.target.value)}
            >
              <option value="">Choose a role</option>
              {(roles.data ?? []).map((role) => (
                <option key={role.id} value={role.code}>{role.name}</option>
              ))}
            </Select>
          </Field>
        ) : (
          <Field
            label="Person"
            htmlFor="grant-user"
            required
            hint={
              users.error
                ? 'The staff directory is not available to you, so paste an account id here.'
                : undefined
            }
          >
            {users.data ? (
              <Select
                id="grant-user"
                value={principalUserId}
                onChange={(event) => setPrincipalUserId(event.target.value)}
              >
                <option value="">Choose a person</option>
                {users.data.map((user) => (
                  <option key={user.id} value={user.id}>
                    {user.displayName} ({user.username})
                  </option>
                ))}
              </Select>
            ) : (
              <TextInput
                id="grant-user"
                value={principalUserId}
                onChange={(event) => setPrincipalUserId(event.target.value)}
                placeholder="Account id"
              />
            )}
          </Field>
        )}
        <Field
          label="Level"
          htmlFor="grant-level"
          hint="View reads it, Edit adds versions, Manage also changes who has access."
        >
          <Select
            id="grant-level"
            value={accessLevel}
            onChange={(event) => setAccessLevel(event.target.value as AccessLevel)}
          >
            {ACCESS_LEVELS.map((entry) => (
              <option key={entry} value={entry}>{humanise(entry)}</option>
            ))}
          </Select>
        </Field>
        <Field label="Ends" htmlFor="grant-expires" hint="Leave empty for no end date.">
          <TextInput
            id="grant-expires"
            type="date"
            value={expiresAt}
            onChange={(event) => setExpiresAt(event.target.value)}
          />
        </Field>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" size="sm" loading={grant.isPending}>Grant access</Button>
          {feedback && <p className="text-sm text-ink-subtle">{feedback}</p>}
        </div>
      </form>
    </section>
  )
}

/* ------------------------------------------------------------------- delete */

function RemoveForm({
  busy,
  onConfirm,
}: {
  busy: boolean
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')

  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={(event) => {
        event.preventDefault()
        onConfirm(reason.trim())
      }}
    >
      <Field label="Why" htmlFor="delete-reason" hint="Recorded on the audit trail.">
        <TextInput
          id="delete-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Replaced by the 2026 policy"
        />
      </Field>
      <Button type="submit" variant="secondary" loading={busy}>Take it out of circulation</Button>
    </form>
  )
}

/* ------------------------------------------------------------------- shared */

function Stat({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-card border border-border bg-surface p-4">
      <p className="text-xs uppercase tracking-wide text-ink-muted">{label}</p>
      <p className="nums mt-1 text-xl font-semibold text-ink">{value ?? '—'}</p>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink">{value}</dd>
    </div>
  )
}

function VerificationBadge({ row }: { row: StoredDocument }) {
  if (row.expired) return <Badge tone="danger">Expired</Badge>
  if (row.verificationStatus === 'VERIFIED') return <Badge tone="success">Checked</Badge>
  if (row.verificationStatus === 'REJECTED') return <Badge tone="danger">Rejected</Badge>
  if (row.verificationStatus === 'PENDING') return <Badge tone="warning">In review</Badge>
  return <Badge>Unchecked</Badge>
}

function ExpiryCell({ expiresOn, expired }: { expiresOn: string | null; expired: boolean }) {
  if (!expiresOn) return <span className="text-ink-muted">—</span>
  return (
    <span className={expired ? 'text-danger' : 'text-ink'}>
      {expired ? 'Expired ' : ''}
      {date(expiresOn)}
    </span>
  )
}
