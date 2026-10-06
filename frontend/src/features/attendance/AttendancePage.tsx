import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  EmptyState,
  Field,
  Panel,
  Select,
  StatusBadge,
  TextInput,
  type BadgeTone,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, humanise } from '../../lib/format'
import {
  ATTENDANCE_STATUSES,
  STATUS_TONE,
  type AttendanceRecord,
  type CourseOffering,
  type Page,
} from './types'

/** Today, and the thirty days before it, which is the default window for a review. */
function defaultWindow(): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 30)
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) }
}

export function AttendancePage() {
  const { can } = useAuth()
  const window = useMemo(defaultWindow, [])
  const [offeringId, setOfferingId] = useState('')
  const [from, setFrom] = useState(window.from)
  const [to, setTo] = useState(window.to)

  const offerings = useQuery({
    queryKey: ['offerings', 'attendance'],
    queryFn: () => api<Page<CourseOffering>>('/api/v1/academic/offerings', { query: { size: 200 } }),
  })

  const records = useQuery({
    queryKey: ['attendance', offeringId, from, to],
    queryFn: () =>
      api<AttendanceRecord[]>(`/api/v1/attendance/course-offering/${offeringId}`, { query: { from, to } }),
    enabled: Boolean(offeringId),
  })

  const selected = offerings.data?.data.find((item) => item.id === offeringId)
  const rows = records.data ?? []

  const tally = rows.reduce(
    (counts, record) => {
      counts[record.status] = (counts[record.status] ?? 0) + 1
      return counts
    },
    {} as Record<string, number>,
  )

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Attendance"
        description="Period-by-period attendance for a course offering."
        count={offeringId ? rows.length : undefined}
      />

      <Panel>
        <form
          className="grid gap-4 sm:grid-cols-4"
          onSubmit={(event) => event.preventDefault()}
        >
          <div className="sm:col-span-2">
            <Field label="Course offering" required>
              <Select value={offeringId} onChange={(event) => setOfferingId(event.target.value)}>
                <option value="">Choose an offering</option>
                {offerings.data?.data.map((offering) => (
                  <option key={offering.id} value={offering.id}>
                    {offering.courseCode} · {offering.courseName}
                    {offering.sectionName ? ` · ${offering.sectionName}` : ''}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="From">
            <TextInput type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="To">
            <TextInput type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>
        </form>

        {offerings.data && offerings.data.data.length === 0 && (
          <p className="mt-4 text-sm text-ink-subtle">
            No course offerings exist yet. An offering ties a course, a class and a teacher together for a
            term, and is what attendance is recorded against.
          </p>
        )}
      </Panel>

      {offeringId && selected && (
        <Panel title={selected.courseName} description={`${selected.offeringCode} · ${selected.teacherName ?? 'No teacher assigned'}`}>
          <div className="flex flex-wrap gap-2">
            {ATTENDANCE_STATUSES.map((status) => (
              <Badge key={status} tone={STATUS_TONE[status]}>
                {humanise(status)} {tally[status] ?? 0}
              </Badge>
            ))}
          </div>
        </Panel>
      )}

      {offeringId && (
        <Panel title="Records" padded={false}>
          {records.isLoading ? (
            <p className="p-5 text-sm text-ink-subtle">Loading attendance…</p>
          ) : records.isError ? (
            <p className="p-5 text-sm text-danger">Attendance could not be loaded.</p>
          ) : rows.length === 0 ? (
            <EmptyState
              title="No attendance in this period"
              description="Nothing was marked between the dates chosen."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Date
                    </th>
                    <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Period
                    </th>
                    <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Status
                    </th>
                    <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Late by
                    </th>
                    <th className="hidden lg:table-cell px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Workflow
                    </th>
                    <th className="hidden md:table-cell px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                      Remarks
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((record) => (
                    <tr key={record.id} className="border-b border-border last:border-0">
                      <td className="px-4 py-3 whitespace-nowrap text-ink">{date(record.attendanceDate)}</td>
                      <td className="px-4 py-3 text-ink-muted">
                        {record.periodType ? humanise(record.periodType) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <Badge tone={STATUS_TONE[record.status]}>{humanise(record.status)}</Badge>
                      </td>
                      <td className="nums px-4 py-3 text-ink-muted">
                        {record.minutesLate ? `${record.minutesLate} min` : '—'}
                      </td>
                      <td className="hidden lg:table-cell px-4 py-3">
                        <StatusBadge status={record.workflowStatus} />
                      </td>
                      <td className="hidden md:table-cell px-4 py-3 text-ink-muted">
                        {record.remarks ?? '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {can('ATTENDANCE_MARK') && rows.length === 0 && (
            <p className="border-t border-border px-4 py-3 text-xs text-ink-subtle">
              Registers are marked from the course offering in the academic section.
            </p>
          )}
        </Panel>
      )}

      {!offeringId && (
        <Panel>
          <EmptyState
            title="Choose a course offering"
            description="Attendance is recorded per offering, so that is what the view is built around."
          />
        </Panel>
      )}
    </div>
  )
}

/** One student's attendance in a period, used on the student record. */
export function StudentAttendancePanel({ studentId }: { studentId: string }) {
  const { can } = useAuth()
  const window = useMemo(defaultWindow, [])
  const [from, setFrom] = useState(window.from)
  const [to, setTo] = useState(window.to)

  const summary = useQuery({
    queryKey: ['attendance-summary', studentId, from, to],
    queryFn: () =>
      api<import('./types').AttendanceSummary>(`/api/v1/attendance/students/${studentId}/summary`, {
        query: { from, to },
      }),
  })

  return (
    <Panel
      title="Attendance"
      description="Attendance counts a late or excused period as attending; only an absence loses credit."
      actions={
        <div className="flex items-end gap-2">
          <TextInput
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            aria-label="From"
          />
          <TextInput
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            aria-label="To"
          />
        </div>
      }
    >
      {summary.isLoading ? (
        <p className="text-sm text-ink-subtle">Loading…</p>
      ) : summary.isError ? (
        <p className="text-sm text-danger">Attendance could not be loaded.</p>
      ) : summary.data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <Metric label="Attendance" value={`${summary.data.attendancePercentage}%`} emphasis />
          <Metric label="Periods" value={String(summary.data.totalPeriods)} />
          <Metric label="Present" value={String(summary.data.presentPeriods)} tone="success" />
          <Metric label="Absent" value={String(summary.data.absentPeriods)} tone="danger" />
          <Metric label="Late" value={String(summary.data.latePeriods)} tone="warning" />
        </div>
      ) : (
        <p className="text-sm text-ink-subtle">No attendance recorded for this period.</p>
      )}
      {!can('ATTENDANCE_READ') && (
        <p className="mt-3 text-xs text-ink-subtle">You do not have permission to mark attendance.</p>
      )}
    </Panel>
  )
}

function Metric({
  label,
  value,
  tone,
  emphasis,
}: {
  label: string
  value: string
  tone?: BadgeTone
  emphasis?: boolean
}) {
  const colour =
    tone === 'success'
      ? 'text-primary'
      : tone === 'danger'
        ? 'text-danger'
        : tone === 'warning'
          ? 'text-warning'
          : 'text-ink'
  return (
    <div>
      <p className="text-xs tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className={`nums mt-1 text-xl font-semibold ${emphasis ? 'text-ink' : colour}`}>{value}</p>
    </div>
  )
}


