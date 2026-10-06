import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post, put, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, humanise, num } from '../../lib/format'
import type { AcademicYear, Semester } from '../academic/types'
import type { Student } from '../students/types'

export interface ReportCardItem {
  subjectName: string
  subjectCode: string
  marksObtained: number | null
  maxMarks: number | null
  credits: number | null
  letterGrade: string | null
  gradePoint: number | null
  pass: boolean | null
}

export interface ReportCard {
  id: string
  studentId: string
  academicYearId: string
  semesterId: string | null
  referenceCode: string
  totalMarks: number | null
  totalCredits: number | null
  gpa: number | null
  cgpa: number | null
  attendancePercentage: number | null
  overallResult: string | null
  status: string
  publishedAt: string | null
  items: ReportCardItem[]
}

export interface Transcript {
  id: string
  studentId: string
  referenceCode: string
  generatedAt: string
  totalCredits: number | null
  cumulativeGpa: number | null
  cumulativeCgpa: number | null
  status: string
  finalisedAt: string | null
  reportCards: ReportCard[]
}

/** The moves ReportCardService allows from each status. */
const CARD_MOVES: Record<string, Array<{ to: string; label: string }>> = {
  DRAFT: [{ to: 'GENERATED', label: 'Mark generated' }],
  GENERATED: [{ to: 'APPROVED', label: 'Approve' }],
  APPROVED: [{ to: 'PUBLISHED', label: 'Publish' }],
}

const CARD_STATUSES = ['DRAFT', 'GENERATED', 'APPROVED', 'PUBLISHED'] as const

/**
 * Report cards are generated per student from approved results, so the screen starts
 * with a student picker and then drives the card through approval and publication.
 */
export function ReportCardsPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [studentId, setStudentId] = useState('')
  const [yearId, setYearId] = useState('')
  const [semesterId, setSemesterId] = useState('')
  const [selected, setSelected] = useState<ReportCard | null>(null)
  const [error, setError] = useState<string | null>(null)

  const years = useQuery({
    queryKey: ['academic-years'],
    queryFn: () => api<PageResponse<AcademicYear>>('/api/v1/academic/academic-years', { query: { size: 100 } }),
  })

  const semesters = useQuery({
    queryKey: ['semesters'],
    queryFn: () => api<Semester[]>('/api/v1/academic/semesters', { query: { size: 100 } }),
  })

  const students = useQuery({
    queryKey: ['report-card-students'],
    queryFn: () =>
      api<PageResponse<Student>>('/api/v1/students', {
        query: { size: 200, sort: 'studentNumber,asc' },
      }),
  })

  const cards = useQuery({
    queryKey: ['report-cards', studentId],
    queryFn: () => api<ReportCard[]>(`/api/v1/exams/report-cards/students/${studentId}`),
    enabled: Boolean(studentId),
  })

  const generate = useMutation({
    mutationFn: () => {
      const query = new URLSearchParams({ studentId })
      if (yearId) query.set('academicYearId', yearId)
      if (semesterId) query.set('semesterId', semesterId)
      return post<ReportCard>(`/api/v1/exams/report-cards/generate?${query.toString()}`)
    },
    onSuccess: (card) => {
      setError(null)
      setSelected(card)
      void queryClient.invalidateQueries({ queryKey: ['report-cards'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const transition = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      put<ReportCard>(`/api/v1/exams/report-cards/${id}/status`, { status }),
    onSuccess: (card) => {
      setError(null)
      setSelected(card)
      void queryClient.invalidateQueries({ queryKey: ['report-cards'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Report cards"
        description="Generated from approved results, then approved and published to the student."
      />

      <Panel>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Student" htmlFor="card-student" required>
            <Select
              id="card-student"
              value={studentId}
              onChange={(event) => {
                setStudentId(event.target.value)
                setSelected(null)
              }}
            >
              <option value="">Choose a student</option>
              {(students.data?.data ?? []).map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName} ({student.studentNumber})
                </option>
              ))}
            </Select>
          </Field>

          {can('REPORT_CARD_GENERATE') && (
            <div className="grid content-end gap-4 sm:grid-cols-2">
              <Field label="Academic year" htmlFor="card-year">
                <Select id="card-year" value={yearId} onChange={(event) => setYearId(event.target.value)}>
                  <option value="">Every year</option>
                  {(years.data?.data ?? []).map((year) => (
                    <option key={year.id} value={year.id}>
                      {year.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Semester" htmlFor="card-semester">
                <Select
                  id="card-semester"
                  value={semesterId}
                  onChange={(event) => setSemesterId(event.target.value)}
                >
                  <option value="">Every semester</option>
                  {(semesters.data ?? []).map((semester) => (
                    <option key={semester.id} value={semester.id}>
                      {semester.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <div className="sm:col-span-2">
                <Button
                  loading={generate.isPending}
                  disabled={!studentId}
                  onClick={() => {
                    if (!studentId) {
                      setError('Choose a student first.')
                      return
                    }
                    generate.mutate()
                  }}
                >
                  Generate report card
                </Button>
              </div>
            </div>
          )}
        </div>
        {error && (
          <p role="alert" className="mt-3 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>

      {studentId && (
        <Panel title="Report cards for this student" padded={false}>
          <QueryBoundary
            isLoading={cards.isLoading}
            error={cards.error}
            data={cards.data}
            onRetry={() => void cards.refetch()}
            loadingRows={3}
            empty={
              <EmptyState
                title="No report card yet"
                description="Generate one once this student has approved results."
              />
            }
          >
            {(data) => (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Reference</Th>
                      <Th>Period</Th>
                      <Th className="text-right">Credits</Th>
                      <Th className="text-right">GPA</Th>
                      <Th>Outcome</Th>
                      <Th>Status</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.map((card) => (
                      <tr key={card.id} className="border-b border-border last:border-0">
                        <td className="nums px-4 py-3 font-medium">{card.referenceCode}</td>
                        <td className="px-4 py-3 text-ink-muted">
                          {card.semesterId
                            ? (semesters.data ?? []).find((s) => s.id === card.semesterId)?.name ??
                              'Semester'
                            : 'Whole year'}
                        </td>
                        <td className="nums px-4 py-3 text-right">{num(card.totalCredits)}</td>
                        <td className="nums px-4 py-3 text-right">{num(card.gpa)}</td>
                        <td className="px-4 py-3">
                          <Badge tone={card.overallResult === 'PASS' ? 'success' : 'warning'}>
                            {card.overallResult ?? '—'}
                          </Badge>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={card.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => setSelected(card)}
                          >
                            View
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </QueryBoundary>
        </Panel>
      )}

      {selected && (
        <ReportCardView
          card={selected}
          moves={can('REPORT_CARD_GENERATE') ? CARD_MOVES[selected.status] ?? [] : []}
          busy={transition.isPending}
          onTransition={(status) => transition.mutate({ id: selected.id, status })}
        />
      )}
    </div>
  )
}

function Th({ children, className = '' }: { children?: React.ReactNode; className?: string }) {
  return (
    <th
      className={`px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase ${className}`}
    >
      {children}
    </th>
  )
}

export function ReportCardView({
  card,
  moves,
  busy,
  onTransition,
}: {
  card: ReportCard
  moves: Array<{ to: string; label: string }>
  busy: boolean
  onTransition: (status: string) => void
}) {
  const student = useQuery({
    queryKey: ['student', card.studentId],
    queryFn: () => api<Student>(`/api/v1/students/${card.studentId}`),
  })

  return (
    <Panel
      title={`Report card ${card.referenceCode}`}
      description={student.data?.fullName ?? 'Subject results for the period.'}
      actions={
        <div className="flex gap-2">
          {moves.map((move) => (
            <Button key={move.to} size="sm" loading={busy} onClick={() => onTransition(move.to)}>
              {move.label}
            </Button>
          ))}
        </div>
      }
      padded={false}
    >
      <div className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-4">
        <Stat label="Total marks" value={num(card.totalMarks)} />
        <Stat label="Credits" value={num(card.totalCredits)} />
        <Stat label="GPA" value={num(card.gpa)} />
        <Stat label="CGPA" value={num(card.cgpa)} />
        <Stat label="Attendance" value={card.attendancePercentage != null ? `${num(card.attendancePercentage)}%` : '—'} />
        <Stat label="Status" value={humanise(card.status)} />
        <Stat label="Published" value={card.publishedAt ? date(card.publishedAt) : 'Not yet'} />
        <Stat label="Outcome" value={card.overallResult ?? '—'} />
      </div>
      {card.items.length === 0 ? (
        <EmptyState title="No subjects on this card" description="Approved results would appear here." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <Th>Subject</Th>
                <Th className="text-right">Marks</Th>
                <Th className="text-right">Credits</Th>
                <Th>Grade</Th>
                <Th>Outcome</Th>
              </tr>
            </thead>
            <tbody>
              {card.items.map((item) => (
                <tr key={`${card.id}-${item.subjectCode}`} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <span className="block font-medium text-ink">{item.subjectName}</span>
                    <span className="nums block text-xs text-ink-subtle">{item.subjectCode}</span>
                  </td>
                  <td className="nums px-4 py-3 text-right">
                    {num(item.marksObtained)} / {num(item.maxMarks)}
                  </td>
                  <td className="nums px-4 py-3 text-right text-ink-muted">{num(item.credits)}</td>
                  <td className="px-4 py-3 font-medium text-ink">{item.letterGrade ?? '—'}</td>
                  <td className="px-4 py-3">
                    {item.pass == null ? (
                      <span className="text-ink-subtle">—</span>
                    ) : (
                      <Badge tone={item.pass ? 'success' : 'danger'}>
                        {item.pass ? 'Pass' : 'Fail'}
                      </Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-semibold tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className="nums mt-1 text-sm font-medium text-ink">{value}</p>
    </div>
  )
}

export const REPORT_CARD_STATUSES = CARD_STATUSES

/**
 * A transcript aggregates every report card a student holds, so it is generated once
 * and then finalised; finalising is what makes it usable as a permanent record.
 */
export function TranscriptsPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [studentId, setStudentId] = useState('')
  const [selected, setSelected] = useState<Transcript | null>(null)
  const [error, setError] = useState<string | null>(null)

  const students = useQuery({
    queryKey: ['transcript-students'],
    queryFn: () =>
      api<PageResponse<Student>>('/api/v1/students', {
        query: { size: 200, sort: 'studentNumber,asc' },
      }),
  })

  const generate = useMutation({
    mutationFn: () =>
      post<Transcript>(`/api/v1/exams/transcripts/generate?studentId=${studentId}`),
    onSuccess: (transcript) => {
      setError(null)
      setSelected(transcript)
      void queryClient.invalidateQueries({ queryKey: ['transcripts'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const finalise = useMutation({
    mutationFn: (id: string) => put<Transcript>(`/api/v1/exams/transcripts/${id}/finalise`),
    onSuccess: (transcript) => {
      setError(null)
      setSelected(transcript)
      void queryClient.invalidateQueries({ queryKey: ['transcripts'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Transcripts"
        description="A cumulative record across every report card a student holds."
      />

      <Panel>
        <div className="grid items-end gap-4 sm:grid-cols-2">
          <Field label="Student" htmlFor="transcript-student" required>
            <Select
              id="transcript-student"
              value={studentId}
              onChange={(event) => {
                setStudentId(event.target.value)
                setSelected(null)
              }}
            >
              <option value="">Choose a student</option>
              {(students.data?.data ?? []).map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName} ({student.studentNumber})
                </option>
              ))}
            </Select>
          </Field>
          {can('TRANSCRIPT_GENERATE') && (
            <Button
              loading={generate.isPending}
              disabled={!studentId}
              onClick={() => generate.mutate()}
            >
              Generate transcript
            </Button>
          )}
        </div>
        {error && (
          <p role="alert" className="mt-3 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>

      {selected ? (
        <Panel
          title={`Transcript ${selected.referenceCode}`}
          description={`${selected.reportCards.length} report cards · ${num(selected.totalCredits)} credits`}
          actions={
            can('TRANSCRIPT_GENERATE') && selected.status === 'DRAFT' ? (
              <Button size="sm" loading={finalise.isPending} onClick={() => finalise.mutate(selected.id)}>
                Finalise
              </Button>
            ) : null
          }
          padded={false}
        >
          <div className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-4">
            <Stat label="Status" value={humanise(selected.status)} />
            <Stat label="Generated" value={date(selected.generatedAt)} />
            <Stat label="Finalised" value={selected.finalisedAt ? date(selected.finalisedAt) : 'Not yet'} />
            <Stat label="Cumulative GPA" value={num(selected.cumulativeGpa)} />
          </div>
          {selected.reportCards.length === 0 ? (
            <EmptyState
              title="No report cards on this transcript"
              description="Generate the student's report cards before issuing a transcript."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Report card</Th>
                    <Th>Period</Th>
                    <Th className="text-right">Credits</Th>
                    <Th className="text-right">GPA</Th>
                    <Th>Outcome</Th>
                    <Th>Status</Th>
                  </tr>
                </thead>
                <tbody>
                  {selected.reportCards.map((card) => (
                    <tr key={card.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium">{card.referenceCode}</td>
                      <td className="px-4 py-3 text-ink-muted">{card.semesterId ? 'Semester' : 'Whole year'}</td>
                      <td className="nums px-4 py-3 text-right">{num(card.totalCredits)}</td>
                      <td className="nums px-4 py-3 text-right">{num(card.gpa)}</td>
                      <td className="px-4 py-3">
                        <Badge tone={card.overallResult === 'PASS' ? 'success' : 'warning'}>
                          {card.overallResult ?? '—'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={card.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      ) : (
        <Panel>
          <EmptyState
            title="No transcript open"
            description="Choose a student and generate their transcript to see every report card at once."
          />
        </Panel>
      )}
    </div>
  )
}