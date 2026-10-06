import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import { Badge, Button, EmptyState, Panel, Select, StatusBadge } from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, humanise, num } from '../../lib/format'
import type { ExamSubject, Examination, Result } from './types'

/**
 * Examinations hold their own schedule and results, so this is one screen with three views
 * rather than three separate pages: pick an examination, then read its papers or its marks.
 */
export function ExaminationsPage() {
  const exams = useQuery({
    queryKey: ['examinations'],
    queryFn: () => api<Examination[]>('/api/v1/exams'),
  })

  const [selectedId, setSelectedId] = useState('')
  const rows = exams.data ?? []
  const selected = rows.find((exam) => exam.id === selectedId)

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Examinations"
        description="Each examination holds its own papers, marks and publication state."
        count={rows.length}
      />

      <Panel padded={false}>
        {exams.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : exams.isError ? (
          <p className="p-5 text-sm text-danger">{describeError(exams.error)}</p>
        ) : rows.length === 0 ? (
          <EmptyState
            title="No examinations yet"
            description="An examination gathers the papers for a term and drives marks entry and publication."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Examination
                  </th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Type
                  </th>
                  <th className="hidden md:table-cell px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Runs
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Max marks
                  </th>
                  <th className="hidden lg:table-cell px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Pass %
                  </th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Status
                  </th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody>
                {rows.map((exam) => (
                  <tr
                    key={exam.id}
                    className={`border-b border-border last:border-0 ${
                      exam.id === selectedId ? 'bg-primary-soft/40' : 'hover:bg-surface-soft/60'
                    }`}
                  >
                    <td className="px-4 py-3">
                      <span className="block font-medium text-ink">{exam.name}</span>
                      <span className="nums block text-xs text-ink-subtle">{exam.code}</span>
                    </td>
                    <td className="px-4 py-3 text-ink-muted">{humanise(exam.examType)}</td>
                    <td className="hidden md:table-cell px-4 py-3 whitespace-nowrap text-ink-muted">
                      {date(exam.startDate)} → {date(exam.endDate)}
                    </td>
                    <td className="nums px-4 py-3 text-right">{num(exam.maxTotalMarks)}</td>
                    <td className="nums hidden lg:table-cell px-4 py-3 text-right text-ink-muted">
                      {num(exam.passPercentage)}%
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={exam.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button variant="secondary" onClick={() => setSelectedId(exam.id)}>
                        Open
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {selected && (
        <>
          <ExamSchedule examinationId={selected.id} />
          <ExamResults examinationId={selected.id} />
        </>
      )}
    </div>
  )
}

function ExamSchedule({ examinationId }: { examinationId: string }) {
  const schedule = useQuery({
    queryKey: ['exam-schedule', examinationId],
    queryFn: () => api<ExamSubject[]>(`/api/v1/exams/${examinationId}/schedule`),
  })

  const rows = schedule.data ?? []

  return (
    <Panel title="Papers" description="What is sat, when, and for how many marks." padded={false}>
      {schedule.isLoading ? (
        <p className="p-5 text-sm text-ink-subtle">Loading…</p>
      ) : rows.length === 0 ? (
        <EmptyState title="No papers scheduled" description="Add subjects to this examination to build its schedule." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                  Subject
                </th>
                <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                  Date
                </th>
                <th className="hidden md:table-cell px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                  Time
                </th>
                <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                  Marks
                </th>
                <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                  Pass
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((subject) => (
                <tr key={subject.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <span className="block font-medium text-ink">{subject.subjectName}</span>
                    <span className="nums block text-xs text-ink-subtle">{subject.subjectCode}</span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(subject.examDate)}</td>
                  <td className="hidden md:table-cell px-4 py-3 whitespace-nowrap text-ink-muted">
                    {subject.startTime?.slice(0, 5)}–{subject.endTime?.slice(0, 5)}
                  </td>
                  <td className="nums px-4 py-3 text-right">{num(subject.maxMarks)}</td>
                  <td className="nums px-4 py-3 text-right text-ink-muted">{num(subject.passMarks)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}

function ExamResults({ examinationId }: { examinationId: string }) {
  const [status, setStatus] = useState('')
  const results = useQuery({
    queryKey: ['exam-results', examinationId],
    queryFn: () => api<Result[]>(`/api/v1/exams/${examinationId}/results`),
  })

  const rows = results.data ?? []
  const shown = status ? rows.filter((result) => result.status === status) : rows

  const published = rows.filter((result) => result.status === 'PUBLISHED').length
  const passed = rows.filter((result) => result.pass === true).length

  return (
    <Panel
      title="Marks"
      description="A result is only visible to a student once it has been published."
      actions={
        <Select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Status">
          <option value="">All statuses</option>
          {['DRAFT', 'MARKS_ENTERED', 'VERIFIED', 'APPROVED', 'PUBLISHED'].map((value) => (
            <option key={value} value={value}>
              {humanise(value)}
            </option>
          ))}
        </Select>
      }
      padded={false}
    >
      {results.isLoading ? (
        <p className="p-5 text-sm text-ink-subtle">Loading…</p>
      ) : rows.length === 0 ? (
        <EmptyState title="No marks entered yet" />
      ) : (
        <>
          <div className="flex flex-wrap gap-2 border-b border-border px-4 py-3">
            <Badge>{rows.length} results</Badge>
            <Badge tone="info">{published} published</Badge>
            <Badge tone={passed === rows.length ? 'success' : 'warning'}>{passed} passing</Badge>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Marks
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Obtained
                  </th>
                  <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    %
                  </th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Grade
                  </th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Outcome
                  </th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {shown.map((result) => (
                  <tr key={result.id} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-3 text-ink-muted">out of {num(result.maxMarks)}</td>
                    <td className="nums px-4 py-3 text-right font-medium">{num(result.marksObtained)}</td>
                    <td className="nums px-4 py-3 text-right text-ink-muted">{num(result.percentage)}</td>
                    <td className="px-4 py-3">
                      <span className="font-medium text-ink">{result.letterGrade ?? '—'}</span>
                      {result.gradePoint != null && (
                        <span className="nums ml-1 text-xs text-ink-subtle">({num(result.gradePoint)})</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {result.pass == null ? (
                        <span className="text-ink-subtle">Not graded</span>
                      ) : (
                        <Badge tone={result.pass ? 'success' : 'danger'}>
                          {result.pass ? 'Pass' : 'Fail'}
                        </Badge>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={result.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {shown.length === 0 && (
            <p className="border-t border-border px-4 py-3 text-sm text-ink-subtle">
              No result is at that stage of the workflow.
            </p>
          )}
        </>
      )}
    </Panel>
  )
}

/** One student's results, used on the student record. */
export function StudentResultsPanel({ studentId }: { studentId: string }) {
  const results = useQuery({
    queryKey: ['student-results', studentId],
    queryFn: () =>
      api<{ status: string }[]>(`/api/v1/exams/results/students/${studentId}/status`),
  })

  return (
    <Panel title="Results" padded={false}>
      {results.isLoading ? (
        <p className="p-5 text-sm text-ink-subtle">Loading…</p>
      ) : results.isError ? (
        <p className="p-5 text-sm text-danger">{describeError(results.error)}</p>
      ) : (results.data ?? []).length === 0 ? (
        <EmptyState title="No results recorded" />
      ) : (
        <ul className="divide-y divide-border">
          {(results.data ?? []).map((result, index) => (
            <li key={`${result.status}-${index}`} className="flex items-center justify-between px-4 py-3">
              <span className="text-sm text-ink-muted">Result {index + 1}</span>
              <StatusBadge status={result.status} />
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}


