import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api, post } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import { Button, EmptyState, Panel, Select, StatusBadge } from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, money } from '../../lib/format'
import { FEE_STRUCTURE_STATUSES, type FeeStructure } from './types'

export function FeeStructuresPage() {
  const { can } = useAuth()
  const [status, setStatus] = useState('')

  const structures = useQuery({
    queryKey: ['fee-structures', status],
    queryFn: () => api<FeeStructure[]>('/api/v1/finance/fee-structures', { query: { status } }),
  })

  const publish = useMutation({
    mutationFn: (id: string) => post<FeeStructure>(`/api/v1/finance/fee-structures/${id}/publish`),
    onSuccess: () => void structures.refetch(),
  })

  const rows = structures.data ?? []
  const total = rows.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0)

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Fee structures"
        description="What each programme or class is charged, broken into its components."
        count={structures.data?.length}
        actions={
          <div className="w-44">
            <Select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Status">
              <option value="">All statuses</option>
              {FEE_STRUCTURE_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {value.charAt(0) + value.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          </div>
        }
      />

      <Panel padded={false}>
        {structures.isLoading ? (
          <div className="p-5">
            <div className="animate-pulse space-y-3">
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="h-10 rounded bg-surface-soft" />
              ))}
            </div>
          </div>
        ) : structures.isError ? (
          <div className="p-5 text-sm text-danger">{describeError(structures.error)}</div>
        ) : rows.length === 0 ? (
          <EmptyState
            title="No fee structures yet"
            description="A fee structure defines what a class is charged and the components that make up the total."
          />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((structure) => (
              <li key={structure.id} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-medium text-ink">{structure.name}</h2>
                      <span className="nums text-xs text-ink-subtle">{structure.code}</span>
                      <StatusBadge status={structure.status} />
                      {!structure.componentsMatchTotal && (
                        <span className="rounded-full bg-warning-soft px-2.5 py-0.5 text-xs font-medium text-warning">
                          Components do not match the total
                        </span>
                      )}
                    </div>
                    {structure.description && (
                      <p className="mt-1 text-sm text-ink-subtle">{structure.description}</p>
                    )}
                  </div>
                  <div className="text-right">
                    <p className="nums text-lg font-semibold text-ink">{money(structure.totalAmount)}</p>
                    <p className="text-xs text-ink-subtle">
                      Components {money(structure.componentTotal)}
                    </p>
                  </div>
                </div>

                {structure.components.length > 0 && (
                  <ul className="mt-4 flex flex-wrap gap-2">
                    {structure.components.map((component) => (
                      <li
                        key={component.id}
                        className="rounded-lg border border-border bg-canvas px-3 py-1.5 text-xs"
                      >
                        <span className="text-ink-muted">{component.name}</span>{' '}
                        <span className="nums font-medium text-ink">{money(component.amount)}</span>
                      </li>
                    ))}
                  </ul>
                )}

                {can('FEE_CREATE') && structure.status === 'DRAFT' && (
                  <div className="mt-4">
                    <Button
                      variant="secondary"
                      loading={publish.isPending}
                      onClick={() => publish.mutate(structure.id)}
                    >
                      Publish
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {rows.length > 0 && (
        <p className="nums text-sm text-ink-subtle">
          Combined headline value of every structure listed: {money(total)}
        </p>
      )}
    </div>
  )
}

export function ReceivablesPage() {
  const report = useQuery({
    queryKey: ['receivables'],
    queryFn: () => api<import('./types').ReceivablesReport>('/api/v1/finance/reports/receivables'),
  })

  // The report keys rows by student id only, so names are resolved from the student list.
  const students = useQuery({
    queryKey: ['students', 'receivables-names'],
    queryFn: () =>
      api<import('../../lib/api').PageResponse<{ id: string; fullName: string }>>('/api/v1/students', {
        query: { size: 200, sort: 'studentNumber,asc' },
      }),
  })

  const studentNames = useMemo(() => {
    const map = new Map<string, string>()
    for (const student of students.data?.data ?? []) map.set(student.id, student.fullName)
    return map
  }, [students.data])

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Receivables"
        description="Everything owed, and how much of it has passed its due date."
      />

      {report.isLoading ? (
        <Panel>
          <div className="animate-pulse space-y-3">
            {Array.from({ length: 5 }).map((_, index) => (
              <div key={index} className="h-9 rounded bg-surface-soft" />
            ))}
          </div>
        </Panel>
      ) : report.isError || !report.data ? (
        <Panel>
          <p className="text-sm text-danger">{describeError(report.error)}</p>
        </Panel>
      ) : (
        <>
          {(() => {
            const data = report.data
            return (
              <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Panel title="Outstanding">
              <p className="nums text-2xl font-semibold text-ink">{money(data.totalOutstanding)}</p>
            </Panel>
            <Panel title="Overdue">
              <p className="nums text-2xl font-semibold text-danger">{money(data.totalOverdue)}</p>
            </Panel>
            <Panel title="Assessments">
              <p className="nums text-2xl font-semibold text-ink">{data.assessmentCount}</p>
            </Panel>
          </div>

          <Panel title="Who owes what" padded={false}>
            {data.rows.length === 0 ? (
              <EmptyState
                title="Nothing is outstanding"
                description="Every assessment on record has been settled in full."
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Assessment
                      </th>
                      <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Student
                      </th>
                      <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Due
                      </th>
                      <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Assessed
                      </th>
                      <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Paid
                      </th>
                      <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Outstanding
                      </th>
                      <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                        Status
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.rows.map((row) => (
                      <tr key={row.assessmentId} className="border-b border-border last:border-0">
                        <td className="px-4 py-3 whitespace-nowrap">
                          <Link
                            to={`/students/${row.studentId}/finance`}
                            className="nums text-primary hover:underline"
                          >
                            {row.assessmentId.slice(0, 8)}
                          </Link>
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {studentNames.get(row.studentId) ?? 'Loading…'}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {date(row.dueDate)}
                          {row.daysOverdue > 0 && (
                            <span className="ml-2 rounded-full bg-danger-soft px-2 py-0.5 text-xs font-medium text-danger">
                              {row.daysOverdue}d overdue
                            </span>
                          )}
                        </td>
                        <td className="nums px-4 py-3 text-right text-ink-muted">
                          {money(row.netAmount)}
                        </td>
                        <td className="nums px-4 py-3 text-right text-ink-muted">
                          {money(row.paidAmount)}
                        </td>
                        <td className="nums px-4 py-3 text-right font-medium text-ink">
                          {money(row.outstandingAmount)}
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={row.status} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>
              </>
            )
          })()}
        </>
      )}
    </div>
  )
}


