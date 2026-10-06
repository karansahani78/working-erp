import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, apiDownload } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import {
  Button,
  EmptyState,
  Field,
  Panel,
  TextInput,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { text } from '../../lib/format'
import {
  DATE_PARAMETERS,
  PARAMETER_LABELS,
  type ReportCatalogue,
  type ReportDescriptor,
  type ReportResult,
} from './types'

/**
 * Reports are defined on the server, so this screen is driven entirely by the catalogue: it
 * asks what reports exist and what each one needs, then builds only those inputs. Adding a
 * report on the server makes it appear here with no frontend change.
 */
export function ReportsPage() {
  const [group, setGroup] = useState('')
  const [key, setKey] = useState('')

  const catalogue = useQuery({
    queryKey: ['report-catalogue'],
    queryFn: () => api<ReportCatalogue>('/api/v1/reports'),
  })

  const groups = Object.keys(catalogue.data ?? {})
  const descriptors = useMemo(
    () => Object.values(catalogue.data ?? {}).flat(),
    [catalogue.data],
  )

  const activeGroup = group || groups[0] || ''
  const inGroup = (catalogue.data?.[activeGroup] ?? []) as ReportDescriptor[]
  const activeKey = key || inGroup[0]?.key || ''
  const descriptor = descriptors.find((item) => item.key === activeKey)

  if (catalogue.isLoading) {
    return (
      <div className="mx-auto max-w-7xl">
        <PageHeader title="Reports" description="Loading the catalogue…" />
      </div>
    )
  }

  if (catalogue.isError) {
    return (
      <div className="mx-auto max-w-3xl py-10">
        <EmptyState
          title="Could not load the report catalogue"
          description={describeError(catalogue.error)}
          action={<Button onClick={() => void catalogue.refetch()}>Try again</Button>}
        />
      </div>
    )
  }

  if (descriptors.length === 0) {
    return (
      <div className="mx-auto max-w-3xl py-10">
        <EmptyState
          title="No reports available"
          description="Reports you have permission to run will be listed here."
        />
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Reports"
        description="Run a report and export the same numbers as a file."
      />

      <div className="grid gap-5 lg:grid-cols-[17rem_1fr]">
        <nav className="flex flex-col gap-4" aria-label="Report groups">
          <Panel title="Groups" padded={false}>
            <ul className="divide-y divide-border">
              {groups.map((name) => (
                <li key={name}>
                  <button
                    type="button"
                    onClick={() => {
                      setGroup(name)
                      setKey('')
                    }}
                    className={[
                      'flex w-full items-center justify-between px-4 py-2.5 text-left text-sm transition-colors',
                      activeGroup === name
                        ? 'bg-primary-soft font-medium text-primary'
                        : 'text-ink-muted hover:bg-surface-soft',
                    ].join(' ')}
                  >
                    {name}
                    <span className="nums text-xs opacity-70">{catalogue.data?.[name]?.length ?? 0}</span>
                  </button>
                </li>
              ))}
            </ul>
          </Panel>

          <Panel title={activeGroup || 'Reports'} padded={false}>
            <ul className="divide-y divide-border">
              {inGroup.map((item) => (
                <li key={item.key}>
                  <button
                    type="button"
                    onClick={() => setKey(item.key)}
                    className={[
                      'w-full px-4 py-2.5 text-left text-sm transition-colors',
                      activeKey === item.key
                        ? 'bg-primary-soft font-medium text-primary'
                        : 'text-ink-muted hover:bg-surface-soft',
                    ].join(' ')}
                  >
                    {item.title}
                  </button>
                </li>
              ))}
            </ul>
          </Panel>
        </nav>

        {/* Keyed on the report so switching reports resets its parameters rather than
            carrying the previous report's values across. */}
        {descriptor && <ReportRunner key={descriptor.key} descriptor={descriptor} />}
      </div>
    </div>
  )
}

/**
 * The reports the server refuses to run without an identifier. Everything else has a
 * sensible blank, so those run as soon as the screen opens.
 */
const REQUIRED_PARAMETERS: Record<string, string[]> = {
  'attendance-student': ['studentId'],
  'grade-sheet': ['studentId', 'examId'],
  'payroll-register': ['payrollRunId'],
}

function ReportRunner({ descriptor }: { descriptor: ReportDescriptor }) {
  const [values, setValues] = useState<Record<string, string>>(() => initialValues(descriptor))
  // Start from the calculated defaults so a dated report shows a result immediately.
  const [submitted, setSubmitted] = useState<Record<string, string>>(() => initialValues(descriptor))
  const [exporting, setExporting] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)

  const required = REQUIRED_PARAMETERS[descriptor.key] ?? []
  const missingRequired = required.filter((name) => !submitted[name])

  const result = useQuery({
    queryKey: ['report-run', descriptor.key, submitted],
    queryFn: () =>
      api<ReportResult>(`/api/v1/reports/${descriptor.key}`, {
        query: submitted,
      }),
    enabled: missingRequired.length === 0,
  })

  async function download(format: string) {
    setExporting(format)
    setExportError(null)
    try {
      const blob = await apiDownload(`/api/v1/reports/${descriptor.key}/export`, {
        query: { ...submitted, format },
      })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${descriptor.key}.${format.toLowerCase() === 'csv' ? 'csv' : format.toLowerCase()}`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (error) {
      setExportError(describeError(error))
    } finally {
      setExporting(null)
    }
  }

  const missing = descriptor.parameters.filter((name) => !submitted[name])
  const canRun = missingRequired.length === 0

  return (
    <div className="flex flex-col gap-4">
      <Panel title={descriptor.title} description={descriptor.description ?? undefined}>
        {descriptor.parameters.length === 0 ? (
          <p className="mb-4 text-sm text-ink-subtle">
            This report takes no parameters.
          </p>
        ) : (
          <form
            className="grid gap-4 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              const next: Record<string, string> = {}
              for (const name of descriptor.parameters) {
                if (values[name]) next[name] = values[name]
              }
              setSubmitted(next)
            }}
          >
            {descriptor.parameters.map((name) => (
              <Field
                key={name}
                label={PARAMETER_LABELS[name] ?? name}
                required={required.includes(name)}
              >
                <TextInput
                  type={DATE_PARAMETERS.has(name) ? 'date' : 'text'}
                  value={values[name] ?? ''}
                  onChange={(event) => setValues((current) => ({ ...current, [name]: event.target.value }))}
                  placeholder={DATE_PARAMETERS.has(name) ? undefined : 'ID or value'}
                />
              </Field>
            ))}

            <div className="flex flex-wrap items-center gap-2 sm:col-span-2">
              <Button type="submit" loading={result.isFetching} disabled={!canRun}>
                Run report
              </Button>
              {missingRequired.length > 0 ? (
                <span className="text-xs text-danger">
                  {missingRequired.map((name) => PARAMETER_LABELS[name] ?? name).join(' and ')}{' '}
                  {missingRequired.length === 1 ? 'is' : 'are'} needed before this report can run.
                </span>
              ) : (
                <span className="text-xs text-ink-subtle">
                  {missing.length > 0
                    ? `${missing.length} optional value${missing.length === 1 ? '' : 's'} left blank.`
                    : 'All values supplied.'}
                </span>
              )}
            </div>
          </form>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-border pt-4">
          <span className="text-xs font-semibold tracking-wide text-ink-subtle uppercase">Export</span>
          {['CSV', 'XLSX', 'PDF'].map((format) => (
            <Button
              key={format}
              variant="secondary"
              size="sm"
              loading={exporting === format}
              disabled={!canRun}
              onClick={() => void download(format)}
            >
              {format}
            </Button>
          ))}
          {exportError && <span className="text-sm text-danger">{exportError}</span>}
        </div>
      </Panel>

      {result.data && <ReportTable result={result.data} />}

      {result.isError && (
        <Panel>
          <EmptyState title="That report could not run" description={describeError(result.error)} />
        </Panel>
      )}
    </div>
  )
}

/** Reports carrying a date range start on the first of the month, which is what people want. */
function initialValues(descriptor: ReportDescriptor): Record<string, string> {
  const values: Record<string, string> = {}
  if (descriptor.parameters.includes('from')) {
    values.from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10)
  }
  if (descriptor.parameters.includes('to')) {
    values.to = new Date().toISOString().slice(0, 10)
  }
  if (descriptor.parameters.includes('month')) {
    values.month = new Date().toISOString().slice(0, 7)
  }
  return values
}

/** A value that is worth lining up on the right: numbers, money and plain totals. */
function looksNumeric(value: unknown): boolean {
  if (typeof value === 'number') return true
  if (typeof value !== 'string') return false
  const trimmed = value.trim()
  if (trimmed === '') return false
  return /^-?[0-9][0-9,]*(\.[0-9]+)?$/.test(trimmed)
}

/** An absent cell is shown as a dash so a gap is not mistaken for a value. */
function cell(value: unknown): string {
  if (value === null || value === undefined || value === '') return '\u2014'
  return text(value)
}

function ReportTable({ result }: { result: ReportResult }) {
  const columns = result.columns ?? []
  const summary = result.summary ?? {}

  // The server sends bare columns and positional rows, with no per-column type, so figures
  // are recognised by looking at the values in a column rather than by a declared type.
  const numeric = columns.map((_, columnIndex) =>
    result.rows.some((row) => looksNumeric(row[columnIndex])),
  )

  return (
    <Panel
      title={result.title}
      padded={false}
      description={`${result.rowCount} row${result.rowCount === 1 ? '' : 's'}`}
    >
      {Object.keys(summary).length > 0 && (
        <div className="flex flex-wrap gap-x-8 gap-y-3 border-b border-border bg-surface-soft px-5 py-4">
          {Object.entries(summary).map(([label, value]) => (
            <div key={label}>
              <p className="text-xs tracking-wide text-ink-subtle uppercase">{label}</p>
              <p className="nums mt-0.5 font-semibold text-ink">{text(value)}</p>
            </div>
          ))}
        </div>
      )}

      {result.rows.length === 0 ? (
        <EmptyState title="No rows" description="The report ran successfully but matched nothing." />
      ) : (
        <div className="max-h-[32rem] overflow-auto">
          <table className="w-full border-collapse text-sm">
            <thead className="sticky top-0 bg-surface">
              <tr className="border-b border-border text-left">
                {columns.map((caption, columnIndex) => (
                  <th
                    key={caption || columnIndex}
                    scope="col"
                    className={[
                      'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
                      numeric[columnIndex] ? 'text-right' : '',
                    ].join(' ')}
                  >
                    {caption}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row, rowIndex) => (
                <tr key={rowIndex} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                  {columns.map((caption, columnIndex) => (
                    <td
                      key={caption || columnIndex}
                      className={[
                        'px-4 py-2.5 whitespace-nowrap',
                        numeric[columnIndex] ? 'nums text-right' : '',
                      ].join(' ')}
                    >
                      {cell(row[columnIndex])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {result.notes && result.notes.length > 0 && (
        <ul className="space-y-1 border-t border-border bg-surface-soft px-5 py-4 text-xs text-ink-subtle">
          {result.notes.map((note, index) => (
            <li key={index}>{note}</li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

