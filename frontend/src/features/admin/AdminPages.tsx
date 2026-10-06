import { useState } from 'react'
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
  Select,
  StatusBadge,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { dateTime, humanise } from '../../lib/format'

export interface RoleSummary {
  id: string
  code: string
  name: string
  description: string | null
  builtIn: boolean
  userCount: number
  permissionCount: number
}

export interface RoleDetail extends RoleSummary {
  permissions: string[]
}

export interface UserResponse {
  id: string
  username: string
  displayName: string
  email: string | null
  phone: string | null
  role: string
  status: string
  studentId: string | null
  employeeId: string | null
  mustChangePassword: boolean
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string | null
}

export interface ModuleState {
  key: string
  enabled: boolean
  enabledByDefault: boolean
}

/* ------------------------------------------------------------------- users */

export function UsersPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [editing, setEditing] = useState<UserResponse | null>(null)

  const roles = useQuery({
    queryKey: ['roles'],
    queryFn: () => api<RoleSummary[]>('/api/v1/admin/roles'),
  })

  const users = useQuery({
    queryKey: ['users', { page, search, role, status }],
    queryFn: () =>
      api<PageResponse<UserResponse>>('/api/v1/admin/users', {
        query: {
          page,
          size: 20,
          term: search || undefined,
          role: role || undefined,
          status: status || undefined,
        },
      }),
  })

  const createUser = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<UserResponse>('/api/v1/admin/users', body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
  })

  const rows = users.data?.data ?? []

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader title="Users" description="Who can sign in, and when they last did." />

      <Panel
        title="Accounts"
        padded={false}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <form
              className="flex gap-2"
              role="search"
              onSubmit={(event) => {
                event.preventDefault()
                setPage(0)
                setSearch(term)
              }}
            >
              <TextInput
                name="term"
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Name, username or email"
                aria-label="Search users"
                className="w-52"
              />
              <Button type="submit" variant="secondary">
                Search
              </Button>
            </form>
            <Select value={role} onChange={(e) => { setRole(e.target.value); setPage(0) }} aria-label="Role">
              <option value="">All roles</option>
              {(roles.data ?? []).map((item) => (
                <option key={item.id} value={item.code}>{item.name}</option>
              ))}
            </Select>
            <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} aria-label="Status">
              <option value="">All statuses</option>
              {['ACTIVE', 'LOCKED', 'DISABLED'].map((value) => (
                <option key={value} value={value}>{humanise(value)}</option>
              ))}
            </Select>
          </div>
        }
      >
        {users.isLoading ? (
          <LoadingState label="Loading users…" />
        ) : users.isError ? (
          <ErrorState error={users.error} onRetry={() => void users.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title="No users" description="No account matched these filters." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">User</th>
                  <th className="hidden px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase md:table-cell">Email</th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">Role</th>
                  <th className="px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">Status</th>
                  <th className="hidden px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase sm:table-cell">Last sign-in</th>
                  {can('USER_UPDATE') && (
                    <th className="px-4 py-2.5 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase"> </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {rows.map((user) => (
                  <tr key={user.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3">
                      <span className="block font-medium text-ink">{user.displayName}</span>
                      <span className="nums block text-xs text-ink-subtle">{user.username}</span>
                    </td>
                    <td className="hidden px-4 py-3 text-ink-muted md:table-cell">{user.email ?? '—'}</td>
                    <td className="px-4 py-3">
                      <Badge>{humanise(user.role)}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <StatusBadge status={user.status} />
                        {user.mustChangePassword && <Badge tone="warning">Must reset</Badge>}
                      </div>
                    </td>
                    <td className="nums hidden px-4 py-3 text-ink-subtle sm:table-cell">
                      {user.lastLoginAt ? dateTime(user.lastLoginAt) : 'Never'}
                    </td>
                    {can('USER_UPDATE') && (
                      <td className="px-4 py-3 text-right">
                        <Button size="sm" variant="ghost" onClick={() => setEditing(user)}>
                          Manage
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {users.data && <Pager page={users.data} onChange={setPage} />}

      {can('USER_CREATE') && (
        <Panel title="Create a user">
          <CreateUserForm
            roles={roles.data ?? []}
            pending={createUser.isPending}
            error={createUser.isError ? describeError(createUser.error) : null}
            onSubmit={(body) => createUser.mutate(body)}
          />
        </Panel>
      )}

      {editing && (
        <ManageUserPanel
          user={editing}
          roles={roles.data ?? []}
          onClose={() => setEditing(null)}
          onChanged={() => void queryClient.invalidateQueries({ queryKey: ['users'] })}
        />
      )}
    </div>
  )
}

function CreateUserForm({
  roles,
  pending,
  error,
  onSubmit,
}: {
  roles: RoleSummary[]
  pending: boolean
  error: string | null
  onSubmit: (body: Record<string, unknown>) => void
}) {
  const [values, setValues] = useState<Record<string, string>>({ mustChangePassword: 'true' })

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          username: values.username,
          password: values.password,
          displayName: values.displayName,
          email: values.email || null,
          phone: values.phone || null,
          role: values.role,
          mustChangePassword: values.mustChangePassword === 'true',
        })
      }}
    >
      <Field label="Full name" required>
        <TextInput value={values.displayName ?? ''} onChange={(e) => setValues({ ...values, displayName: e.target.value })} required maxLength={150} />
      </Field>
      <Field label="Username" required hint="Letters, digits, dot, dash and underscore.">
        <TextInput value={values.username ?? ''} onChange={(e) => setValues({ ...values, username: e.target.value })} required autoComplete="off" />
      </Field>
      <Field label="Email" hint="Used for password resets.">
        <TextInput type="email" value={values.email ?? ''} onChange={(e) => setValues({ ...values, email: e.target.value })} maxLength={180} />
      </Field>
      <Field label="Phone">
        <TextInput value={values.phone ?? ''} onChange={(e) => setValues({ ...values, phone: e.target.value })} maxLength={40} />
      </Field>
      <Field label="Role" required>
        <Select value={values.role ?? ''} onChange={(e) => setValues({ ...values, role: e.target.value })} required>
          <option value="">Choose a role</option>
          {roles.map((item) => (
            <option key={item.id} value={item.code}>
              {item.name} — {item.permissionCount} permissions
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Password" required hint="At least 10 characters.">
        <TextInput type="password" value={values.password ?? ''} onChange={(e) => setValues({ ...values, password: e.target.value })} required minLength={10} autoComplete="new-password" />
      </Field>
      <Field label="Force a password change at first sign-in">
        <Select value={values.mustChangePassword} onChange={(e) => setValues({ ...values, mustChangePassword: e.target.value })}>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </Select>
      </Field>
      {error && <p className="text-sm text-danger sm:col-span-2">{error}</p>}
      <div className="sm:col-span-2">
        <Button type="submit" loading={pending} disabled={!values.role}>
          Create user
        </Button>
      </div>
    </form>
  )
}

function ManageUserPanel({
  user,
  roles,
  onClose,
  onChanged,
}: {
  user: UserResponse
  roles: RoleSummary[]
  onClose: () => void
  onChanged: () => void
}) {
  const [values, setValues] = useState({
    displayName: user.displayName,
    email: user.email ?? '',
    phone: user.phone ?? '',
    role: user.role,
    status: user.status,
    mustChangePassword: String(user.mustChangePassword),
  })
  const [password, setPassword] = useState('')

  const save = useMutation({
    mutationFn: () =>
      put<UserResponse>(`/api/v1/admin/users/${user.id}`, {
        displayName: values.displayName,
        email: values.email || null,
        phone: values.phone || null,
        role: values.role,
        status: values.status,
        mustChangePassword: values.mustChangePassword === 'true',
      }),
    onSuccess: onChanged,
  })

  const reset = useMutation({
    mutationFn: () => post<void>(`/api/v1/admin/users/${user.id}/password`, { password }),
    onSuccess: () => {
      setPassword('')
      onChanged()
    },
  })

  return (
    <Panel
      title={`Manage ${user.username}`}
      description={user.studentId || user.employeeId ? 'Linked to a person in the directory.' : 'Not linked to a student or employee record.'}
      actions={<Button size="sm" variant="ghost" onClick={onClose}>Close</Button>}
    >
      <div className="grid gap-6 lg:grid-cols-2">
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate()
          }}
        >
          <p className="text-sm font-semibold text-ink">Details</p>
          <Field label="Full name" required>
            <TextInput value={values.displayName} onChange={(e) => setValues({ ...values, displayName: e.target.value })} required maxLength={150} />
          </Field>
          <Field label="Email">
            <TextInput type="email" value={values.email} onChange={(e) => setValues({ ...values, email: e.target.value })} maxLength={180} />
          </Field>
          <Field label="Phone">
            <TextInput value={values.phone} onChange={(e) => setValues({ ...values, phone: e.target.value })} maxLength={40} />
          </Field>
          <Field label="Role" required>
            <Select value={values.role} onChange={(e) => setValues({ ...values, role: e.target.value })}>
              {roles.map((item) => (
                <option key={item.id} value={item.code}>{item.name}</option>
              ))}
            </Select>
          </Field>
          <Field label="Status" required hint="Locking a user blocks sign-in without deleting anything.">
            <Select value={values.status} onChange={(e) => setValues({ ...values, status: e.target.value })}>
              {['ACTIVE', 'LOCKED', 'DISABLED'].map((value) => (
                <option key={value} value={value}>{humanise(value)}</option>
              ))}
            </Select>
          </Field>
          <Field label="Require a password change at next sign-in">
            <Select value={values.mustChangePassword} onChange={(e) => setValues({ ...values, mustChangePassword: e.target.value })}>
              <option value="false">No</option>
              <option value="true">Yes</option>
            </Select>
          </Field>
          {save.isError && <p className="text-sm text-danger">{describeError(save.error)}</p>}
          <div>
            <Button type="submit" loading={save.isPending}>Save changes</Button>
          </div>
        </form>

        <form
          className="grid content-start gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            reset.mutate()
          }}
        >
          <p className="text-sm font-semibold text-ink">Reset password</p>
          <p className="text-sm text-ink-subtle">
            Sets a new password immediately and signs the user out everywhere.
          </p>
          <Field label="New password" required hint="At least 10 characters.">
            <TextInput type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={10} autoComplete="new-password" />
          </Field>
          {reset.isError && <p className="text-sm text-danger">{describeError(reset.error)}</p>}
          {reset.isSuccess && <p className="text-sm text-success">Password reset. The user must sign in again.</p>}
          <div>
            <Button type="submit" variant="secondary" loading={reset.isPending} disabled={password.length < 10}>
              Reset password
            </Button>
          </div>
        </form>
      </div>
    </Panel>
  )
}

/* ------------------------------------------------------------------- roles */

export function RolesPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [openRole, setOpenRole] = useState<string | null>(null)

  const roles = useQuery({
    queryKey: ['roles'],
    queryFn: () => api<RoleSummary[]>('/api/v1/admin/roles'),
  })

  const rows = roles.data ?? []

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Roles"
        description="A role is a named set of permissions. Every user holds exactly one."
      />

      <Panel title="Roles" padded={false}>
        {roles.isLoading ? (
          <LoadingState label="Loading roles…" />
        ) : roles.isError ? (
          <ErrorState error={roles.error} onRetry={() => void roles.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title="No roles" description="This institution has no roles defined." />
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((role) => (
              <li key={role.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-ink">{role.name}</span>
                    {role.builtIn && <Badge tone="info">System</Badge>}
                  </div>
                  <p className="mt-0.5 text-sm text-ink-subtle">{role.description ?? 'No description'}</p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className="nums text-xs text-ink-subtle">{role.code}</span>
                  <span className="nums text-sm text-ink-muted">{role.permissionCount} permissions</span>
                  <span className="nums text-sm text-ink-muted">{role.userCount} users</span>
                  {can('ROLE_UPDATE') && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setOpenRole(openRole === role.id ? null : role.id)}
                    >
                      {openRole === role.id ? 'Close' : 'Permissions'}
                    </Button>
                  )}
                </div>
                {openRole === role.id && (
                  <RolePermissions
                    roleId={role.id}
                    onClose={() => setOpenRole(null)}
                    onSaved={() => void queryClient.invalidateQueries({ queryKey: ['roles'] })}
                  />
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {!can('ROLE_UPDATE') && (
        <Panel title="About permissions">
          <p className="text-sm text-ink-muted">
            Permissions are granted through roles. Only an administrator with role management access can change
            what a role may do.
          </p>
        </Panel>
      )}
    </div>
  )
}

function RolePermissions({
  roleId,
  onClose,
  onSaved,
}: {
  roleId: string
  onClose: () => void
  onSaved: () => void
}) {
  const detail = useQuery({
    queryKey: ['role-detail', roleId],
    queryFn: () => api<RoleDetail>(`/api/v1/admin/roles/${roleId}`),
  })

  const [granted, setGranted] = useState<string[] | null>(null)

  const save = useMutation({
    mutationFn: (permissions: string[]) =>
      put<RoleDetail>(`/api/v1/admin/roles/${roleId}/permissions`, { permissions }),
    onSuccess: () => {
      onSaved()
      onClose()
    },
  })

  if (detail.isLoading) return <LoadingState label="Loading permissions…" rows={2} />
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />

  const role = detail.data!
  const selected = granted ?? role.permissions
  const all = ALL_PERMISSIONS

  function toggle(permission: string) {
    const next = selected.includes(permission)
      ? selected.filter((item) => item !== permission)
      : [...selected, permission]
    setGranted(next)
  }

  const groups = new Map<string, string[]>()
  for (const permission of all) {
    const group = permission.split('_')[0]
    groups.set(group, [...(groups.get(group) ?? []), permission])
  }

  return (
    <div className="mt-4 w-full rounded-lg border border-border bg-surface-soft/40 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-ink">{role.name} permissions</p>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="ghost" onClick={() => setGranted(ALL_PERMISSIONS)}>Select all</Button>
          <Button size="sm" variant="ghost" onClick={() => setGranted([])}>Clear</Button>
          <Button
            size="sm"
            variant="secondary"
            loading={save.isPending}
            onClick={() => save.mutate(selected)}
          >
            Save
          </Button>
        </div>
      </div>

      <div className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[...groups.entries()].map(([group, permissions]) => (
          <fieldset key={group} className="rounded-md border border-border bg-surface p-3">
            <legend className="px-1 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
              {humanise(group)}
            </legend>
            <div className="mt-1 grid gap-1.5">
              {permissions.map((permission) => (
                <label key={permission} className="flex items-start gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={selected.includes(permission)}
                    onChange={() => toggle(permission)}
                  />
                  <span className="nums text-xs">{permission}</span>
                </label>
              ))}
            </div>
          </fieldset>
        ))}
      </div>

      {save.isError && <p className="mt-3 text-sm text-danger">{describeError(save.error)}</p>}
      <p className="mt-3 text-xs text-ink-subtle">
        {selected.length} of {ALL_PERMISSIONS.length} permissions granted.
      </p>
    </div>
  )
}

/**
 * The permission catalogue, mirroring the backend `Permission` enum. Kept in one place
 * so the role editor cannot offer a grant the server would reject, nor hide one it knows.
 */
const ALL_PERMISSIONS: string[] = [
  // Institution & configuration
  'INSTITUTION_READ', 'INSTITUTION_UPDATE', 'MODULE_CONFIG_READ', 'MODULE_CONFIG_UPDATE',
  'SETTINGS_READ', 'SETTINGS_UPDATE', 'AUDIT_READ',
  // Users, roles
  'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE', 'USER_RESET_PASSWORD', 'ROLE_READ',
  'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE', 'PERMISSION_READ', 'PERMISSION_ASSIGN',
  // Academic structure
  'ACADEMIC_READ', 'ACADEMIC_CREATE', 'ACADEMIC_UPDATE', 'ACADEMIC_DELETE', 'CURRICULUM_READ',
  'CURRICULUM_UPDATE', 'TIMETABLE_READ', 'TIMETABLE_MANAGE', 'CALENDAR_READ', 'CALENDAR_MANAGE',
  'ROOM_MANAGE',
  // Admission
  'ADMISSION_READ', 'ADMISSION_CREATE', 'ADMISSION_UPDATE', 'ADMISSION_REVIEW',
  'ADMISSION_APPROVE', 'ADMISSION_REJECT', 'ADMISSION_OFFER', 'CAMPAIGN_MANAGE',
  'APPLICATION_DOCUMENT_REVIEW',
  // Students
  'STUDENT_READ', 'STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_DELETE', 'STUDENT_STATUS_CHANGE',
  'ENROLLMENT_READ', 'ENROLLMENT_CREATE', 'ENROLLMENT_UPDATE', 'ENROLLMENT_DELETE',
  'GUARDIAN_READ', 'GUARDIAN_MANAGE', 'GRADUATION_READ', 'GRADUATION_MANAGE',
  // Attendance
  'ATTENDANCE_READ', 'ATTENDANCE_MARK', 'ATTENDANCE_APPROVE', 'ATTENDANCE_CORRECT',
  // Examination
  'EXAM_READ', 'EXAM_CREATE', 'EXAM_SCHEDULE_MANAGE', 'EXAM_ELIGIBILITY_MANAGE', 'MARKS_ENTER',
  'MARKS_VERIFY', 'MARKS_APPROVE', 'RESULT_PUBLISH', 'RESULT_CORRECT', 'RESULT_READ',
  'REPORT_CARD_READ', 'REPORT_CARD_GENERATE', 'TRANSCRIPT_READ', 'TRANSCRIPT_GENERATE',
  'CERTIFICATE_READ', 'CERTIFICATE_GENERATE',
  // Grading
  'GRADING_READ', 'GRADING_MANAGE',
  // Finance
  'FEE_READ', 'FEE_CREATE', 'FEE_APPROVE', 'INVOICE_READ', 'INVOICE_CREATE', 'PAYMENT_READ',
  'PAYMENT_CREATE', 'PAYMENT_REFUND', 'DISCOUNT_READ', 'DISCOUNT_MANAGE', 'SCHOLARSHIP_READ',
  'SCHOLARSHIP_MANAGE', 'FINANCE_REPORT_READ',
  // Accounting
  'ACCOUNTING_READ', 'ACCOUNTING_POST', 'ACCOUNTING_MANAGE', 'LEDGER_READ',
  'RECONCILIATION_MANAGE',
  // HR
  'EMPLOYEE_READ', 'EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_DELETE', 'LEAVE_READ',
  'LEAVE_APPROVE', 'LEAVE_APPLY', 'ATTENDANCE_EMPLOYEE_READ', 'ATTENDANCE_EMPLOYEE_MARK',
  'PAYROLL_READ', 'PAYROLL_PROCESS', 'PAYROLL_APPROVE', 'PAYSLIP_READ', 'SALARY_MANAGE',
  // Library
  'LIBRARY_READ', 'LIBRARY_MANAGE', 'LIBRARY_CIRCULATE',
  // Inventory
  'INVENTORY_READ', 'INVENTORY_MANAGE', 'INVENTORY_TRANSACT',
  // Assets
  'ASSET_READ', 'ASSET_MANAGE',
  // Documents
  'DOCUMENT_READ', 'DOCUMENT_UPLOAD', 'DOCUMENT_VERIFY', 'DOCUMENT_DELETE',
  // Communication
  'COMMUNICATION_READ', 'COMMUNICATION_SEND', 'TEMPLATE_READ', 'TEMPLATE_MANAGE',
  'NOTICE_PUBLISH',
  // Portals & self-service
  'PORTAL_STUDENT', 'PORTAL_PARENT', 'PORTAL_TEACHER', 'SELF_PROFILE_UPDATE',
  // Reporting / imports / search
  'REPORT_READ', 'REPORT_EXPORT', 'IMPORT_READ', 'IMPORT_RUN', 'SEARCH_GLOBAL',
  // Dashboards
  'DASHBOARD_ADMIN', 'DASHBOARD_PRINCIPAL', 'DASHBOARD_ACCOUNTANT', 'DASHBOARD_TEACHER',
  'DASHBOARD_STUDENT', 'DASHBOARD_PARENT',
]

/* ----------------------------------------------------------------- modules */

export function ModuleSettingsPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<Record<string, boolean>>({})

  // Reading the module list is not permission to change it; the toggle is hidden otherwise so
  // a read-only administrator is not offered a control the server will reject.
  const mayConfigure = can('MODULE_CONFIG_UPDATE')

  const modules = useQuery({
    queryKey: ['modules'],
    queryFn: () => api<ModuleState[]>('/api/v1/admin/modules'),
  })

  const toggle = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      put<ModuleState>('/api/v1/admin/modules', { key, enabled }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['modules'] }),
  })

  const rows = modules.data ?? []
  const core = rows.filter((row) => row.enabledByDefault)
  const optional = rows.filter((row) => !row.enabledByDefault)

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-5">
      <PageHeader
        title="Modules"
        description="Turning a module off hides it everywhere and locks its API. Data is kept."
      />

      {modules.isLoading ? (
        <LoadingState label="Loading modules…" />
      ) : modules.isError ? (
        <ErrorState error={modules.error} onRetry={() => void modules.refetch()} />
      ) : (
        <>
          <Panel title="Core modules" padded={false} description="Always on, because the school cannot run without them">
            <ModuleList
              rows={core}
              pending={pending}
              busy={toggle.isPending}
              disabled
              onToggle={(row) => {
                setPending((current) => ({ ...current, [row.key]: true }))
                toggle.mutate(
                  { key: row.key, enabled: !row.enabled },
                  {
                    onSettled: () =>
                      setPending((current) => {
                        const next = { ...current }
                        delete next[row.key]
                        return next
                      }),
                  },
                )
              }}
            />
          </Panel>

          <Panel title="Optional modules" padded={false} description="Off by default, switched on per institution">
            <ModuleList
              rows={optional}
              pending={pending}
              busy={toggle.isPending}
              disabled={!mayConfigure}
              onToggle={(row) => {
                setPending((current) => ({ ...current, [row.key]: true }))
                toggle.mutate(
                  { key: row.key, enabled: !row.enabled },
                  {
                    onSettled: () =>
                      setPending((current) => {
                        const next = { ...current }
                        delete next[row.key]
                        return next
                      }),
                  },
                )
              }}
            />
          </Panel>

          {toggle.isError && <p className="text-sm text-danger">{describeError(toggle.error)}</p>}
          {!mayConfigure && (
            <p className="text-xs text-ink-subtle">
              You can see which modules are switched on, but changing them needs the
              module configuration permission.
            </p>
          )}
        </>
      )}
    </div>
  )
}

function ModuleList({
  rows,
  pending,
  busy,
  disabled,
  onToggle,
}: {
  rows: ModuleState[]
  pending: Record<string, boolean>
  busy: boolean
  disabled?: boolean
  onToggle: (row: ModuleState) => void
}) {
  if (rows.length === 0) {
    return <EmptyState title="Nothing here" description="No modules in this group." />
  }

  return (
    <ul className="divide-y divide-border">
      {rows.map((row) => (
        <li key={row.key} className="flex items-start justify-between gap-4 p-4">
          <div className="min-w-0">
            <span className="block font-medium text-ink">{humanise(row.key)}</span>
            <p className="nums mt-0.5 text-xs text-ink-subtle">{row.key}</p>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <Badge tone={row.enabled ? 'success' : 'neutral'}>{row.enabled ? 'On' : 'Off'}</Badge>
            {!disabled && (
              <Button
                size="sm"
                variant={row.enabled ? 'ghost' : 'secondary'}
                loading={Boolean(pending[row.key]) || busy}
                onClick={() => onToggle(row)}
              >
                {row.enabled ? 'Disable' : 'Enable'}
              </Button>
            )}
            {disabled && !row.enabledByDefault && (
              <span className="text-xs text-ink-subtle">Read only</span>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}
