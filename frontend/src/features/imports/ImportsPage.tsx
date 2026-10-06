import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api, api as http, post, put, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  Select,
  StatusBadge,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { dateTime, humanise, num } from '../../lib/format'
import type {
  Duplicate,
  FieldHelp,
  ImportBatch,
  ImportReport,
  ImportTypeHelp,
  RowError,
  UploadResult,
  ValidationResult,
} from './types'

/**
 * A bulk import is a wizard: upload a file, say which column is which field, check what
 * would be written, then commit. Nothing is written until the confirm step, and a batch
 * that fails validation can be corrected and re-validated rather than uploaded again.
 */
export function ImportsPage() {
  const [page, setPage] = useState(0)
  const [type, setType] = useState('')

  const batches = useQuery({
    queryKey: ['import-batches', { page, type }],
    queryFn: () =>
      api<PageResponse<ImportBatch>>('/api/v1/imports', {
        query: { page, size: 20, type: type || undefined },
      }),
  })

  const rows = batches.data?.data ?? []

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Data imports"
        description="Load lists in bulk. Nothing is written until you confirm, and every batch is kept for review."
        actions={<Link to="/imports/new"><Button>Start an import</Button></Link>}
      />

      <Panel
        title="Batches"
        padded={false}
        actions={
          <Select value={type} onChange={(event) => { setType(event.target.value); setPage(0) }} aria-label="Type">
            <option value="">All types</option>
            {['STUDENTS', 'GUARDIANS', 'EMPLOYEES', 'COURSES', 'FEES', 'INVENTORY', 'BOOKS'].map((value) => (
              <option key={value} value={value}>{humanise(value)}</option>
            ))}
          </Select>
        }
      >
        {batches.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading batches…</p>
        ) : batches.isError ? (
          <div className="p-5"><EmptyState title="Could not load batches" description={describeError(batches.error)} /></div>
        ) : rows.length === 0 ? (
          <EmptyState
            title="No imports yet"
            description="Upload a spreadsheet to bring students, staff or stock in at once."
            action={<Link to="/imports/new"><Button>Start an import</Button></Link>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Batch</Th>
                  <Th hideBelow="md">Type</Th>
                  <Th hideBelow="lg">File</Th>
                  <Th align="right">Rows</Th>
                  <Th align="right" hideBelow="md">Imported</Th>
                  <Th>Status</Th>
                  <Th hideBelow="lg">Created</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((batch) => (
                  <tr key={batch.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                    <td className="px-4 py-3">
                      <Link
                        to={`/imports/${batch.id}`}
                        className="nums font-medium text-primary hover:underline"
                      >
                        {batch.batchNumber}
                      </Link>
                    </td>
                    <td className="hidden px-4 py-3 text-ink-muted md:table-cell">{humanise(batch.importType)}</td>
                    <td className="hidden max-w-48 truncate px-4 py-3 text-ink-subtle lg:table-cell">
                      {batch.originalFilename ?? '—'}
                    </td>
                    <td className="nums px-4 py-3 text-right text-ink-muted">{num(batch.totalRows)}</td>
                    <td className="nums hidden px-4 py-3 text-right text-ink-muted md:table-cell">
                      {num(batch.importedRows)}
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={batch.status} /></td>
                    <td className="nums hidden px-4 py-3 text-ink-subtle lg:table-cell">
                      {dateTime(batch.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {batches.data && <Pager page={batches.data} onChange={setPage} />}
    </div>
  )
}

/** Step one: choose what is being loaded, then upload the file. */
export function ImportUploadPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [type, setType] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const types = useQuery({
    queryKey: ['import-types'],
    queryFn: () => api<ImportTypeHelp[]>('/api/v1/imports/types'),
  })

  const selected = types.data?.find((item) => item.type === type)

  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a file first.')
      const body = new FormData()
      body.append('file', file)
      return http<UploadResult>(`/api/v1/imports?type=${encodeURIComponent(type)}`, {
        method: 'POST',
        body,
      })
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['import-batches'] })
      window.location.assign(`/imports/${result.batch.id}`)
    },
  })

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-5">
      <PageHeader
        title="Start an import"
        description="The file is stored and checked. Nothing reaches the database until you confirm."
      />

      <Panel title="1. What are you loading?">
        <div className="grid gap-4">
          <Field label="Record type" required>
            <Select value={type} onChange={(event) => setType(event.target.value)}>
              <option value="">Choose a type</option>
              {(types.data ?? [])
                .filter((item) => can(item.confirmPermission))
                .map((item) => (
                  <option key={item.type} value={item.type}>{humanise(item.type)}</option>
                ))}
            </Select>
          </Field>

          {selected && (
            <div className="rounded-lg border border-border bg-surface-soft p-4">
              <p className="text-sm font-medium text-ink">Columns this import expects</p>
              <ul className="mt-2 flex flex-wrap gap-1.5">
                {selected.fields.map((field) => (
                  <li key={field.label}>
                    <Badge tone={field.required ? 'info' : 'neutral'}>
                      {field.label}
                      {field.required ? ' *' : ''}
                    </Badge>
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-ink-subtle">
                The first row of your file must contain these column names. You can map them to
                different columns on the next screen.
              </p>
            </div>
          )}
        </div>
      </Panel>

      <Panel title="2. Choose the file">
        <div className="grid gap-4">
          <Field label="Spreadsheet" hint="CSV or XLSX. The first row is the header.">
            <input
              ref={fileInput}
              type="file"
              accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink file:mr-3 file:rounded-md file:border-0 file:bg-primary-soft file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-primary"
            />
          </Field>

          {upload.isError && <p className="text-sm text-danger">{describeError(upload.error)}</p>}

          <div className="flex gap-2">
            <Button
              loading={upload.isPending}
              disabled={!type || !file}
              onClick={() => upload.mutate()}
            >
              Upload and continue
            </Button>
            <Link to="/imports"><Button variant="ghost">Cancel</Button></Link>
          </div>
        </div>
      </Panel>
    </div>
  )
}

/** Steps two to four, for one batch. */
export function ImportBatchPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [validation, setValidation] = useState<ValidationResult | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)

  const batch = useQuery({
    queryKey: ['import-batch', id],
    queryFn: () => api<ImportBatch>(`/api/v1/imports/${id}`),
  })

  const types = useQuery({
    queryKey: ['import-types'],
    queryFn: () => api<ImportTypeHelp[]>('/api/v1/imports/types'),
  })

  // The form is rebuilt from the fields this import needs, seeded with the mapping the batch
  // already holds. Keyed off the mapping rather than the batch id, so a remount after a save
  // picks up what the server stored instead of the pre-save values.
  const savedMapping = batch.data?.columnMapping ?? null
  const mapping = useMemo(() => ({ ...(savedMapping ?? {}) }), [savedMapping])
  const [draft, setDraft] = useState<Record<string, string> | null>(null)
  const mappingIn = draft ?? mapping

  const errors = useQuery({
    queryKey: ['import-errors', id],
    queryFn: () => api<RowError[]>(`/api/v1/imports/${id}/errors`),
    enabled: batch.data?.invalidRows ? batch.data.invalidRows > 0 : false,
  })

  const duplicates = useQuery({
    queryKey: ['import-duplicates', id],
    queryFn: () => api<Duplicate[]>(`/api/v1/imports/${id}/duplicates`),
    enabled: batch.data?.validRows ? batch.data.validRows > 0 : false,
  })

  const saveMapping = useMutation({
    mutationFn: () => put<void>(`/api/v1/imports/${id}/mapping`, { mapping: mappingIn }),
    onSuccess: () => {
      setDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['import-batch', id] })
    },
  })

  const validate = useMutation({
    mutationFn: async () => {
      // Saved first, because what is on the batch is what gets read; and always sent, since a
      // user who clears a column means to leave it unmapped rather than keep the old one.
      if (Object.keys(mappingIn).length > 0 || (savedMapping && Object.keys(savedMapping).length > 0)) {
        await put<void>(`/api/v1/imports/${id}/mapping`, { mapping: mappingIn })
      }
      const result = await post<ValidationResult>(`/api/v1/imports/${id}/validate`)
      setValidation(result)
      void queryClient.invalidateQueries({ queryKey: ['import-batch', id] })
      return result
    },
  })

  const confirm = useMutation({
    mutationFn: (skipInvalidRows: boolean) =>
      post<ImportReport>(`/api/v1/imports/${id}/confirm?skipInvalidRows=${skipInvalidRows}`),
    onSuccess: (result) => {
      setReport(result)
      void queryClient.invalidateQueries()
    },
  })

  const current = batch.data
  const errorsList = validation?.errors ?? errors.data ?? []
  const duplicateList = validation?.duplicates ?? duplicates.data ?? []
  const canConfirm = current?.status === 'VALIDATED'

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5">
      <PageHeader
        title={current?.batchNumber ?? 'Import'}
        description={current ? `${humanise(current.importType)} · ${current.originalFilename ?? 'file'}` : undefined}
        actions={<Link to="/imports"><Button variant="secondary">All batches</Button></Link>}
      />

      <Steps status={current?.status} />

      {current && (
        <Panel title="Batch">
          <dl className="grid gap-4 sm:grid-cols-4">
            <Stat label="Total rows" value={num(current.totalRows)} />
            <Stat label="Valid" value={num(current.validRows)} tone="success" />
            <Stat label="Invalid" value={num(current.invalidRows)} tone={current.invalidRows > 0 ? 'danger' : 'neutral'} />
            <Stat label="Imported" value={num(current.importedRows)} tone="info" />
          </dl>
        </Panel>
      )}

      {report && (
        <Panel title="Result">
          <div className="rounded-lg bg-success-soft p-4">
            <p className="font-medium text-success">
              {report.imported} of {report.total} rows imported
              {report.skipped > 0 ? `, ${report.skipped} skipped` : ''}.
            </p>
            {report.notes.length > 0 && (
              <ul className="mt-2 list-disc pl-5 text-sm text-ink-muted">
                {report.notes.map((note, index) => <li key={index}>{note}</li>)}
              </ul>
            )}
          </div>
        </Panel>
      )}

      {(current?.status === 'UPLOADED' || current?.status === 'MAPPED') && (
        <MappingStep
          fields={types.data?.find((item) => item.type === current?.importType)?.fields ?? []}
          mapping={mappingIn}
          dirty={draft !== null}
          onChange={setDraft}
          onSave={() => saveMapping.mutate()}
          saving={saveMapping.isPending}
          saved={saveMapping.isSuccess}
          error={saveMapping.isError ? describeError(saveMapping.error) : null}
        />
      )}

      {(current?.status === 'UPLOADED' || current?.status === 'MAPPED' || current?.status === 'VALIDATED' || current?.status === 'FAILED') && (
        <Panel title="Check and commit">
          <div className="flex flex-wrap items-center gap-3">
            <Button loading={validate.isPending} onClick={() => validate.mutate()}>
              {validation ? 'Check again' : 'Check the file'}
            </Button>

            {canConfirm && errorsList.length === 0 && (
              <Button
                variant="secondary"
                loading={confirm.isPending}
                onClick={() => confirm.mutate(false)}
              >
                Import every valid row
              </Button>
            )}

            {canConfirm && errorsList.length > 0 && (
              <Button
                variant="danger"
                loading={confirm.isPending}
                onClick={() => confirm.mutate(true)}
              >
                Import the good rows only ({current!.validRows})
              </Button>
            )}

            {validate.isError && <span className="text-sm text-danger">{describeError(validate.error)}</span>}
            {confirm.isError && <span className="text-sm text-danger">{describeError(confirm.error)}</span>}
          </div>

          {errorsList.length > 0 && canConfirm && (
            <p className="mt-3 text-sm text-ink-muted">
              {errorsList.length} row{errorsList.length === 1 ? ' has' : 's have'} problems. You can still
              import everything that is valid, or go back and fix the file.
            </p>
          )}
        </Panel>
      )}

      {errorsList.length > 0 && (
        <Panel title={`Problems (${errorsList.length})`} padded={false}>
          <div className="max-h-96 overflow-auto">
            <table className="w-full border-collapse text-sm">
              <thead className="sticky top-0 bg-surface">
                <tr className="border-b border-border text-left">
                  <Th align="right">Row</Th>
                  <Th>Field</Th>
                  <Th>Problem</Th>
                  <Th hideBelow="md">Value</Th>
                </tr>
              </thead>
              <tbody>
                {errorsList.map((row, index) => (
                  <tr key={`${row.row}-${row.field}-${index}`} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-2.5 text-right text-ink-subtle">{row.row}</td>
                    <td className="px-4 py-2.5 text-ink">{row.field ?? '—'}</td>
                    <td className="px-4 py-2.5 text-danger">{row.message}</td>
                    <td className="nums hidden max-w-48 truncate px-4 py-2.5 text-ink-subtle md:table-cell">
                      {row.value ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {duplicateList.length > 0 && (
        <Panel title={`Possible duplicates (${duplicateList.length})`} padded={false}>
          <p className="border-b border-border px-4 py-3 text-sm text-ink-subtle">
            These rows match something already in the system. They are skipped rather than
            creating a second record.
          </p>
          <div className="max-h-72 overflow-auto">
            <table className="w-full border-collapse text-sm">
              <thead className="sticky top-0 bg-surface">
                <tr className="border-b border-border text-left">
                  <Th align="right">Row</Th>
                  <Th>Matched on</Th>
                  <Th>Already there</Th>
                  <Th hideBelow="md">In your file</Th>
                </tr>
              </thead>
              <tbody>
                {duplicateList.map((row, index) => (
                  <tr key={`${row.row}-${row.field}-${index}`} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-2.5 text-right text-ink-subtle">{row.row}</td>
                    <td className="px-4 py-2.5 text-ink">{row.field ?? '—'}</td>
                    <td className="nums px-4 py-2.5 text-ink-muted">{row.existing}</td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle md:table-cell">{row.incoming}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {validation && validation.preview.length > 0 && (
        <Panel title="What will be written" padded={false} description={`${validation.preview.length} rows`}>
          <div className="max-h-96 overflow-auto">
            <table className="w-full border-collapse text-sm">
              <thead className="sticky top-0 bg-surface">
                <tr className="border-b border-border text-left">
                  <Th align="right">Row</Th>
                  {Object.keys(validation.preview[0].values).map((key) => (
                    <Th key={key}>{humanise(key)}</Th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {validation.preview.map((row) => (
                  <tr key={row.rowNumber} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-2.5 text-right text-ink-subtle">{row.rowNumber}</td>
                    {Object.keys(validation.preview[0].values).map((key) => (
                      <td key={key} className="px-4 py-2.5 whitespace-nowrap text-ink">
                        {row.values[key] ?? '—'}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
    </div>
  )
}

function MappingStep({
  fields,
  mapping,
  dirty,
  onChange,
  onSave,
  saving,
  saved,
  error,
}: {
  fields: FieldHelp[]
  mapping: Record<string, string>
  dirty: boolean
  onChange: (mapping: Record<string, string>) => void
  onSave: () => void
  saving: boolean
  saved: boolean
  error: string | null
}) {
  const matched = fields.filter((field) => (mapping[field.name] ?? '').trim() !== '').length

  return (
    <Panel
      title="2. Match your columns"
      description="Say which column in your file feeds each field. Already matched columns are filled in."
      actions={
        <Button variant="secondary" loading={saving} onClick={onSave}>
          Save mapping
        </Button>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <Field
            key={field.name}
            label={`${field.label}${field.required ? ' *' : ''}`}
            hint={field.required ? 'Required' : 'Optional'}
          >
            <TextInput
              value={mapping[field.name] ?? ''}
              onChange={(event) => onChange({ ...mapping, [field.name]: event.target.value })}
              placeholder="Column name"
            />
          </Field>
        ))}
      </div>
      <p className="mt-3 text-xs text-ink-subtle">
        Leave a column blank to ignore it. {matched} of {fields.length} matched.
      </p>
      {dirty && (
        <p className="mt-2 text-xs text-ink-subtle">
          Unsaved changes. Checking the file saves them first.
        </p>
      )}
      {saved && <p className="mt-3 text-sm text-success">Mapping saved.</p>}
      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
    </Panel>
  )
}

function Steps({ status }: { status?: string }) {
  const order = ['UPLOADED', 'MAPPED', 'VALIDATED', 'COMPLETED']
  const current = order.indexOf(status ?? '')
  const failed = status === 'FAILED'

  return (
    <ol className="flex flex-wrap items-center gap-2" aria-label="Import progress">
      {['Uploaded', 'Mapped', 'Checked', 'Imported'].map((label, index) => {
        const done = !failed && current > index
        const active = !failed && current === index
        return (
          <li key={label} className="flex items-center gap-2">
            <span
              className={[
                'flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium',
                failed && index > current
                  ? 'bg-surface-soft text-ink-subtle'
                  : done || active
                    ? 'bg-primary-soft text-primary'
                    : 'bg-surface-soft text-ink-subtle',
              ].join(' ')}
            >
              <span className="nums">{index + 1}</span>
              {label}
            </span>
            {index < order.length - 1 && <span className="text-ink-subtle">→</span>}
          </li>
        )
      })}
      {failed && <StatusBadge status="FAILED" />}
    </ol>
  )
}

function Stat({ label, value, tone = 'neutral' }: { label: string; value: string; tone?: 'neutral' | 'success' | 'danger' | 'info' }) {
  const colour = {
    neutral: 'text-ink',
    success: 'text-success',
    danger: 'text-danger',
    info: 'text-info',
  }[tone]
  return (
    <div>
      <dt className="text-xs tracking-wide text-ink-subtle uppercase">{label}</dt>
      <dd className={`nums mt-0.5 text-xl font-semibold ${colour}`}>{value}</dd>
    </div>
  )
}

function Th({ children, align, hideBelow }: { children: React.ReactNode; align?: 'right'; hideBelow?: 'md' | 'lg' }) {
  return (
    <th
      scope="col"
      className={[
        'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
        align === 'right' ? 'text-right' : '',
        hideBelow === 'md' ? 'hidden md:table-cell' : '',
        hideBelow === 'lg' ? 'hidden lg:table-cell' : '',
      ].join(' ')}
    >
      {children}
    </th>
  )
}
