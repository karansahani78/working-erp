import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post, type PageResponse } from '../../lib/api'
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
import { PageHeader } from '../../components/DataTable'
import { date, num } from '../../lib/format'
import {
  COURSE_TYPES,
  type AcademicYear,
  type Campus,
  type Course,
  type CourseOffering,
  type Department,
  type Faculty,
  type Program,
  type Room,
  type SchoolClass,
  type Section,
  type Semester,
  type TimeSlot,
} from './types'

/**
 * The academic structure is a chain: years hold classes, classes hold sections, courses hang
 * off departments or programmes, and a course offering ties one of each to a teacher. Each tab
 * is scoped to the level above it so a form never asks for an impossible parent.
 */
type TabKey = 'years' | 'semesters' | 'classes' | 'sections' | 'courses' | 'offerings' | 'campuses' | 'rooms' | 'slots' | 'faculties' | 'departments' | 'programs'

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'years', label: 'Years' },
  { key: 'semesters', label: 'Semesters' },
  { key: 'classes', label: 'Classes' },
  { key: 'sections', label: 'Sections' },
  { key: 'courses', label: 'Courses' },
  { key: 'offerings', label: 'Offerings' },
  { key: 'campuses', label: 'Campuses' },
  { key: 'rooms', label: 'Rooms' },
  { key: 'slots', label: 'Time slots' },
  { key: 'faculties', label: 'Faculties' },
  { key: 'departments', label: 'Departments' },
  { key: 'programs', label: 'Programmes' },
]

export function AcademicStructurePage() {
  const [tab, setTab] = useState<TabKey>('years')

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Academic structure"
        description="The shape of the institution: years, classes, sections, courses and who teaches them."
      />

      <div className="flex flex-wrap gap-1 border-b border-border">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={[
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === item.key
                ? 'border-primary text-primary'
                : 'border-transparent text-ink-subtle hover:text-ink',
            ].join(' ')}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'years' && <Years />}
      {tab === 'semesters' && <Semesters />}
      {tab === 'classes' && <Classes />}
      {tab === 'sections' && <Sections />}
      {tab === 'courses' && <Courses />}
      {tab === 'offerings' && <Offerings />}
      {tab === 'campuses' && <Campuses />}
      {tab === 'rooms' && <Rooms />}
      {tab === 'slots' && <TimeSlots />}
      {tab === 'faculties' && <Faculties />}
      {tab === 'departments' && <Departments />}
      {tab === 'programs' && <Programs />}
    </div>
  )
}

/** A reusable create form: fields are declared, and the body is posted as JSON. */
function CreateForm({
  title,
  path,
  fields,
  onDone,
  submitLabel = 'Create',
  extraBody,
}: {
  title: string
  path: string | ((values: Record<string, string>) => string)
  /** Values the server needs but that no input produces, such as an `active` flag. */
  extraBody?: Record<string, unknown>
  fields: Array<{
    name: string
    label: string
    type?: 'text' | 'number' | 'date' | 'time' | 'select'
    options?: Array<{ value: string; label: string }>
    required?: boolean
    hint?: string
    span?: 2
  }>
  onDone?: () => void
  submitLabel?: string
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (values: Record<string, string>) => {
      const body: Record<string, unknown> = {}
      for (const [key, value] of Object.entries(values)) {
        if (value === '') continue
        body[key] = fields.find((field) => field.name === key)?.type === 'number' ? Number(value) : value
      }
      const target = typeof path === 'function' ? path(body as Record<string, string>) : path
      return post<unknown>(target, { ...body, ...extraBody })
    },
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries()
      onDone?.()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <Panel title={title}>
      <form
        className="grid gap-4 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          create.mutate(Object.fromEntries(data.entries()) as Record<string, string>)
        }}
      >
        {fields.map((field) => (
          <div key={field.name} className={field.span === 2 ? 'sm:col-span-2' : ''}>
            <Field label={field.label} hint={field.hint} required={field.required}>
              {field.type === 'select' ? (
                <Select name={field.name} defaultValue="">
                  <option value="">Not set</option>
                  {field.options?.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              ) : (
                <TextInput
                  name={field.name}
                  type={field.type ?? 'text'}
                  step={field.type === 'number' ? 'any' : undefined}
                  required={field.required}
                />
              )}
            </Field>
          </div>
        ))}

        {error && <p className="text-sm text-danger sm:col-span-2">{error}</p>}

        <div className="sm:col-span-2">
          <Button type="submit" loading={create.isPending}>
            {submitLabel}
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function useYears() {
  return useQuery({
    queryKey: ['academic-years'],
    queryFn: () => api<PageResponse<AcademicYear>>('/api/v1/academic/academic-years', { query: { size: 100 } }),
  })
}

function Years() {
  const { can } = useAuth()
  const years = useYears()
  const rows = years.data?.data ?? []

  return (
    <>
      <Panel title="Academic years" padded={false}>
        {years.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No academic years" description="A year holds the classes and sections for that period." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((year) => (
              <li key={year.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-ink">{year.name}</span>
                    {year.current && <Badge tone="success">Current</Badge>}
                    <StatusBadge status={year.status} />
                  </div>
                  <p className="nums mt-1 text-xs text-ink-subtle">
                    {date(year.startDate)} → {date(year.endDate)} · {year.calendar}
                  </p>
                </div>
                <span className="nums text-sm text-ink-muted">{year.code}</span>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add an academic year"
          path="/api/v1/academic/academic-years"
          fields={[
            { name: 'name', label: 'Name', required: true, hint: 'For example 2083' },
            { name: 'code', label: 'Code', required: true },
            { name: 'startDate', label: 'Starts', type: 'date', required: true },
            { name: 'endDate', label: 'Ends', type: 'date', required: true },
            {
              name: 'calendar',
              label: 'Calendar',
              type: 'select',
              required: true,
              options: [
                { value: 'AD', label: 'AD' },
                { value: 'BS', label: 'BS' },
              ],
            },
          ]}
        />
      )}
    </>
  )
}

function Semesters() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const years = useYears()
  const [yearId, setYearId] = useState('')
  const effectiveYear = yearId || years.data?.data.find((year) => year.current)?.id || ''

  const semesters = useQuery({
    queryKey: ['semesters', effectiveYear],
    queryFn: () => api<Semester[]>('/api/v1/academic/semesters', { query: { academicYearId: effectiveYear } }),
    enabled: Boolean(effectiveYear),
  })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'activate' | 'complete' }) =>
      post<unknown>(`/api/v1/academic/semesters/${id}/${action}`, {}),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['semesters'] }),
  })

  const rows = semesters.data ?? []

  return (
    <>
      <Panel
        title="Semesters and terms"
        description="Terms inside an academic year. Exactly one is active at a time."
        actions={
          <Select value={effectiveYear} onChange={(event) => setYearId(event.target.value)} aria-label="Year">
            <option value="">Choose a year</option>
            {years.data?.data.map((year) => (
              <option key={year.id} value={year.id}>
                {year.name}
              </option>
            ))}
          </Select>
        }
        padded={false}
      >
        {!effectiveYear ? (
          <EmptyState title="Choose an academic year" />
        ) : semesters.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No semesters" description="Add the terms that make up this academic year." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((semester) => (
              <li key={semester.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-ink">{semester.name}</span>
                    <StatusBadge status={semester.status ?? 'ACTIVE'} />
                  </div>
                  <p className="nums mt-1 text-xs text-ink-subtle">
                    {date(semester.startDate)} → {date(semester.endDate)}
                  </p>
                </div>
                {can('ACADEMIC_CREATE') && (
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" onClick={() => transition.mutate({ id: semester.id, action: 'activate' })}>
                      Activate
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => transition.mutate({ id: semester.id, action: 'complete' })}>
                      Complete
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && effectiveYear && (
        <CreateForm
          title="Add a semester"
          path="/api/v1/academic/semesters"
          fields={[
            { name: 'academicYearId', label: 'Academic year', required: true, type: 'select',
              options: years.data?.data.map((year) => ({ value: year.id, label: year.name })) },
            { name: 'name', label: 'Name', required: true },
            { name: 'ordinal', label: 'Order', type: 'number', required: true },
            {
              name: 'type',
              label: 'Type',
              type: 'select',
              options: ['SEMESTER', 'TERM', 'TRIMESTER', 'YEAR'].map((value) => ({ value, label: value })),
            },
            { name: 'startDate', label: 'Starts', type: 'date', required: true },
            { name: 'endDate', label: 'Ends', type: 'date', required: true },
          ]}
        />
      )}
    </>
  )
}

function Classes() {
  const { can } = useAuth()
  const years = useYears()
  const [yearId, setYearId] = useState('')
  const effectiveYear = yearId || years.data?.data.find((year) => year.current)?.id || ''

  const classes = useQuery({
    queryKey: ['classes', effectiveYear],
    queryFn: () =>
      api<PageResponse<SchoolClass>>('/api/v1/academic/classes', { query: { yearId: effectiveYear, size: 100 } }),
    enabled: Boolean(effectiveYear),
  })

  const rows = classes.data?.data ?? []

  return (
    <>
      <Panel
        title="Classes"
        description="Grades or years of study, inside one academic year."
        actions={
          <Select value={effectiveYear} onChange={(event) => setYearId(event.target.value)} aria-label="Academic year">
            <option value="">Choose a year</option>
            {years.data?.data.map((year) => (
              <option key={year.id} value={year.id}>
                {year.name}
              </option>
            ))}
          </Select>
        }
        padded={false}
      >
        {!effectiveYear ? (
          <EmptyState title="Choose an academic year" description="Classes belong to a year." />
        ) : classes.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No classes in this year" />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((item) => (
              <li key={item.id} className="flex items-center justify-between p-4">
                <div>
                  <span className="font-medium text-ink">{item.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{item.code}</span>
                </div>
                <div className="flex items-center gap-2">
                  {item.ordinal != null && <span className="nums text-xs text-ink-subtle">#{item.ordinal}</span>}
                  <Badge tone={item.active ? 'success' : 'neutral'}>{item.active ? 'Active' : 'Inactive'}</Badge>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && effectiveYear && (
        <CreateForm
          title="Add a class"
          path={(values) => `/api/v1/academic/classes?yearId=${values.yearId ?? effectiveYear}`}
          fields={[
            { name: 'yearId', label: 'Academic year', required: true, type: 'select',
              options: years.data?.data.map((year) => ({ value: year.id, label: year.name })) },
            { name: 'name', label: 'Name', required: true, hint: 'For example Grade 8' },
            { name: 'code', label: 'Code', required: true },
            { name: 'ordinal', label: 'Order', type: 'number' },
          ]}
          // A class has to be active before a student can be enrolled into it.
          extraBody={{ active: true }}
        />
      )}
    </>
  )
}

function Sections() {
  const { can } = useAuth()
  const years = useYears()
  const [yearId, setYearId] = useState('')
  const effectiveYear = yearId || years.data?.data.find((year) => year.current)?.id || ''

  const classes = useQuery({
    queryKey: ['classes', effectiveYear],
    queryFn: () =>
      api<PageResponse<SchoolClass>>('/api/v1/academic/classes', { query: { yearId: effectiveYear, size: 100 } }),
    enabled: Boolean(effectiveYear),
  })
  const [classId, setClassId] = useState('')
  const effectiveClass = classId || classes.data?.data[0]?.id || ''

  const sections = useQuery({
    queryKey: ['sections', effectiveClass],
    queryFn: () => api<Section[]>('/api/v1/academic/sections', { query: { classId: effectiveClass } }),
    enabled: Boolean(effectiveClass),
  })

  const rows = sections.data ?? []

  return (
    <>
      <Panel
        title="Sections"
        description="A stream within a class, for example 8A and 8B."
        actions={
          <div className="flex gap-2">
            <Select value={effectiveYear} onChange={(event) => setYearId(event.target.value)} aria-label="Year">
              <option value="">Year</option>
              {years.data?.data.map((year) => (
                <option key={year.id} value={year.id}>
                  {year.name}
                </option>
              ))}
            </Select>
            <Select value={effectiveClass} onChange={(event) => setClassId(event.target.value)} aria-label="Class">
              <option value="">Class</option>
              {classes.data?.data.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </Select>
          </div>
        }
        padded={false}
      >
        {!effectiveClass ? (
          <EmptyState title="Choose a class" description="Sections belong to a class." />
        ) : sections.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No sections" />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((item) => (
              <li key={item.id} className="flex items-center justify-between p-4">
                <div>
                  <span className="font-medium text-ink">{item.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{item.code}</span>
                </div>
                <div className="flex items-center gap-2">
                  {item.capacity != null && (
                    <span className="nums text-xs text-ink-subtle">Capacity {item.capacity}</span>
                  )}
                  <Badge tone={item.active ? 'success' : 'neutral'}>{item.active ? 'Active' : 'Inactive'}</Badge>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && effectiveClass && (
        <CreateForm
          title="Add a section"
          path={(values) => `/api/v1/academic/sections?classId=${values.classId ?? effectiveClass}`}
          fields={[
            { name: 'classId', label: 'Class', required: true, type: 'select',
              options: classes.data?.data.map((item) => ({ value: item.id, label: item.name })) },
            { name: 'name', label: 'Name', required: true },
            { name: 'code', label: 'Code', required: true },
            { name: 'capacity', label: 'Capacity', type: 'number' },
          ]}
        />
      )}
    </>
  )
}

function Courses() {
  const { can } = useAuth()
  const [term, setTerm] = useState('')
  const courses = useQuery({
    queryKey: ['courses', term],
    queryFn: () => api<PageResponse<Course>>('/api/v1/academic/courses', { query: { term, size: 100 } }),
  })

  const rows = courses.data?.data ?? []

  return (
    <>
      <Panel
        title="Courses"
        description="What is taught, independent of who teaches it or when."
        padded={false}
        actions={
          <form
            className="flex gap-2"
            role="search"
            onSubmit={(event) => {
              event.preventDefault()
              setTerm((new FormData(event.currentTarget).get('term') as string) ?? '')
            }}
          >
            <TextInput name="term" placeholder="Search by code or name" aria-label="Search courses" />
            <Button type="submit" variant="secondary">
              Search
            </Button>
          </form>
        }
      >
        {courses.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No courses" description="Add the subjects this institution teaches." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Code</Th>
                  <Th>Name</Th>
                  <Th hideBelow="md">Type</Th>
                  <Th align="right" hideBelow="lg">Credits</Th>
                  <Th>State</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((course) => (
                  <tr key={course.id} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">{course.code}</td>
                    <td className="px-4 py-3 font-medium text-ink">{course.name}</td>
                    <td className="hidden md:table-cell px-4 py-3 text-ink-muted">{course.courseType}</td>
                    <td className="nums hidden lg:table-cell px-4 py-3 text-right text-ink-muted">
                      {num(course.creditHours)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={course.active ? 'success' : 'neutral'}>
                        {course.active ? 'Active' : 'Retired'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a course"
          path="/api/v1/academic/courses"
          extraBody={{ active: true }}
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            {
              name: 'courseType',
              label: 'Type',
              type: 'select',
              options: COURSE_TYPES.map((value) => ({ value, label: value })),
            },
            { name: 'creditHours', label: 'Credit hours', type: 'number' },
            { name: 'description', label: 'Description', span: 2 },
          ]}
        />
      )}
    </>
  )
}

function Offerings() {
  const years = useYears()
  const [yearId, setYearId] = useState('')
  const effectiveYear = yearId || years.data?.data.find((year) => year.current)?.id || ''

  const offerings = useQuery({
    queryKey: ['offerings', effectiveYear],
    queryFn: () =>
      api<PageResponse<CourseOffering>>('/api/v1/academic/offerings', { query: { yearId: effectiveYear, size: 100 } }),
    enabled: Boolean(effectiveYear),
  })

  const rows = offerings.data?.data ?? []

  return (
    <Panel
      title="Course offerings"
      description="A course, a class and a teacher together for a term. Attendance and marks hang off this."
      actions={
        <Select value={effectiveYear} onChange={(event) => setYearId(event.target.value)} aria-label="Year">
          <option value="">Choose a year</option>
          {years.data?.data.map((year) => (
            <option key={year.id} value={year.id}>
              {year.name}
            </option>
          ))}
        </Select>
      }
      padded={false}
    >
      {!effectiveYear ? (
        <EmptyState title="Choose an academic year" />
      ) : offerings.isLoading ? (
        <p className="p-5 text-sm text-ink-subtle">Loading…</p>
      ) : rows.length === 0 ? (
        <EmptyState
          title="No offerings yet"
          description="An offering is what connects a course, a class and a teacher for a term."
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <Th>Course</Th>
                <Th hideBelow="md">Class</Th>
                <Th hideBelow="lg">Teacher</Th>
                <Th>Code</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((offering) => (
                <tr key={offering.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3">
                    <span className="block font-medium text-ink">{offering.courseName}</span>
                    <span className="nums block text-xs text-ink-subtle">{offering.courseCode}</span>
                  </td>
                  <td className="hidden md:table-cell px-4 py-3 text-ink-muted">
                    {offering.schoolClassName ?? offering.programName ?? '—'}
                    {offering.sectionName ? ` · ${offering.sectionName}` : ''}
                  </td>
                  <td className="hidden lg:table-cell px-4 py-3 text-ink-muted">
                    {offering.teacherName ?? 'Unassigned'}
                  </td>
                  <td className="nums px-4 py-3 text-ink-muted">{offering.offeringCode}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}

function Campuses() {
  const { can } = useAuth()
  const campuses = useQuery({
    queryKey: ['campuses'],
    queryFn: () => api<PageResponse<Campus>>('/api/v1/academic/campuses', { query: { size: 100 } }),
  })
  const rows = campuses.data?.data ?? []

  return (
    <>
      <Panel title="Campuses" padded={false}>
        {campuses.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No campuses" description="Add the physical sites of this institution." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((campus) => (
              <li key={campus.id} className="flex items-start justify-between gap-3 p-4">
                <div>
                  <span className="font-medium text-ink">{campus.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{campus.code}</span>
                  {campus.address && <p className="mt-1 text-sm text-ink-subtle">{campus.address}</p>}
                </div>
                <Badge tone={campus.active ? 'success' : 'neutral'}>{campus.active ? 'Active' : 'Closed'}</Badge>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a campus"
          path="/api/v1/academic/campuses"
          extraBody={{ active: true }}
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            { name: 'address', label: 'Address', span: 2 },
            { name: 'phone', label: 'Phone' },
          ]}
        />
      )}
    </>
  )
}

function Rooms() {
  const { can } = useAuth()
  const rooms = useQuery({
    queryKey: ['rooms'],
    queryFn: () => api<Room[]>('/api/v1/academic/rooms'),
  })
  const campuses = useQuery({
    queryKey: ['campuses'],
    queryFn: () => api<PageResponse<Campus>>('/api/v1/academic/campuses', { query: { size: 100 } }),
  })

  const rows = rooms.data ?? []

  return (
    <>
      <Panel title="Rooms" padded={false}>
        {rooms.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No rooms" description="Rooms are what the timetable schedules against." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((room) => (
              <li key={room.id} className="flex items-center justify-between gap-3 p-4">
                <div>
                  <span className="font-medium text-ink">{room.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{room.code}</span>
                  <p className="mt-0.5 text-xs text-ink-subtle">
                    {room.campusName ?? 'No campus'}
                    {room.capacity ? ` · seats ${room.capacity}` : ''}
                    {room.roomType ? ` · ${room.roomType}` : ''}
                  </p>
                </div>
                <Badge tone={room.active ? 'success' : 'neutral'}>{room.active ? 'In use' : 'Closed'}</Badge>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a room"
          path="/api/v1/academic/rooms"
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            { name: 'building', label: 'Building' },
            { name: 'capacity', label: 'Capacity', type: 'number' },
            {
              name: 'campusId',
              label: 'Campus',
              type: 'select',
              options: campuses.data?.data.map((campus) => ({ value: campus.id, label: campus.name })),
            },
          ]}
        />
      )}
    </>
  )
}

function TimeSlots() {
  const { can } = useAuth()
  const slots = useQuery({
    queryKey: ['time-slots'],
    queryFn: () => api<TimeSlot[]>('/api/v1/academic/time-slots'),
  })
  const rows = slots.data ?? []

  return (
    <>
      <Panel title="Time slots" description="The periods of the day that a timetable fills." padded={false}>
        {slots.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No time slots" description="Define the periods of a teaching day first." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((slot) => (
              <li key={slot.id} className="flex items-center justify-between gap-3 p-4">
                <div>
                  <span className="font-medium text-ink">{slot.name}</span>
                  <p className="nums mt-0.5 text-xs text-ink-subtle">
                    {slot.startTime?.slice(0, 5)}–{slot.endTime?.slice(0, 5)} · {slot.slotType}
                  </p>
                </div>
                <Badge tone={slot.active ? 'success' : 'neutral'}>#{slot.ordinal}</Badge>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a time slot"
          path="/api/v1/academic/time-slots"
          fields={[
            { name: 'name', label: 'Name', required: true, hint: 'For example Period 1' },
            { name: 'startTime', label: 'Starts', type: 'time', required: true },
            { name: 'endTime', label: 'Ends', type: 'time', required: true },
            { name: 'ordinal', label: 'Order', type: 'number', required: true },
            {
              name: 'slotType',
              label: 'Slot type',
              type: 'select',
              options: [
                { value: 'LECTURE', label: 'Lecture' },
                { value: 'LAB', label: 'Lab' },
                { value: 'TUTORIAL', label: 'Tutorial' },
                { value: 'BREAK', label: 'Break' },
                { value: 'ASSEMBLY', label: 'Assembly' },
                { value: 'EXAM', label: 'Exam' },
              ],
            },
          ]}
          extraBody={{ active: true }}
        />
      )}
    </>
  )
}

function Faculties() {
  const { can } = useAuth()
  const faculties = useQuery({
    queryKey: ['faculties'],
    queryFn: () => api<PageResponse<Faculty>>('/api/v1/academic/faculties', { query: { size: 100 } }),
  })
  const rows = faculties.data?.data ?? []

  return (
    <>
      <Panel title="Faculties" padded={false}>
        {faculties.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No faculties" description="Only relevant to college and university models." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((faculty) => (
              <li key={faculty.id} className="flex items-center justify-between p-4">
                <div>
                  <span className="font-medium text-ink">{faculty.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{faculty.code}</span>
                </div>
                <Badge tone={faculty.active ? 'success' : 'neutral'}>
                  {faculty.active ? 'Active' : 'Closed'}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a faculty"
          path="/api/v1/academic/faculties"
          extraBody={{ active: true }}
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            { name: 'description', label: 'Description', span: 2 },
          ]}
        />
      )}
    </>
  )
}

function Departments() {
  const { can } = useAuth()
  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<PageResponse<Department>>('/api/v1/academic/departments', { query: { size: 100 } }),
  })
  const faculties = useQuery({
    queryKey: ['faculties'],
    queryFn: () => api<PageResponse<Faculty>>('/api/v1/academic/faculties', { query: { size: 100 } }),
  })
  const rows = departments.data?.data ?? []

  return (
    <>
      <Panel title="Departments" padded={false}>
        {departments.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No departments" />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((department) => (
              <li key={department.id} className="flex items-center justify-between p-4">
                <div>
                  <span className="font-medium text-ink">{department.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{department.code}</span>
                </div>
                <Badge tone={department.active ? 'success' : 'neutral'}>
                  {department.active ? 'Active' : 'Closed'}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a department"
          path="/api/v1/academic/departments"
          extraBody={{ active: true }}
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            {
              name: 'facultyId',
              label: 'Faculty',
              type: 'select',
              options: faculties.data?.data.map((faculty) => ({ value: faculty.id, label: faculty.name })),
            },
            { name: 'description', label: 'Description' },
          ]}
        />
      )}
    </>
  )
}

function Programs() {
  const { can } = useAuth()
  const programs = useQuery({
    queryKey: ['programs'],
    queryFn: () => api<Program[]>('/api/v1/academic/programs'),
  })
  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: () => api<PageResponse<Department>>('/api/v1/academic/departments', { query: { size: 100 } }),
  })
  const rows = programs.data ?? []

  return (
    <>
      <Panel title="Programmes" padded={false}>
        {programs.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : rows.length === 0 ? (
          <EmptyState title="No programmes" description="A programme is a degree or course of study." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((program) => (
              <li key={program.id} className="flex items-center justify-between p-4">
                <div>
                  <span className="font-medium text-ink">{program.name}</span>
                  <span className="nums ml-2 text-xs text-ink-subtle">{program.code}</span>
                  {program.departmentName && (
                    <p className="mt-0.5 text-xs text-ink-subtle">{program.departmentName}</p>
                  )}
                </div>
                {program.level && <Badge>{program.level}</Badge>}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {can('ACADEMIC_CREATE') && (
        <CreateForm
          title="Add a programme"
          path="/api/v1/academic/programs"
          fields={[
            { name: 'code', label: 'Code', required: true },
            { name: 'name', label: 'Name', required: true },
            { name: 'level', label: 'Level', hint: 'For example Undergraduate' },
            {
              name: 'departmentId',
              label: 'Department',
              type: 'select',
              options: departments.data?.data.map((item) => ({ value: item.id, label: item.name })),
            },
          ]}
        />
      )}
    </>
  )
}

function Th({
  children,
  align,
  hideBelow,
}: {
  children: ReactNode
  align?: 'right'
  hideBelow?: 'md' | 'lg'
}) {
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

