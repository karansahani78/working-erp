import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post, put, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextArea,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager, type PageInfo } from '../../components/DataTable'
import { date, money, num, text } from '../../lib/format'
import {
  ATTENDANCE_STATUSES,
  EMPLOYEE_STATUSES,
  EMPLOYMENT_TYPES,
  GENDERS,
  LEAVE_DECISIONS,
  type ApplyLeave,
  type AttendanceEntry,
  type CreateEmployee,
  type CreateLeaveType,
  type Designation,
  type Employee,
  type EmploymentType,
  type EmployeeProfile,
  type LeaveBalance,
  type LeaveType,
  type LeaveRequest,
  type SalaryStructure,
} from './types'

const PAGE_SIZE = 20

/**
 * The HR endpoints return whole lists rather than pages, so the browser does the
 * filtering and paging. This keeps one table shape for both cases.
 */
function clientPage<T>(items: T[], needle: string, matches: (item: T, needle: string) => boolean, page: number) {
  const term = needle.trim().toLowerCase()
  const filtered = term === '' ? items : items.filter((item) => matches(item, term))
  const totalElements = filtered.length
  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE))
  const current = Math.min(page, totalPages - 1)
  const rows = filtered.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE)
  const info: PageInfo = {
    page: current,
    totalPages,
    totalElements,
    first: current === 0,
    last: current >= totalPages - 1,
  }
  return { rows, info }
}

/** Leave has no search field, so it pages the filtered list as it arrives. */
function leavePage(requests: LeaveRequest[], page: number) {
  return clientPage(requests, '', () => true, page)
}

/** Matches a person on any field a staff member would realistically search by. */
function matchesEmployee(employee: Employee, needle: string) {
  const fields = [
    employee.fullName,
    employee.employeeCode,
    employee.email,
    employee.phone,
    employee.designationName,
    employee.departmentName,
  ]
  return fields.some((field) => field?.toLowerCase().includes(needle))
}

/* ----------------------------------------------------------------- employees */

export function EmployeesPage() {
  const { can } = useAuth()
  const [term, setTerm] = useState('')
  const [appliedTerm, setAppliedTerm] = useState('')
  const [status, setStatus] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [page, setPage] = useState(0)

  const applyTerm = () => {
    setAppliedTerm(term.trim())
    setPage(0)
  }

  const designations = useQuery({
    queryKey: ['hr-designations'],
    queryFn: () => api<Designation[]>('/api/v1/hr/designations'),
    enabled: can('EMPLOYEE_CREATE'),
  })

  const structures = useQuery({
    queryKey: ['hr-payroll-salary-structures'],
    queryFn: () => api<SalaryStructure[]>('/api/v1/hr/payroll/salary-structures'),
    enabled: can('EMPLOYEE_CREATE'),
  })

  const employees = useQuery({
    queryKey: ['hr-employees', status, departmentId],
    queryFn: () =>
      api<Employee[]>('/api/v1/hr/employees', {
        query: {
          status: status || undefined,
          departmentId: departmentId || undefined,
        },
      }),
  })

  const departments = useQuery({
    queryKey: ['academic-departments'],
    queryFn: () => api<PageResponse<{ id: string; name: string; code: string }>>('/api/v1/academic/departments', { query: { size: 100 } }),
    enabled: can('EMPLOYEE_READ'),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Employees"
        description="Staff records, contracts and the documents behind them."
      />

      {can('EMPLOYEE_CREATE') && (
        <EmployeeForm
          departments={departments.data?.data ?? []}
          designations={designations.data ?? []}
          structures={structures.data ?? []}
          onCreated={() => void employees.refetch()}
        />
      )}

      <Panel padded={false}>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-border p-4"
          onSubmit={(event) => {
            event.preventDefault()
            applyTerm()
          }}
          role="search"
        >
          <div className="min-w-56 flex-1">
            <Field label="Search" htmlFor="employee-search">
              <TextInput
                id="employee-search"
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Name, code, email or phone"
              />
            </Field>
          </div>
          <div className="w-44">
            <Field label="Department" htmlFor="employee-department">
              <Select
                id="employee-department"
                value={departmentId}
                onChange={(event) => {
                  setDepartmentId(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All departments</option>
                {(departments.data?.data ?? []).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="w-40">
            <Field label="Status" htmlFor="employee-status">
              <Select
                id="employee-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All statuses</option>
                {EMPLOYEE_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {text(value)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
          {(appliedTerm || status || departmentId) && (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setTerm('')
                setAppliedTerm('')
                setStatus('')
                setDepartmentId('')
                setPage(0)
              }}
            >
              Clear
            </Button>
          )}
        </form>

        <QueryBoundary
          isLoading={employees.isLoading}
          error={employees.error}
          data={employees.data}
          onRetry={() => void employees.refetch()}
          loadingRows={8}
          empty={
            <EmptyState
              title={appliedTerm || status || departmentId ? 'No employees match' : 'No staff yet'}
              description={
                appliedTerm || status || departmentId
                  ? 'Try a different search, or clear the filters.'
                  : 'Employees appear here once a staff record is created.'
              }
            />
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Code</Th>
                      <Th>Name</Th>
                      <Th hideBelow="md">Designation</Th>
                      <Th hideBelow="lg">Department</Th>
                      <Th>Joined</Th>
                      <Th>Status</Th>
                      <Th className="text-right">Open</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {clientPage(data, appliedTerm, matchesEmployee, page).rows.map((employee) => (
                      <tr key={employee.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">{employee.employeeCode}</td>
                        <td className="px-4 py-3">
                          <Link
                            to={`/employees/${employee.id}`}
                            className="font-medium text-primary hover:underline"
                          >
                            {employee.fullName}
                          </Link>
                        </td>
                        <td className="px-4 py-3 text-ink-muted">{employee.designationName ?? '—'}</td>
                        <td className="px-4 py-3 text-ink-muted">{employee.departmentName ?? '—'}</td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(employee.joinDate)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={employee.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Link
                            to={`/employees/${employee.id}`}
                            className="text-sm font-medium text-primary hover:underline"
                          >
                            View
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={clientPage(data, appliedTerm, matchesEmployee, page).info} onChange={setPage} />
            </>
          )}
        </QueryBoundary>
      </Panel>
    </div>
  )
}

/**
 * Creates a staff record. Only the employee code and join date are mandatory; the rest
 * can be filled in later, so a new starter can be recorded before their paperwork lands.
 */
function EmployeeForm({
  departments,
  designations,
  structures,
  onCreated,
}: {
  departments: { id: string; name: string }[]
  designations: Designation[]
  structures: SalaryStructure[]
  onCreated: () => void
}) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const today = new Date().toISOString().slice(0, 10)

  const create = useMutation({
    mutationFn: (values: CreateEmployee) => post<Employee>('/api/v1/hr/employees', values),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      onCreated()
    },
    onError: (err) => setError(describeError(err)),
  })

  if (!open) {
    return (
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)}>Add employee</Button>
      </div>
    )
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined

    create.mutate({
      employeeCode: value('employeeCode') ?? '',
      firstName: value('firstName') ?? '',
      middleName: value('middleName'),
      lastName: value('lastName'),
      dateOfBirth: value('dateOfBirth'),
      gender: value('gender'),
      nationality: value('nationality'),
      phone: value('phone'),
      email: value('email'),
      address: value('address'),
      photoUrl: value('photoUrl'),
      employmentType: value('employmentType') as EmploymentType | undefined,
      joinDate: value('joinDate') ?? today,
      departmentId: value('departmentId'),
      designationId: value('designationId'),
      salaryStructureId: value('salaryStructureId'),
      bankName: value('bankName'),
      bankAccount: value('bankAccount'),
      taxNumber: value('taxNumber'),
    })
  }

  return (
    <Panel title="Add an employee" description="Records the person; payroll picks them up once a salary structure is attached.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="Employee code" htmlFor="new-employee-code" required>
          <TextInput id="new-employee-code" name="employeeCode" placeholder="EMP-0001" required />
        </Field>
        <Field label="First name" htmlFor="new-employee-first" required>
          <TextInput id="new-employee-first" name="firstName" required />
        </Field>
        <Field label="Last name" htmlFor="new-employee-last">
          <TextInput id="new-employee-last" name="lastName" />
        </Field>
        <Field label="Middle name" htmlFor="new-employee-middle">
          <TextInput id="new-employee-middle" name="middleName" />
        </Field>
        <Field label="Date of birth" htmlFor="new-employee-dob">
          <TextInput id="new-employee-dob" name="dateOfBirth" type="date" />
        </Field>
        <Field label="Gender" htmlFor="new-employee-gender">
          <Select id="new-employee-gender" name="gender" defaultValue="">
            <option value="">Not stated</option>
            {GENDERS.map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Employment type" htmlFor="new-employee-type">
          <Select id="new-employee-type" name="employmentType" defaultValue="FULL_TIME">
            {EMPLOYMENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Join date" htmlFor="new-employee-join" required>
          <TextInput id="new-employee-join" name="joinDate" type="date" defaultValue={today} required />
        </Field>
        <Field label="Phone" htmlFor="new-employee-phone">
          <TextInput id="new-employee-phone" name="phone" />
        </Field>
        <Field label="Email" htmlFor="new-employee-email">
          <TextInput id="new-employee-email" name="email" type="email" />
        </Field>
        <Field label="Department" htmlFor="new-employee-department">
          <Select id="new-employee-department" name="departmentId" defaultValue="">
            <option value="">Not assigned</option>
            {departments.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Designation" htmlFor="new-employee-designation">
          <Select id="new-employee-designation" name="designationId" defaultValue="">
            <option value="">Not assigned</option>
            {designations.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Salary structure" htmlFor="new-employee-structure">
          <Select id="new-employee-structure" name="salaryStructureId" defaultValue="">
            <option value="">Not assigned</option>
            {structures.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Nationality" htmlFor="new-employee-nationality">
          <TextInput id="new-employee-nationality" name="nationality" />
        </Field>
        <Field label="Bank name" htmlFor="new-employee-bank">
          <TextInput id="new-employee-bank" name="bankName" />
        </Field>
        <Field label="Bank account" htmlFor="new-employee-account">
          <TextInput id="new-employee-account" name="bankAccount" />
        </Field>
        <Field label="Tax number" htmlFor="new-employee-tax">
          <TextInput id="new-employee-tax" name="taxNumber" />
        </Field>
        <div className="sm:col-span-3">
          <Field label="Address" htmlFor="new-employee-address">
            <TextArea id="new-employee-address" name="address" />
          </Field>
        </div>

        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}

        <div className="flex gap-2 sm:col-span-3">
          <Button type="submit" loading={create.isPending}>
            Create employee
          </Button>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

export function EmployeeDetailPage({ id }: { id: string }) {
  const { can } = useAuth()
  const profile = useQuery({
    queryKey: ['hr-employee-profile', id],
    queryFn: () => api<EmployeeProfile>(`/api/v1/hr/employees/${id}/profile`),
    enabled: Boolean(id),
  })

  if (profile.isLoading) return <LoadingState label="Loading employee…" />
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />
  if (!profile.data) return null

  const { employee, employments, qualifications, documents } = profile.data
  const maskedAccount = employee.bankAccount
    ? `••••${employee.bankAccount.slice(-4)}`
    : null

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/employees" className="text-sm text-primary hover:underline">
            Employees
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-ink">{employee.fullName}</h1>
          <p className="nums mt-1 text-sm text-ink-subtle">
            {employee.employeeCode}
            {employee.designationName ? ` · ${employee.designationName}` : ''}
          </p>
        </div>
        <StatusBadge status={employee.status} />
      </header>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Fact label="Employment" value={text(employee.employmentType)} />
        <Fact label="Joined" value={date(employee.joinDate)} />
        <Fact label="Department" value={employee.departmentName ?? '—'} />
        <Fact label="Salary structure" value={employee.salaryStructureName ?? '—'} />
        <Fact label="Email" value={employee.email ?? '—'} />
        <Fact label="Phone" value={employee.phone ?? '—'} />
        <Fact label="Nationality" value={employee.nationality ?? '—'} />
        <Fact
          label="Bank"
          value={employee.bankName ? `${employee.bankName} ${maskedAccount ?? ''}` : '—'}
        />
      </div>

      {can('EMPLOYEE_UPDATE') && <EmployeeActions employee={employee} />}

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel title="Employment history" padded={false}>
          {(employments ?? []).length === 0 ? (
            <EmptyState title="No history" description="Only the current engagement is recorded." />
          ) : (
            <ul className="divide-y divide-border">
              {(employments ?? []).map((item: import('./types').Employment) => (
                <li key={item.id} className="p-4">
                  <p className="font-medium text-ink">{item.designationName ?? 'Undesignated'}</p>
                  <p className="nums mt-0.5 text-xs text-ink-subtle">
                    {text(item.employmentType)} · {date(item.startDate)}
                    {item.endDate ? ` to ${date(item.endDate)}` : ' — current'}
                  </p>
                  {item.notes && <p className="mt-1 text-sm text-ink-muted">{item.notes}</p>}
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title="Qualifications" padded={false}>
          {(qualifications ?? []).length === 0 ? (
            <EmptyState title="None recorded" description="Awards and certifications appear here." />
          ) : (
            <ul className="divide-y divide-border">
              {(qualifications ?? []).map((item: import('./types').EmployeeQualification) => (
                <li key={item.id} className="p-4">
                  <p className="font-medium text-ink">{item.qualificationName}</p>
                  <p className="mt-0.5 text-xs text-ink-subtle">
                    {item.institution ?? '—'}
                    {item.awardedOn ? ` · ${date(item.awardedOn)}` : ''}
                    {item.expiresOn ? ` · expires ${date(item.expiresOn)}` : ''}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      <Panel title="Documents" padded={false} description="Employment documents held against this record">
        {(documents ?? []).length === 0 ? (
          <EmptyState title="No documents" description="Nothing has been filed against this employee." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Type</Th>
                  <Th>File</Th>
                  <Th>Status</Th>
                  <Th>Reviewed</Th>
                </tr>
              </thead>
              <tbody>
                {(documents ?? []).map((item: import('./types').EmployeeDocument) => (
                  <tr key={item.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3">{text(item.documentType)}</td>
                    <td className="px-4 py-3 text-ink-muted">{item.fileName}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={item.status} />
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-ink-subtle">
                      {date(item.reviewedAt?.slice(0, 10) ?? null)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}

function EmployeeActions({ employee }: { employee: Employee }) {
  const queryClient = useQueryClient()
  const [exitDate, setExitDate] = useState(employee.exitDate ?? '')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)

  const terminate = useMutation({
    mutationFn: () =>
      post<void>(`/api/v1/hr/employees/${employee.id}/terminate`, {
        exitDate,
        status: 'TERMINATED',
        reason: reason || undefined,
      }),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['hr-employee-profile', employee.id] })
      void queryClient.invalidateQueries({ queryKey: ['hr-employees'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  if (employee.status === 'TERMINATED' || employee.status === 'RETIRED') return null

  if (!open) {
    return (
      <Panel title="Employment">
        <Button variant="secondary" onClick={() => setOpen(true)}>
          Record an exit
        </Button>
      </Panel>
    )
  }

  return (
    <Panel title="Record an exit" description="Leaving is a status change with its own rules, not an edit.">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Exit date" htmlFor="exit-date" required>
          <TextInput
            id="exit-date"
            type="date"
            value={exitDate}
            onChange={(event) => setExitDate(event.target.value)}
          />
        </Field>
        <Field label="Reason" htmlFor="exit-reason">
          <TextInput
            id="exit-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Resignation, retirement, redundancy…"
          />
        </Field>
      </div>
      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
      <div className="mt-4 flex gap-2">
        <Button
          loading={terminate.isPending}
          disabled={!exitDate}
          onClick={() => terminate.mutate()}
        >
          Record exit
        </Button>
        <Button variant="ghost" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </Panel>
  )
}

/* --------------------------------------------------------------------- leave */

export function LeavePage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const employees = useQuery({
    queryKey: ['hr-leave-requests-employees'],
    queryFn: () => api<Employee[]>('/api/v1/hr/employees', { query: { status: 'ACTIVE' } }),
    enabled: can('LEAVE_CREATE'),
  })

  const types = useQuery({
    queryKey: ['hr-leave-types'],
    queryFn: () => api<import('./types').LeaveType[]>('/api/v1/hr/leave/types'),
    enabled: can('LEAVE_CREATE'),
  })

  const requests = useQuery({
    queryKey: ['hr-leave-requests', status],
    queryFn: () =>
      api<LeaveRequest[]>('/api/v1/hr/leave/requests', {
        query: { status: status || undefined },
      }),
  })

  const decide = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: string }) =>
      put<void>(`/api/v1/hr/leave/requests/${id}/decision`, { status: decision }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['hr-leave-requests'] })
      void queryClient.invalidateQueries({ queryKey: ['hr-leave-balances'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader title="Leave" description="Requests, balances and the decisions behind them." />

      {can('LEAVE_APPLY') && <LeaveApplyForm employees={employees.data ?? []} types={types.data ?? []} />}
      {can('EMPLOYEE_CREATE') && <LeaveTypeForm />}

      <Panel padded={false}>
        <form className="flex flex-wrap items-end gap-3 border-b border-border p-4" onSubmit={(e) => e.preventDefault()}>
          <div className="w-44">
            <Field label="Status" htmlFor="leave-status">
              <Select
                id="leave-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All requests</option>
                {['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((value) => (
                  <option key={value} value={value}>
                    {text(value)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </form>

        <QueryBoundary
          isLoading={requests.isLoading}
          error={requests.error}
          data={requests.data}
          onRetry={() => void requests.refetch()}
          loadingRows={8}
          empty={
            <EmptyState
              title="No leave requests"
              description="Requests appear here once someone applies for leave."
            />
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Employee</Th>
                      <Th>Type</Th>
                      <Th>Dates</Th>
                      <Th className="text-right">Days</Th>
                      <Th>Status</Th>
                      {can('LEAVE_APPROVE') && <Th className="text-right">Decide</Th>}
                    </tr>
                  </thead>
                  <tbody>
                    {leavePage(data, page).rows.map((request) => (
                      <tr key={request.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="px-4 py-3">
                          <span className="font-medium text-ink">{request.employeeName}</span>
                          <span className="nums block text-xs text-ink-subtle">{request.employeeCode}</span>
                        </td>
                        <td className="px-4 py-3 text-ink-muted">{request.leaveTypeName}</td>
                        <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">
                          {date(request.startDate)} – {date(request.endDate)}
                        </td>
                        <td className="nums px-4 py-3 text-right">{num(request.days)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={request.status} />
                        </td>
                        {can('LEAVE_APPROVE') && (
                          <td className="px-4 py-3 text-right">
                            {request.status === 'PENDING' ? (
                              <span className="inline-flex gap-1">
                                {LEAVE_DECISIONS.map((decision) => (
                                  <Button
                                    key={decision}
                                    size="sm"
                                    variant={decision === 'APPROVED' ? 'primary' : 'ghost'}
                                    loading={decide.isPending}
                                    onClick={() =>
                                      decide.mutate({ id: request.id, decision })
                                    }
                                  >
                                    {decision === 'APPROVED' ? 'Approve' : 'Reject'}
                                  </Button>
                                ))}
                              </span>
                            ) : (
                              <span className="text-xs text-ink-subtle">{date(request.decidedAt?.slice(0, 10) ?? null)}</span>
                            )}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={leavePage(data, page).info} onChange={setPage} />
            </>
          )}
        </QueryBoundary>
        {error && <p className="border-t border-border p-4 text-sm text-danger">{error}</p>}
      </Panel>
    </div>
  )
}

/** Records a leave request against a named employee. */
function LeaveApplyForm({ employees, types }: { employees: Employee[]; types: LeaveType[] }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const apply = useMutation({
    mutationFn: (values: ApplyLeave) => post<LeaveRequest>('/api/v1/hr/leave/requests', values),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['hr-leave-requests'] })
      void queryClient.invalidateQueries({ queryKey: ['hr-leave-balances'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  if (!open) {
    return (
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)} disabled={employees.length === 0 || types.length === 0}>
          Record leave
        </Button>
      </div>
    )
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    apply.mutate({
      employeeId: value('employeeId'),
      leaveTypeId: value('leaveTypeId') ?? '',
      startDate: value('startDate') ?? '',
      endDate: value('endDate') ?? '',
      reason: value('reason'),
    })
  }

  return (
    <Panel title="Record leave" description="Approving a request draws down the employee's balance for that type.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <Field label="Employee" htmlFor="leave-employee" required>
          <Select id="leave-employee" name="employeeId" required defaultValue="">
            <option value="">Choose an employee</option>
            {employees.map((item) => (
              <option key={item.id} value={item.id}>
                {item.fullName} ({item.employeeCode})
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Leave type" htmlFor="leave-type" required>
          <Select id="leave-type" name="leaveTypeId" required defaultValue="">
            <option value="">Choose a type</option>
            {types.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="From" htmlFor="leave-start" required>
          <TextInput id="leave-start" name="startDate" type="date" required />
        </Field>
        <Field label="To" htmlFor="leave-end" required>
          <TextInput id="leave-end" name="endDate" type="date" required />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Reason" htmlFor="leave-reason">
            <TextArea id="leave-reason" name="reason" />
          </Field>
        </div>

        {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}

        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" loading={apply.isPending}>
            Submit request
          </Button>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/** Leave types are policy, so they are managed by the same permission as the requests. */
function LeaveTypeForm() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (values: CreateLeaveType) => post<LeaveType>('/api/v1/hr/leave/types', values),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['hr-leave-types'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    create.mutate({
      code: (form.get('code') as string).trim(),
      name: (form.get('name') as string).trim(),
      daysPerYear: Number(form.get('daysPerYear')) || 0,
      paid: form.get('paid') === 'on',
      description: (form.get('description') as string | null)?.trim() || undefined,
    })
  }

  return (
    <Panel title="Leave types" description="The kinds of leave an employee can be granted.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <Field label="Code" htmlFor="leave-type-code" required>
          <TextInput id="leave-type-code" name="code" placeholder="ANNUAL" required />
        </Field>
        <Field label="Name" htmlFor="leave-type-name" required>
          <TextInput id="leave-type-name" name="name" placeholder="Annual leave" required />
        </Field>
        <Field label="Days per year" htmlFor="leave-type-days">
          <TextInput id="leave-type-days" name="daysPerYear" type="number" min={0} step="0.5" defaultValue="0" />
        </Field>
        <Field label="Paid" htmlFor="leave-type-paid">
          <label className="flex items-center gap-2 pt-6 text-sm text-ink">
            <input id="leave-type-paid" name="paid" type="checkbox" defaultChecked className="size-4" />
            Paid leave
          </label>
        </Field>
        <div className="sm:col-span-4">
          <Field label="Description" htmlFor="leave-type-description">
            <TextInput id="leave-type-description" name="description" />
          </Field>
        </div>
        {error && <p className="sm:col-span-4 text-sm text-danger">{error}</p>}
        <div className="sm:col-span-4">
          <Button type="submit" loading={create.isPending}>
            Add leave type
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/* ------------------------------------------------------------------- payroll */

export function PayrollPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [showRun, setShowRun] = useState(false)

  const runs = useQuery({
    queryKey: ['hr-payroll-runs'],
    queryFn: () => api<import('./types').PayrollRun[]>('/api/v1/hr/payroll/runs', { query: { status: status || undefined } }),
  })

  const structures = useQuery({
    queryKey: ['hr-salary-structures'],
    queryFn: () => api<SalaryStructure[]>('/api/v1/hr/payroll/salary-structures'),
  })

  const approve = useMutation({
    mutationFn: (id: string) => post<void>(`/api/v1/hr/payroll/runs/${id}/approve`, {}),
    onSuccess: () => {
      setError(null)
      setNotice('Payroll run approved.')
      void queryClient.invalidateQueries({ queryKey: ['hr-payroll-runs'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Payroll"
        description="Salary structures and the monthly runs built from them."
        actions={
          <>
            {can('SALARY_MANAGE') && (
              <Button variant="secondary" onClick={() => setShowRun((open) => !open)}>
                {showRun ? 'Hide structures' : 'Salary structures'}
              </Button>
            )}
            {can('PAYROLL_PROCESS') && (
              <Button onClick={() => setShowRun((open) => !open)}>Process a run</Button>
            )}
          </>
        }
      />

      {error && <p className="text-sm text-danger">{error}</p>}
      {notice && <p className="text-sm text-primary">{notice}</p>}

      <Panel title="Payroll runs" padded={false}>
        {runs.isLoading ? (
          <LoadingState label="Loading runs…" />
        ) : runs.isError ? (
          <ErrorState error={runs.error} onRetry={() => void runs.refetch()} />
        ) : (runs.data ?? []).length === 0 ? (
          <EmptyState title="No payroll runs" description="A run is created by processing a month." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Period</Th>
                  <Th className="text-right">Employees</Th>
                  <Th className="text-right">Gross</Th>
                  <Th className="text-right">Deductions</Th>
                  <Th className="text-right">Net</Th>
                  <Th>Status</Th>
                  <Th className="text-right">Open</Th>
                </tr>
              </thead>
              <tbody>
                {(runs.data ?? []).map((run) => (
                  <tr key={run.id} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-3 whitespace-nowrap font-medium text-ink">{run.period}</td>
                    <td className="nums px-4 py-3 text-right">{num(run.employeeCount)}</td>
                    <td className="nums px-4 py-3 text-right">{money(run.totalGross)}</td>
                    <td className="nums px-4 py-3 text-right">{money(run.totalDeductions)}</td>
                    <td className="nums px-4 py-3 text-right font-medium">{money(run.totalNet)}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={run.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className="inline-flex items-center justify-end gap-2">
                        {run.status === 'PROCESSED' && can('PAYROLL_APPROVE') && (
                          <Button
                            size="sm"
                            loading={approve.isPending}
                            onClick={() => approve.mutate(run.id)}
                          >
                            Approve
                          </Button>
                        )}
                        <Link
                          to={`/payroll/${run.id}`}
                          className="text-sm font-medium text-primary hover:underline"
                        >
                          View
                        </Link>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {showRun && (
        <PayrollRunForm structures={structures.data ?? []} />
      )}
    </div>
  )
}

function PayrollRunForm({ structures }: { structures: SalaryStructure[] }) {
  const queryClient = useQueryClient()
  const [year, setYear] = useState(String(new Date().getFullYear()))
  const [month, setMonth] = useState(String(new Date().getMonth() + 1))
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  const now = new Date().toISOString().slice(0, 10)

  const process = useMutation({
    mutationFn: () =>
      post<import('./types').PayrollRun>('/api/v1/hr/payroll/runs', {
        periodYear: Number(year),
        periodMonth: Number(month),
        notes: notes || undefined,
      }),
    onSuccess: (run) => {
      setError(null)
      setNotes('')
      void queryClient.invalidateQueries({ queryKey: ['hr-payroll-runs'] })
      window.location.assign(`/payroll/${run.id}`)
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <Panel
      title="Process a payroll run"
      description={`Calculates payslips for every active employee. Today is ${date(now)}.`}
    >
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Year" htmlFor="payroll-year" required>
          <TextInput
            id="payroll-year"
            type="number"
            min={2000}
            max={2999}
            value={year}
            onChange={(event) => setYear(event.target.value)}
          />
        </Field>
        <Field label="Month" htmlFor="payroll-month" required>
          <Select id="payroll-month" value={month} onChange={(event) => setMonth(event.target.value)}>
            {Array.from({ length: 12 }, (_, index) => index + 1).map((value) => (
              <option key={value} value={value}>
                {new Date(2000, value - 1, 1).toLocaleString(undefined, { month: 'long' })}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Notes" htmlFor="payroll-notes">
          <TextInput
            id="payroll-notes"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="Optional"
          />
        </Field>
      </div>

      {structures.length > 0 && (
        <p className="mt-3 text-xs text-ink-subtle">
          {structures.length} salary structure{structures.length === 1 ? '' : 's'} are available; each employee is
          paid against the structure on their record.
        </p>
      )}

      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
      <div className="mt-4">
        <Button loading={process.isPending} onClick={() => process.mutate()}>
          Process run
        </Button>
      </div>
    </Panel>
  )
}

export function PayrollRunDetailPage({ id }: { id: string }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const run = useQuery({
    queryKey: ['hr-payroll-run', id],
    queryFn: () => api<import('./types').PayrollRun>(`/api/v1/hr/payroll/runs/${id}`),
    enabled: Boolean(id),
  })

  const slips = useQuery({
    queryKey: ['hr-payslips', id],
    queryFn: () => api<import('./types').Payslip[]>(`/api/v1/hr/payroll/runs/${id}/payslips`),
    enabled: Boolean(id),
  })

  const approve = useMutation({
    mutationFn: () => post<void>(`/api/v1/hr/payroll/runs/${id}/approve`, {}),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['hr-payroll-run', id] })
    },
    onError: (err) => setError(describeError(err)),
  })

  if (run.isLoading) return <LoadingState label="Loading payroll run…" />
  if (run.isError) return <ErrorState error={run.error} onRetry={() => void run.refetch()} />
  if (!run.data) return null

  const data = run.data

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/payroll" className="text-sm text-primary hover:underline">
            Payroll
          </Link>
          <h1 className="nums mt-1 text-2xl font-semibold text-ink">{data.period}</h1>
          <p className="mt-1 text-sm text-ink-subtle">
            {num(data.employeeCount)} employees · processed {date(data.processedAt?.slice(0, 10) ?? null)}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={data.status} />
          {data.status === 'PROCESSED' && can('PAYROLL_APPROVE') && (
            <Button loading={approve.isPending} onClick={() => approve.mutate()}>
              Approve run
            </Button>
          )}
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Fact label="Gross" value={money(data.totalGross)} />
        <Fact label="Deductions" value={money(data.totalDeductions)} />
        <Fact label="Tax" value={money(data.totalTax)} />
        <Fact label="Net payable" value={money(data.totalNet)} />
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <Panel title="Payslips" padded={false}>
        {slips.isLoading ? (
          <LoadingState label="Loading payslips…" />
        ) : slips.isError ? (
          <ErrorState error={slips.error} onRetry={() => void slips.refetch()} />
        ) : (slips.data ?? []).length === 0 ? (
          <EmptyState title="No payslips" description="This run produced no payslips." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Employee</Th>
                  <Th hideBelow="md">Designation</Th>
                  <Th className="text-right">Basic</Th>
                  <Th className="text-right">Gross</Th>
                  <Th className="text-right">Tax</Th>
                  <Th className="text-right">Net</Th>
                </tr>
              </thead>
              <tbody>
                {(slips.data ?? []).map((slip) => (
                  <tr key={slip.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3">
                      <span className="font-medium text-ink">{slip.employeeName}</span>
                      <span className="nums block text-xs text-ink-subtle">{slip.employeeCode}</span>
                    </td>
                    <td className="px-4 py-3 text-ink-muted">{slip.designation ?? '—'}</td>
                    <td className="nums px-4 py-3 text-right">{money(slip.basicSalary)}</td>
                    <td className="nums px-4 py-3 text-right">{money(slip.gross)}</td>
                    <td className="nums px-4 py-3 text-right">{money(slip.tax)}</td>
                    <td className="nums px-4 py-3 text-right font-medium">{money(slip.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}

/* ----------------------------------------------------------------- attendance */

export function EmployeeAttendancePage({ employeeId }: { employeeId?: string } = {}) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [date_, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [error, setError] = useState<string | null>(null)

  const attendance = useQuery({
    queryKey: ['hr-employee-attendance', employeeId, date_],
    queryFn: () =>
      api<import('./types').EmployeeAttendance[]>(`/api/v1/hr/attendance/employees/${employeeId}`, {
        query: { date: date_ },
      }),
    enabled: Boolean(employeeId),
  })

  if (!employeeId) {
    return <EmptyState title="No employee selected" description="Open an employee to mark their attendance." />
  }

  if (!can('ATTENDANCE_EMPLOYEE_MARK')) {
    return (
      <EmptyState
        title="Not permitted"
        description="Marking staff attendance needs the staff attendance permission."
      />
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Field label="Date" htmlFor="staff-attendance-date">
        <TextInput
          id="staff-attendance-date"
          type="date"
          value={date_}
          onChange={(event) => setDate(event.target.value)}
          className="w-48"
        />
      </Field>
      {error && <p className="text-sm text-danger">{error}</p>}
      {attendance.data && attendance.data.length > 0 && (
        <ul className="divide-y divide-border">
          {attendance.data.map((entry) => (
            <li key={entry.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
              <StatusBadge status={entry.status} />
              <span className="nums text-ink-muted">
                {entry.checkIn ? `in ${entry.checkIn}` : '—'}
                {entry.checkOut ? ` · out ${entry.checkOut}` : ''}
              </span>
              {entry.overtimeMinutes > 0 && (
                <Badge tone="warning">{num(entry.overtimeMinutes)} min overtime</Badge>
              )}
            </li>
          ))}
        </ul>
      )}
      <BulkMark
        date={date_}
        onDone={() => {
          setError(null)
          void queryClient.invalidateQueries({ queryKey: ['hr-employee-attendance', employeeId] })
        }}
        onError={setError}
      />
    </div>
  )
}

function BulkMark({
  date,
  onDone,
  onError,
}: {
  date: string
  onDone: () => void
  onError: (message: string) => void
}) {
  const [rows, setRows] = useState<Record<string, Partial<AttendanceEntry>>>({})
  const submit = useMutation({
    mutationFn: () => {
      const entries = Object.entries(rows)
        .filter(([, value]) => value.status)
        .map(([employeeId, value]) => ({ ...value, employeeId }) as AttendanceEntry)
      if (entries.length === 0) return Promise.resolve()
      return post<void>('/api/v1/hr/attendance/bulk', { attendanceDate: date, entries })
    },
    onSuccess: () => {
      setRows({})
      onDone()
    },
    onError: (err) => onError(describeError(err)),
  })

  const employees = useQuery({
    queryKey: ['hr-employees-attendable'],
    queryFn: () => api<Employee[]>('/api/v1/hr/employees', { query: { status: 'ACTIVE' } }),
  })

  if (!employees.data || employees.data.length === 0) return null

  return (
    <div className="flex flex-col gap-3">
      <ul className="divide-y divide-border">
        {employees.data.map((employee) => (
          <li key={employee.id} className="flex flex-wrap items-center gap-3 py-2">
            <span className="min-w-40 flex-1 text-sm text-ink">{employee.fullName}</span>
            <Select
              aria-label={`Status for ${employee.fullName}`}
              className="w-40"
              value={rows[employee.id]?.status ?? ''}
              onChange={(event) =>
                setRows((current) => ({
                  ...current,
                  [employee.id]: { ...current[employee.id], status: event.target.value },
                }))
              }
            >
              <option value="">Not marked</option>
              {ATTENDANCE_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {text(value)}
                </option>
              ))}
            </Select>
            <TextInput
              aria-label={`Overtime minutes for ${employee.fullName}`}
              type="number"
              min={0}
              placeholder="OT min"
              className="w-28"
              value={rows[employee.id]?.overtimeMinutes ?? ''}
              onChange={(event) =>
                setRows((current) => ({
                  ...current,
                  [employee.id]: {
                    ...current[employee.id],
                    overtimeMinutes: event.target.value ? Number(event.target.value) : undefined,
                  },
                }))
              }
            />
          </li>
        ))}
      </ul>
      <div>
        <Button loading={submit.isPending} onClick={() => submit.mutate()}>
          Save attendance for {date}
        </Button>
      </div>
    </div>
  )
}

/* -------------------------------------------------------------------- shared */

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-card border border-border bg-surface px-4 py-3">
      <p className="text-xs tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className="mt-1 text-sm font-medium text-ink">{value}</p>
    </div>
  )
}

function Th({ children, className = '', hideBelow }: { children: ReactNode; className?: string; hideBelow?: 'md' | 'lg' }) {
  return (
    <th
      scope="col"
      className={[
        'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
        hideBelow === 'md' ? 'hidden md:table-cell' : hideBelow === 'lg' ? 'hidden lg:table-cell' : '',
        className,
      ].join(' ')}
    >
      {children}
    </th>
  )
}

/** Re-exported so the employee form can reuse the same option lists. */
export { EMPLOYMENT_TYPES, GENDERS, type CreateEmployee, type Designation, type LeaveBalance }