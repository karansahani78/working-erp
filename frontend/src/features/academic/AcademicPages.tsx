import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, del, post, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  QueryBoundary,
  Select,
  TextArea,
  TextInput,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, text } from '../../lib/format'
import type {
  AcademicYear,
  Course,
  Department,
  Program,
  Room,
  Section,
  Semester,
  TimeSlot,
} from './types'

/* --------------------------------------------------------------- curriculum */

export interface ProgramVersion {
  id: string
  programId: string
  programCode: string
  label: string
  effectiveFrom: string | null
  effectiveTo: string | null
  totalCredits: number | null
  status: string
}

export interface Curriculum {
  id: string
  programVersionId: string
  programVersionLabel: string
  name: string
  description: string | null
  totalCredits: number | null
  placedCredits: number | null
  complete: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
  active: boolean
}

export interface CurriculumCourse {
  id: string
  courseId: string
  courseCode: string
  courseName: string
  courseCredits: number | null
  semesterId: string | null
  semesterName: string | null
  semesterOrdinal: number | null
  requirementType: string
  creditHours: number | null
  internalMarks: number | null
  externalMarks: number | null
  totalMarks: number | null
  electiveGroup: string | null
  ordinal: number | null
}

/** Mirrors CurriculumCourse.RequirementType on the server. */
export const REQUIREMENT_TYPES = ['MANDATORY', 'ELECTIVE', 'OPTIONAL'] as const

/** Mirrors AcademicCalendarEvent.EventType; the server rejects anything else with a 500. */
export const EVENT_TYPES = [
  'ACADEMIC_START',
  'ACADEMIC_END',
  'REGISTRATION_START',
  'REGISTRATION_END',
  'TEACHING_START',
  'TEACHING_END',
  'EXAM_START',
  'EXAM_END',
  'RESULT_PUBLICATION',
  'ADMISSION_START',
  'ADMISSION_END',
  'FEE_DEADLINE',
  'HOLIDAY',
  'EVENT',
] as const

/**
 * Curriculum is the chain a registrar builds: a programme, a version of it, and the
 * courses that version requires. Each step depends on the one above it.
 */
export function CurriculumPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const programs = useQuery({
    queryKey: ['programs'],
    queryFn: () => api<Program[]>('/api/v1/academic/programs'),
  })

  const [programId, setProgramId] = useState('')

  const versions = useQuery({
    queryKey: ['program-versions', programId],
    queryFn: () => api<ProgramVersion[]>(`/api/v1/academic/programs/${programId}/versions`),
    enabled: Boolean(programId),
  })

  const [versionId, setVersionId] = useState('')

  const curricula = useQuery({
    queryKey: ['curricula', versionId],
    queryFn: () => api<Curriculum[]>(`/api/v1/academic/programs/versions/${versionId}/curricula`),
    enabled: Boolean(versionId),
  })

  const [curriculumId, setCurriculumId] = useState('')

  const courses = useQuery({
    queryKey: ['curriculum-courses', curriculumId],
    queryFn: () => api<CurriculumCourse[]>(`/api/v1/academic/programs/curricula/${curriculumId}/courses`),
    enabled: Boolean(curriculumId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['program-versions'] })
    void queryClient.invalidateQueries({ queryKey: ['curricula'] })
  }

  const invalidateCurricula = () => {
    void queryClient.invalidateQueries({ queryKey: ['curricula'] })
    void queryClient.invalidateQueries({ queryKey: ['curriculum-courses'] })
  }

  const removeCourse = useMutation({
    // The server removes the placement record, so its own id is needed rather than the course id.
    mutationFn: (placementId: string) =>
      del<void>(`/api/v1/academic/programs/curricula/${curriculumId}/courses/${placementId}`),
    onSuccess: () => {
      setError(null)
      invalidateCurricula()
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Curriculum"
        description="What each programme requires, version by version."
      />

      {can('ACADEMIC_CREATE') && (
        <ProgramForm
          departments={[]}
          onCreated={(created) => {
            void queryClient.invalidateQueries({ queryKey: ['programs'] })
            setProgramId(created.id)
          }}
        />
      )}

      {programId && can('ACADEMIC_CREATE') && (
        <VersionForm programId={programId} onCreated={invalidate} />
      )}

      {versionId && can('ACADEMIC_CREATE') && (
        <CurriculumForm versionId={versionId} onCreated={invalidateCurricula} />
      )}

      <Panel title="Browse the curriculum">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Programme" htmlFor="curriculum-program">
            <Select
              id="curriculum-program"
              value={programId}
              onChange={(event) => {
                setProgramId(event.target.value)
                setVersionId('')
                setCurriculumId('')
              }}
            >
              <option value="">Choose a programme</option>
              {(programs.data ?? []).map((program) => (
                <option key={program.id} value={program.id}>
                  {program.name}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Version" htmlFor="curriculum-version">
            <Select
              id="curriculum-version"
              value={versionId}
              onChange={(event) => {
                setVersionId(event.target.value)
                setCurriculumId('')
              }}
              disabled={!programId}
            >
              <option value="">{programId ? 'Choose a version' : 'Choose a programme first'}</option>
              {(versions.data ?? []).map((version) => (
                <option key={version.id} value={version.id}>
                  {version.label} ({text(version.status)})
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Curriculum" htmlFor="curriculum-pick">
            <Select
              id="curriculum-pick"
              value={curriculumId}
              onChange={(event) => setCurriculumId(event.target.value)}
              disabled={!versionId}
            >
              <option value="">{versionId ? 'Choose a curriculum' : 'Choose a version first'}</option>
              {(curricula.data ?? []).map((curriculum) => (
                <option key={curriculum.id} value={curriculum.id}>
                  {curriculum.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        {curriculumId && (
          <p className="mt-4 text-sm text-ink-muted">
            {(() => {
              const curriculum = (curricula.data ?? []).find((item) => item.id === curriculumId)
              if (!curriculum) return ''
              const placed = curriculum.placedCredits ?? 0
              const total = curriculum.totalCredits ?? 0
              return `${placed} of ${total || '—'} credits placed, so this curriculum is ${
                curriculum.complete ? 'complete' : 'still open'
              }.`
            })()}
          </p>
        )}
      </Panel>

      {curriculumId && can('ACADEMIC_CREATE') && (
        <CurriculumCourseForm curriculumId={curriculumId} onAdded={invalidateCurricula} />
      )}

      <Panel title="Courses in this curriculum" padded={false}>
        {!curriculumId ? (
          <EmptyState
            title="Choose a curriculum"
            description="Pick a programme, a version and a curriculum to see the courses it requires."
          />
        ) : (
          <QueryBoundary
            isLoading={courses.isLoading}
            error={courses.error}
            data={courses.data}
            onRetry={() => void courses.refetch()}
            loadingRows={5}
            empty={
              <EmptyState
                title="No courses yet"
                description="Add the courses this curriculum requires, one semester at a time."
              />
            }
          >
            {(data) => (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>#</Th>
                      <Th>Course</Th>
                      <Th className="hidden md:table-cell">Semester</Th>
                      <Th>Requirement</Th>
                      <Th className="hidden lg:table-cell">Credits</Th>
                      <Th className="hidden lg:table-cell">Marks</Th>
                      {can('ACADEMIC_DELETE') && <Th className="text-right">Remove</Th>}
                    </tr>
                  </thead>
                  <tbody>
                    {data.map((course) => (
                      <tr key={course.id} className="border-b border-border last:border-0">
                        <td className="nums px-4 py-3 text-ink-muted">{course.ordinal ?? '—'}</td>
                        <td className="px-4 py-3">
                          <span className="nums text-ink-muted">{course.courseCode}</span>{' '}
                          <span className="font-medium text-ink">{course.courseName}</span>
                        </td>
                        <td className="px-4 py-3 text-ink-muted">
                          {course.semesterName ?? 'Any semester'}
                        </td>
                        <td className="px-4 py-3">
                          <Badge tone="neutral">{text(course.requirementType)}</Badge>
                          {course.electiveGroup && (
                            <span className="ml-2 text-xs text-ink-subtle">{course.electiveGroup}</span>
                          )}
                        </td>
                        <td className="nums px-4 py-3 text-ink-muted">{course.creditHours ?? '—'}</td>
                        <td className="nums px-4 py-3 text-ink-muted">{course.totalMarks ?? '—'}</td>
                        {can('ACADEMIC_DELETE') && (
                          <td className="px-4 py-3 text-right">
                            <Button
                              size="sm"
                              variant="ghost"
                              loading={removeCourse.isPending}
                              onClick={() => removeCourse.mutate(course.id)}
                            >
                              Remove
                            </Button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </QueryBoundary>
        )}
        {error && (
          <p role="alert" className="border-t border-border p-4 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>
    </div>
  )
}

function ProgramForm({
  departments,
  onCreated,
}: {
  departments: Department[]
  onCreated: (program: Program) => void
}) {
  const [error, setError] = useState<string | null>(null)

  const list = useQuery({
    queryKey: ['departments', 'curriculum'],
    queryFn: () => api<PageResponse<Department>>('/api/v1/academic/departments', { query: { size: 100 } }),
  })

  const create = useMutation({
    mutationFn: (values: { code: string; name: string; level?: string; durationYears?: number; durationSemesters?: number; departmentId?: string }) =>
      post<Program>('/api/v1/academic/programs', values),
    onSuccess: (program) => {
      setError(null)
      onCreated(program)
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      code: value('code') ?? '',
      name: value('name') ?? '',
      level: value('level'),
      durationYears: value('durationYears') ? Number(value('durationYears')) : undefined,
      durationSemesters: value('durationSemesters') ? Number(value('durationSemesters')) : undefined,
      departmentId: value('departmentId'),
    })
  }

  return (
    <Panel title="Add a programme" description="A course of study, such as Grade 6 or the Science stream.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <Field label="Code" htmlFor="program-code" required>
          <TextInput id="program-code" name="code" placeholder="SCI" required />
        </Field>
        <Field label="Name" htmlFor="program-name" required>
          <TextInput id="program-name" name="name" placeholder="Science" required />
        </Field>
        <Field label="Level" htmlFor="program-level">
          <TextInput id="program-level" name="level" placeholder="SECONDARY" />
        </Field>
        <Field label="Department" htmlFor="program-department">
          <Select id="program-department" name="departmentId" defaultValue="">
            <option value="">Not assigned</option>
            {(list.data?.data ?? departments).map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Years" htmlFor="program-years">
          <TextInput id="program-years" name="durationYears" type="number" min={1} max={12} />
        </Field>
        <Field label="Semesters" htmlFor="program-semesters">
          <TextInput id="program-semesters" name="durationSemesters" type="number" min={1} max={24} />
        </Field>
        {error && <p className="sm:col-span-4 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-4">
          <Button type="submit" loading={create.isPending}>
            Create programme
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function VersionForm({ programId, onCreated }: { programId: string; onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (values: { programId: string; label: string; effectiveFrom?: string; effectiveTo?: string; totalCredits?: number }) =>
      post<ProgramVersion>('/api/v1/academic/programs/versions', values),
    onSuccess: () => {
      setError(null)
      onCreated()
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      programId,
      label: value('label') ?? '',
      effectiveFrom: value('effectiveFrom'),
      effectiveTo: value('effectiveTo'),
      totalCredits: value('totalCredits') ? Number(value('totalCredits')) : undefined,
    })
  }

  return (
    <Panel title="Add a programme version" description="A revision of a programme, so old and new cohorts can differ.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <Field label="Label" htmlFor="version-label" required>
          <TextInput id="version-label" name="label" placeholder="2026 revision" required />
        </Field>
        <Field label="Effective from" htmlFor="version-from">
          <TextInput id="version-from" name="effectiveFrom" type="date" />
        </Field>
        <Field label="Effective to" htmlFor="version-to">
          <TextInput id="version-to" name="effectiveTo" type="date" />
        </Field>
        <Field label="Total credits" htmlFor="version-credits">
          <TextInput id="version-credits" name="totalCredits" type="number" min={0} />
        </Field>
        {error && <p className="sm:col-span-4 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-4">
          <Button type="submit" loading={create.isPending}>
            Create version
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function CurriculumForm({ versionId, onCreated }: { versionId: string; onCreated: () => void }) {
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (values: { programVersionId: string; name: string; description?: string; totalCredits?: number; effectiveFrom?: string; effectiveTo?: string }) =>
      post<Curriculum>('/api/v1/academic/programs/curricula', values),
    onSuccess: () => {
      setError(null)
      onCreated()
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      programVersionId: versionId,
      name: value('name') ?? '',
      description: value('description'),
      totalCredits: value('totalCredits') ? Number(value('totalCredits')) : undefined,
      effectiveFrom: value('effectiveFrom'),
      effectiveTo: value('effectiveTo'),
    })
  }

  return (
    <Panel title="Add a curriculum" description="One arrangement of courses for this programme version.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="Name" htmlFor="curriculum-name" required>
          <TextInput id="curriculum-name" name="name" placeholder="Standard science stream" required />
        </Field>
        <Field label="Total credits" htmlFor="curriculum-total">
          <TextInput id="curriculum-total" name="totalCredits" type="number" min={0} />
        </Field>
        <Field label="Effective from" htmlFor="curriculum-from">
          <TextInput id="curriculum-from" name="effectiveFrom" type="date" />
        </Field>
        <div className="sm:col-span-3">
          <Field label="Description" htmlFor="curriculum-description">
            <TextArea id="curriculum-description" name="description" />
          </Field>
        </div>
        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-3">
          <Button type="submit" loading={create.isPending}>
            Create curriculum
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function CurriculumCourseForm({ curriculumId, onAdded }: { curriculumId: string; onAdded: () => void }) {
  const [error, setError] = useState<string | null>(null)

  const courses = useQuery({
    queryKey: ['courses', 'all'],
    queryFn: () => api<PageResponse<Course>>('/api/v1/academic/courses', { query: { size: 200 } }),
  })

  const semesters = useQuery({
    queryKey: ['semesters', 'curriculum'],
    queryFn: () => api<Semester[]>('/api/v1/academic/semesters'),
  })

  const add = useMutation({
    mutationFn: (values: { courseId: string; semesterId?: string; requirementType: string; creditHours?: number; internalMarks?: number; externalMarks?: number; electiveGroup?: string; ordinal?: number }) =>
      post<CurriculumCourse>(`/api/v1/academic/programs/curricula/${curriculumId}/courses`, values),
    onSuccess: () => {
      setError(null)
      onAdded()
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    add.mutate({
      courseId: value('courseId') ?? '',
      semesterId: value('semesterId'),
      requirementType: value('requirementType') ?? 'MANDATORY',
      creditHours: value('creditHours') ? Number(value('creditHours')) : undefined,
      internalMarks: value('internalMarks') ? Number(value('internalMarks')) : undefined,
      externalMarks: value('externalMarks') ? Number(value('externalMarks')) : undefined,
      electiveGroup: value('electiveGroup'),
      ordinal: value('ordinal') ? Number(value('ordinal')) : undefined,
    })
  }

  return (
    <Panel title="Add a course to this curriculum">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <Field label="Course" htmlFor="curriculum-course" required>
          <Select id="curriculum-course" name="courseId" required defaultValue="">
            <option value="">Choose a course</option>
            {(courses.data?.data ?? []).map((course) => (
              <option key={course.id} value={course.id}>
                {course.code} — {course.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Semester" htmlFor="curriculum-course-semester">
          <Select id="curriculum-course-semester" name="semesterId" defaultValue="">
            <option value="">Any semester</option>
            {(semesters.data ?? []).map((semester) => (
              <option key={semester.id} value={semester.id}>
                {semester.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Requirement" htmlFor="curriculum-course-requirement">
          <Select
            id="curriculum-course-requirement"
            name="requirementType"
            defaultValue="MANDATORY"
          >
            {REQUIREMENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Order" htmlFor="curriculum-course-ordinal">
          <TextInput id="curriculum-course-ordinal" name="ordinal" type="number" min={1} />
        </Field>
        <Field label="Credit hours" htmlFor="curriculum-course-credits">
          <TextInput id="curriculum-course-credits" name="creditHours" type="number" min={0} />
        </Field>
        <Field label="Internal marks" htmlFor="curriculum-course-internal">
          <TextInput id="curriculum-course-internal" name="internalMarks" type="number" min={0} />
        </Field>
        <Field label="External marks" htmlFor="curriculum-course-external">
          <TextInput id="curriculum-course-external" name="externalMarks" type="number" min={0} />
        </Field>
        <Field label="Elective group" htmlFor="curriculum-course-group">
          <TextInput id="curriculum-course-group" name="electiveGroup" placeholder="Science options" />
        </Field>
        {error && <p className="sm:col-span-4 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-4">
          <Button type="submit" loading={add.isPending}>
            Add course
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/* ----------------------------------------------------------------- timetable */

export interface TimetableEntry {
  id: string
  dayOfWeek: string
  timeSlotId: string
  timeSlotName: string
  time: { start: string; end: string }
  courseOfferingId: string
  courseCode: string
  courseName: string
  offeringCode: string
  sectionId: string | null
  sectionName: string | null
  schoolClassId: string | null
  schoolClassName: string | null
  roomId: string | null
  roomName: string | null
  teacherId: string | null
  teacherName: string | null
  effectiveFrom: string | null
  effectiveTo: string | null
  active: boolean
}

export interface TimetableSlot {
  dayOfWeek: string
  timeSlotId: string
  timeSlotName: string
  start: string
  end: string
  entries: TimetableEntry[]
}

export interface TimetableGrid {
  scope: string
  scopeId: string | null
  slots: TimetableSlot[]
}

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'] as const

export function TimetablePage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [sectionId, setSectionId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const sections = useQuery({
    queryKey: ['sections', 'timetable'],
    // The server returns a plain list here, unlike the paginated offerings below.
    queryFn: () => api<Section[]>('/api/v1/academic/sections'),
  })

  const grid = useQuery({
    queryKey: ['timetable', sectionId],
    queryFn: () =>
      api<TimetableGrid>('/api/v1/academic/timetable', {
        query: { sectionId: sectionId || undefined },
      }),
  })

  const removeEntry = useMutation({
    mutationFn: (entryId: string) => del<void>(`/api/v1/academic/timetable/${entryId}`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['timetable'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  const slotKeys = Array.from(
    new Map((grid.data?.slots ?? []).map((slot) => [slot.timeSlotId, slot])).values(),
  )

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Timetable"
        description="The weekly grid, built from offerings, rooms and time slots."
      />

      <Panel>
        <div className="max-w-sm">
          <Field label="Section" htmlFor="timetable-section">
            <Select
              id="timetable-section"
              value={sectionId}
              onChange={(event) => setSectionId(event.target.value)}
            >
              <option value="">All sections</option>
              {(sections.data ?? []).map((section) => (
                <option key={section.id} value={section.id}>
                  {/* Section names repeat across classes, so the class disambiguates them. */}
                  {section.schoolClassName ? `${section.schoolClassName} · ${section.name}` : section.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Panel>

      {can('ACADEMIC_CREATE') && <TimetableForm sectionId={sectionId} />}

      <Panel title="Weekly grid" padded={false}>
        <QueryBoundary
          isLoading={grid.isLoading}
          error={grid.error}
          data={grid.data}
          onRetry={() => void grid.refetch()}
          loadingRows={4}
          empty={
            <EmptyState
              title="No periods scheduled"
              description="Add a period below to start building the week."
            />
          }
        >
          {(data) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th className="w-40">Period</Th>
                    {DAYS.map((day) => (
                      <Th key={day}>{text(day)}</Th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {slotKeys.map((slot) => (
                    <tr key={slot.timeSlotId} className="border-b border-border align-top last:border-0">
                      <td className="px-4 py-3">
                        <span className="block font-medium text-ink">{slot.timeSlotName}</span>
                        <span className="nums text-xs text-ink-subtle">
                          {slot.start}–{slot.end}
                        </span>
                      </td>
                      {DAYS.map((day) => {
                        const cell = (data.slots ?? []).find(
                          (candidate) => candidate.dayOfWeek === day && candidate.timeSlotId === slot.timeSlotId,
                        )
                        return (
                          <td key={day} className="px-2 py-2">
                            {(cell?.entries ?? []).length === 0 ? (
                              <span className="text-xs text-ink-subtle">—</span>
                            ) : (
                              <ul className="flex flex-col gap-1">
                                {(cell?.entries ?? []).map((entry) => (
                                  <li key={entry.id} className="rounded-md bg-surface-soft px-2 py-1">
                                    <span className="block text-xs font-medium text-ink">
                                      {entry.courseCode} {entry.courseName}
                                    </span>
                                    <span className="block text-xs text-ink-subtle">
                                      {entry.teacherName ?? 'No teacher'} · {entry.roomName ?? 'No room'}
                                    </span>
                                    {can('ACADEMIC_DELETE') && (
                                      <button
                                        type="button"
                                        className="mt-1 text-xs text-danger hover:underline"
                                        onClick={() => removeEntry.mutate(entry.id)}
                                      >
                                        Remove
                                      </button>
                                    )}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>

        {error && (
          <p role="alert" className="border-t border-border p-4 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>
    </div>
  )
}

function TimetableForm({ sectionId }: { sectionId: string }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const slots = useQuery({
    queryKey: ['time-slots'],
    queryFn: () => api<TimeSlot[]>('/api/v1/academic/time-slots'),
  })

  const offerings = useQuery({
    queryKey: ['offerings', 'timetable'],
    queryFn: () =>
      api<PageResponse<import('./types').CourseOffering>>('/api/v1/academic/offerings', {
        query: { sectionId: sectionId || undefined, size: 200, active: true },
      }),
  })

  const rooms = useQuery({
    queryKey: ['rooms'],
    queryFn: () => api<Room[]>('/api/v1/academic/rooms'),
  })

  const create = useMutation({
    mutationFn: (values: {
      dayOfWeek: string
      timeSlotId: string
      courseOfferingId: string
      sectionId?: string
      roomId?: string
    }) => post<TimetableEntry>('/api/v1/academic/timetable', values),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['timetable'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      dayOfWeek: value('dayOfWeek') ?? 'MONDAY',
      timeSlotId: value('timeSlotId') ?? '',
      courseOfferingId: value('courseOfferingId') ?? '',
      sectionId: sectionId || undefined,
      roomId: value('roomId'),
    })
  }

  if ((slots.data ?? []).length === 0 || (offerings.data?.data ?? []).length === 0) {
    return (
      <Panel title="Add a period">
        <p className="text-sm text-ink-muted">
          {(slots.data ?? []).length === 0
            ? 'Define time slots before scheduling periods.'
            : 'Create a course offering before scheduling it into the week.'}
        </p>
      </Panel>
    )
  }

  return (
    <Panel
      title="Add a period"
      description={sectionId ? 'Scheduled into the section selected above.' : 'Scheduled without a section.'}
    >
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <Field label="Day" htmlFor="timetable-day" required>
          <Select id="timetable-day" name="dayOfWeek" defaultValue="MONDAY">
            {DAYS.map((day) => (
              <option key={day} value={day}>
                {text(day)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Time slot" htmlFor="timetable-slot" required>
          <Select id="timetable-slot" name="timeSlotId" required defaultValue="">
            <option value="">Choose a slot</option>
            {(slots.data ?? []).map((slot) => (
              <option key={slot.id} value={slot.id}>
                {slot.name} ({slot.startTime}–{slot.endTime})
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Offering" htmlFor="timetable-offering" required>
          <Select id="timetable-offering" name="courseOfferingId" required defaultValue="">
            <option value="">Choose an offering</option>
            {(offerings.data?.data ?? []).map((offering) => (
              <option key={offering.id} value={offering.id}>
                {offering.offeringCode} — {offering.courseCode}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Room" htmlFor="timetable-room">
          <Select id="timetable-room" name="roomId" defaultValue="">
            <option value="">No room</option>
            {(rooms.data ?? []).map((room) => (
              <option key={room.id} value={room.id}>
                {room.name}
              </option>
            ))}
          </Select>
        </Field>
        {error && <p className="sm:col-span-4 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-4">
          <Button type="submit" loading={create.isPending}>
            Add period
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/* ------------------------------------------------------------------ calendar */

export interface CalendarEvent {
  id: string
  title: string
  eventType: string
  academicYearId: string
  semesterId: string | null
  startDate: string
  endDate: string
  workingDay: boolean
  description: string | null
}

export function CalendarPage() {
  const { can } = useAuth()
  const [yearId, setYearId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const years = useQuery({
    queryKey: ['academic-years'],
    queryFn: () => api<PageResponse<AcademicYear>>('/api/v1/academic/academic-years', { query: { size: 50 } }),
  })

  const effectiveYear = yearId || years.data?.data.find((year) => year.current)?.id || ''

  const events = useQuery({
    queryKey: ['academic-calendar', effectiveYear],
    queryFn: () => api<CalendarEvent[]>('/api/v1/academic/calendar', { query: { yearId: effectiveYear } }),
    enabled: Boolean(effectiveYear),
  })

  const remove = useMutation({
    mutationFn: (id: string) => del<void>(`/api/v1/academic/calendar/${id}`),
    onSuccess: () => {
      setError(null)
      void events.refetch()
    },
    onError: (err) => setError(describeError(err)),
  })

  const byMonth = new Map<string, CalendarEvent[]>()
  for (const event of events.data ?? []) {
    const month = event.startDate.slice(0, 7)
    byMonth.set(month, [...(byMonth.get(month) ?? []), event])
  }
  const months = Array.from(byMonth.entries()).sort(([a], [b]) => a.localeCompare(b))

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Calendar"
        description="Terms, holidays and exams, so the school year has one shared shape."
      />

      <Panel>
        <div className="max-w-sm">
          <Field label="Academic year" htmlFor="calendar-year">
            <Select id="calendar-year" value={effectiveYear} onChange={(event) => setYearId(event.target.value)}>
              <option value="">Choose a year</option>
              {(years.data?.data ?? []).map((year) => (
                <option key={year.id} value={year.id}>
                  {year.name}
                  {year.current ? ' (current)' : ''}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Panel>

      {can('ACADEMIC_CREATE') && effectiveYear && (
        <CalendarForm
          yearId={effectiveYear}
          onCreated={() => void events.refetch()}
          onError={setError}
        />
      )}

      <Panel title="Events" padded={false}>
        <QueryBoundary
          isLoading={events.isLoading}
          error={events.error}
          data={events.data}
          onRetry={() => void events.refetch()}
          loadingRows={5}
          empty={
            <EmptyState
              title="Nothing scheduled"
              description="Add terms, holidays and exam windows so every screen agrees on the calendar."
            />
          }
        >
          {() => (
            <ul className="divide-y divide-border">
              {months.map(([month, monthEvents]) => (
                <li key={month}>
                  <h3 className="bg-surface-soft px-4 py-2 text-xs font-medium tracking-wide text-ink-subtle uppercase">
                    {new Date(`${month}-01T00:00:00`).toLocaleString(undefined, {
                      month: 'long',
                      year: 'numeric',
                    })}
                  </h3>
                  <ul className="divide-y divide-border">
                    {monthEvents.map((event) => (
                      <li key={event.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                        <Badge tone="neutral">{text(event.eventType)}</Badge>
                        <span className="font-medium text-ink">{event.title}</span>
                        <span className="nums text-ink-muted">
                          {date(event.startDate)} – {date(event.endDate)}
                        </span>
                        <Badge tone={event.workingDay ? 'success' : 'warning'}>
                          {event.workingDay ? 'Working day' : 'Holiday'}
                        </Badge>
                        {can('ACADEMIC_DELETE') && (
                          <Button
                            size="sm"
                            variant="ghost"
                            className="ml-auto"
                            loading={remove.isPending}
                            onClick={() => remove.mutate(event.id)}
                          >
                            Remove
                          </Button>
                        )}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </QueryBoundary>

        {error && (
          <p role="alert" className="border-t border-border p-4 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>
    </div>
  )
}

function CalendarForm({
  yearId,
  onCreated,
  onError,
}: {
  yearId: string
  onCreated: () => void
  onError: (message: string | null) => void
}) {
  const [error, setError] = useState<string | null>(null)

  const semesters = useQuery({
    queryKey: ['semesters', 'calendar'],
    queryFn: () => api<Semester[]>('/api/v1/academic/semesters', { query: { academicYearId: yearId } }),
  })

  const create = useMutation({
    mutationFn: (values: {
      title: string
      eventType: string
      academicYearId: string
      semesterId?: string
      startDate: string
      endDate: string
      workingDay: boolean
      description?: string
    }) => post<CalendarEvent>('/api/v1/academic/calendar', values),
    onSuccess: () => {
      setError(null)
      onError(null)
      onCreated()
    },
    onError: (err) => {
      setError(describeError(err))
      onError(null)
    },
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      title: value('title') ?? '',
      eventType: value('eventType') ?? 'EVENT',
      academicYearId: yearId,
      semesterId: value('semesterId'),
      startDate: value('startDate') ?? '',
      endDate: value('endDate') ?? '',
      workingDay: form.get('workingDay') === 'on',
      description: value('description'),
    })
  }

  return (
    <Panel title="Add an event">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="Title" htmlFor="calendar-title" required>
          <TextInput id="calendar-title" name="title" placeholder="First term" required />
        </Field>
        <Field label="Type" htmlFor="calendar-type" required>
          <Select id="calendar-type" name="eventType" defaultValue="EVENT">
            {EVENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Semester" htmlFor="calendar-semester">
          <Select id="calendar-semester" name="semesterId" defaultValue="">
            <option value="">Whole year</option>
            {(semesters.data ?? []).map((semester) => (
              <option key={semester.id} value={semester.id}>
                {semester.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="From" htmlFor="calendar-start" required>
          <TextInput id="calendar-start" name="startDate" type="date" required />
        </Field>
        <Field label="To" htmlFor="calendar-end" required>
          <TextInput id="calendar-end" name="endDate" type="date" required />
        </Field>
        <Field label="Attendance" htmlFor="calendar-working">
          <label className="flex items-center gap-2 pt-6 text-sm text-ink">
            <input
              id="calendar-working"
              name="workingDay"
              type="checkbox"
              defaultChecked
              className="size-4"
            />
            School is open
          </label>
        </Field>
        <div className="sm:col-span-3">
          <Field label="Description" htmlFor="calendar-description">
            <TextArea id="calendar-description" name="description" rows={2} />
          </Field>
        </div>
        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-3">
          <Button type="submit" loading={create.isPending}>
            Add event
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function Th({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <th scope="col" className={`px-4 py-2 font-medium text-ink-subtle ${className}`}>
      {children}
    </th>
  )
}
