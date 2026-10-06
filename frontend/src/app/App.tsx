import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { AppShell, ShellFallback } from './AppShell'
import { LoginPage } from '../auth/LoginPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { StudentListPage } from '../features/students/StudentListPage'
import { StudentDetailPage } from '../features/students/StudentDetailPage'
import { StudentFormPage } from '../features/students/StudentFormPage'
import { StudentEditPage } from '../features/students/StudentEditPage'
import { StudentEnrolmentPage } from '../features/students/StudentEnrolmentPage'
import { SearchPage } from '../features/search/SearchPage'
import { AcademicStructurePage } from '../features/academic/AcademicStructurePage'
import { CalendarPage, CurriculumPage, TimetablePage } from '../features/academic/AcademicPages'
import { FeeStructuresPage, ReceivablesPage } from '../features/finance/FeePages'
import { StudentFinancePage } from '../features/finance/StudentFinancePage'
import { AttendancePage } from '../features/attendance/AttendancePage'
import { ExaminationsPage } from '../features/exam/ExamPages'
import { ResultsPage } from '../features/exam/ResultsPage'
import { ReportCardsPage, TranscriptsPage } from '../features/exam/ReportCardPages'
import {
  InvoicesPage,
  PaymentsPage,
  RefundsPage,
  ScholarshipsPage,
} from '../features/finance/CollectionsPages'
import { AccountingPage } from '../features/accounting/AccountingPage'
import { InventoryPage } from '../features/inventory/InventoryPage'
import AssetsPage from '../features/assets/AssetsPage'
import { ModuleSettingsPage, RolesPage, UsersPage } from '../features/admin/AdminPages'
import { ChangePasswordPage } from '../auth/ChangePasswordPage'
import { AuditPage } from '../features/audit/AuditPage'
import { ReportsPage } from '../features/reports/ReportsPage'
import { ImportBatchPage, ImportsPage, ImportUploadPage } from '../features/imports/ImportsPage'
import { LibraryPage } from '../features/library/LibraryPage'
import DocumentsPage from '../features/documents/DocumentsPage'
import {
  EmployeeDetailPage,
  EmployeeAttendancePage,
  EmployeesPage,
  LeavePage,
  PayrollPage,
  PayrollRunDetailPage,
} from '../features/hr/HrPages'

const EmployeeDetail = withId(EmployeeDetailPage)
const PayrollRunDetail = withId(PayrollRunDetailPage)
const ApplicationDetail = withId(ApplicationDetailPage)
const GuardianDetail = withId(GuardianDetailPage)

/** The `:id` segment, kept separate so both route shapes can be handled in one component. */
function useIdParam(): { id?: string } {
  return useParams()
}
import { EmptyState, Spinner } from '../components/ui'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Student } from '../features/students/types'
import {
  ApplicationDetailPage,
  ApplicationListPage,
  CampaignListPage,
} from '../features/admissions/AdmissionsPages'
import { GuardianDetailPage, GuardiansPage } from '../features/guardians/GuardiansPage'
import type { ReactNode } from 'react'
import { useParams } from 'react-router-dom'

/** Reads a route parameter and hands it to a page that expects an id prop. */
function withId<P extends { id: string }>(Component: (props: P) => ReactNode) {
  return function WithIdRoute(): ReactNode {
    const { id } = useParams()
    return id ? <Component {...({ id } as P)} /> : null
  }
}

/** The enrolment screen is reachable by uuid or by student number. */
function StudentEnrolmentRoute(): ReactNode {
  const { studentNumber } = useParams()
  const { id } = useIdParam()
  const lookup = useQuery({
    queryKey: ['student-by-number', studentNumber],
    queryFn: () =>
      api<Student>(`/api/v1/students/number/${studentNumber}`, { query: { enroll: 'true' } }),
    enabled: Boolean(studentNumber),
  })

  if (id) return <StudentEnrolmentPage studentId={id} />
  if (lookup.data?.id) return <StudentEnrolmentPage studentId={lookup.data.id} />
  return <EmptyState title="Find the student" description="Loading the student record…" />
}

/** Blocks a route until the session is known, then requires every listed permission. */
function Protected({
  permissions,
  module,
  children,
}: {
  permissions?: string[]
  module?: string
  children: ReactNode
}) {
  const { user, initialising, canAny } = useAuth()
  const { pathname } = useLocation()

  if (initialising) return <ShellFallback />
  // Both redirects below sit inside the layout that wraps the password screen itself, so
  // without these comparisons the guard would redirect to the page it is already showing and
  // fight whatever navigation put it there.
  if (!user) return pathname === '/login' ? null : <Navigate to="/login" replace />
  // Held to the password screen until the password is changed, rather than being told about
  // it and then left to wander.
  if (user.mustChangePassword && pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (permissions && permissions.length > 0 && !canAny(...permissions)) {
    return (
      <div className="mx-auto max-w-2xl py-16">
        <EmptyState
          title="You do not have access to this page"
          description={
            module
              ? `${module} needs a permission your roles do not hold.`
              : 'Ask an administrator if you believe you should have it.'
          }
        />
      </div>
    )
  }
  return <>{children}</>
}

/** A single-permission guard, which is what nearly every page needs. */
function Gate({
  permission,
  module,
  children,
}: {
  permission: string
  module?: string
  children: ReactNode
}) {
  return (
    <Protected permissions={[permission]} module={module}>
      {children}
    </Protected>
  )
}

export function App() {
  const { initialising, user } = useAuth()

  return (
    <Routes>
      <Route
        path="/login"
        element={initialising ? <ShellFallback /> : user ? <Navigate to="/" replace /> : <LoginPage />}
      />

      <Route
        element={
          <Protected>
            <AppShell />
          </Protected>
        }
      >
        <Route index element={<DashboardPage />} />

        <Route
          path="students"
          element={
            <Protected permissions={['STUDENT_READ']}>
              <StudentListPage />
            </Protected>
          }
        />
        <Route
          path="students/new"
          element={
            <Protected permissions={['STUDENT_CREATE']}>
              <StudentFormPage />
            </Protected>
          }
        />
        <Route
          path="students/number/:studentNumber"
          element={
            <Protected permissions={['STUDENT_READ']}>
              <StudentDetailPage />
            </Protected>
          }
        />
        <Route
          path="students/:id/edit"
          element={
            <Protected permissions={['STUDENT_UPDATE']}>
              <StudentEditPage />
            </Protected>
          }
        />
        <Route
          path="students/:id/enrolment"
          element={
            <Protected permissions={['ENROLLMENT_CREATE']}>
              <StudentEnrolmentRoute />
            </Protected>
          }
        />
        <Route
          path="students/number/:studentNumber/enrolment"
          element={
            <Protected permissions={['ENROLLMENT_CREATE']}>
              <StudentEnrolmentRoute />
            </Protected>
          }
        />
        <Route
          path="students/:id"
          element={
            <Protected permissions={['STUDENT_READ']}>
              <StudentDetailPage />
            </Protected>
          }
        />
        <Route
          path="search"
          element={
            <Protected permissions={['SEARCH_GLOBAL']}>
              <SearchPage />
            </Protected>
          }
        />

        <Route path="academics" element={<Gate permission="ACADEMIC_READ"><AcademicStructurePage /></Gate>} />

        <Route path="fees" element={<Gate permission="FEE_READ"><FeeStructuresPage /></Gate>} />
        <Route path="receivables" element={<Gate permission="FINANCE_REPORT_READ"><ReceivablesPage /></Gate>} />
        <Route
          path="students/:id/finance"
          element={<Gate permission="FEE_READ"><StudentFinancePage /></Gate>}
        />
        <Route
          path="students/number/:studentNumber/finance"
          element={<Gate permission="FEE_READ"><StudentFinancePage /></Gate>}
        />

        <Route path="attendance" element={<Gate permission="ATTENDANCE_READ"><AttendancePage /></Gate>} />
        <Route path="examinations" element={<Gate permission="EXAM_READ"><ExaminationsPage /></Gate>} />

        <Route path="users" element={<Gate permission="USER_READ"><UsersPage /></Gate>} />
        <Route path="roles" element={<Gate permission="ROLE_READ"><RolesPage /></Gate>} />
        <Route path="settings" element={<Gate permission="MODULE_CONFIG_READ"><ModuleSettingsPage /></Gate>} />

        {/* Remaining modules: the shell and permissions are in place, the screens land next. */}
        <Route path="admissions" element={<Gate permission="ADMISSION_READ" module="Admissions"><ApplicationListPage /></Gate>} />
        <Route path="admissions/campaigns" element={<Gate permission="ADMISSION_READ" module="Admissions"><CampaignListPage /></Gate>} />
        <Route path="admissions/applications/:id" element={<Gate permission="ADMISSION_READ" module="Admissions"><ApplicationDetail /></Gate>} />
        <Route path="guardians" element={<Gate permission="GUARDIAN_READ" module="Guardians"><GuardiansPage /></Gate>} />
        <Route path="guardians/:id" element={<Gate permission="GUARDIAN_READ" module="Guardians"><GuardianDetail /></Gate>} />
        <Route path="employees" element={<Gate permission="EMPLOYEE_READ" module="Employees"><EmployeesPage /></Gate>} />
        <Route path="employees/:id" element={<Gate permission="EMPLOYEE_READ" module="Employees"><EmployeeDetail /></Gate>} />
        <Route path="employees/:id/attendance" element={<Gate permission="EMPLOYEE_READ" module="Employees"><EmployeeAttendancePage /></Gate>} />
        <Route path="payroll" element={<Gate permission="PAYROLL_READ" module="Payroll"><PayrollPage /></Gate>} />
        <Route path="payroll/:id" element={<Gate permission="PAYROLL_READ" module="Payroll"><PayrollRunDetail /></Gate>} />
        <Route path="leave" element={<Gate permission="LEAVE_READ" module="Leave"><LeavePage /></Gate>} />
        <Route path="curriculum" element={<Gate permission="ACADEMIC_READ" module="Curriculum"><CurriculumPage /></Gate>} />
        <Route path="timetable" element={<Gate permission="ACADEMIC_READ" module="Timetable"><TimetablePage /></Gate>} />
        <Route path="schedules" element={<Gate permission="ACADEMIC_READ" module="Calendar"><CalendarPage /></Gate>} />
        <Route path="results" element={<Gate permission="RESULT_READ" module="Results"><ResultsPage /></Gate>} />
        <Route path="report-cards" element={<Gate permission="REPORT_CARD_READ" module="Report cards"><ReportCardsPage /></Gate>} />
        <Route path="transcripts" element={<Gate permission="TRANSCRIPT_READ" module="Transcripts"><TranscriptsPage /></Gate>} />
        <Route path="invoices" element={<Gate permission="INVOICE_READ" module="Invoices"><InvoicesPage /></Gate>} />
        <Route path="payments" element={<Gate permission="PAYMENT_READ" module="Payments"><PaymentsPage /></Gate>} />
        <Route path="refunds" element={<Gate permission="PAYMENT_REFUND" module="Refunds"><RefundsPage /></Gate>} />
        <Route path="scholarships" element={<Gate permission="FEE_READ" module="Scholarships"><ScholarshipsPage /></Gate>} />
        <Route path="accounting" element={<Gate permission="ACCOUNTING_READ" module="Accounting"><AccountingPage /></Gate>} />
        <Route path="library" element={<Gate permission="LIBRARY_READ" module="Library"><LibraryPage /></Gate>} />
        <Route path="inventory" element={<Gate permission="INVENTORY_READ" module="Inventory"><InventoryPage /></Gate>} />
        <Route path="assets" element={<Gate permission="ASSET_READ" module="Assets"><AssetsPage /></Gate>} />
        <Route path="documents" element={<Gate permission="DOCUMENT_READ" module="Documents"><DocumentsPage /></Gate>} />
        <Route path="reports" element={<Gate permission="REPORT_READ"><ReportsPage /></Gate>} />
        <Route path="imports" element={<Gate permission="IMPORT_READ"><ImportsPage /></Gate>} />
        <Route path="imports/new" element={<Gate permission="IMPORT_READ"><ImportUploadPage /></Gate>} />
        <Route path="imports/:id" element={<Gate permission="IMPORT_READ"><ImportBatchPage /></Gate>} />
        <Route path="audit" element={<Gate permission="AUDIT_READ"><AuditPage /></Gate>} />
        <Route path="change-password" element={<ChangePasswordPage />} />

      </Route>

      {/* Anything not routed above is a mistake, not a blank page. */}
      <Route
        path="*"
        element={
          <div className="flex min-h-screen items-center justify-center bg-canvas">
            {initialising ? (
              <Spinner className="h-8 w-8 text-primary" />
            ) : (
              <EmptyState
                title="That page does not exist"
                description="The link may be out of date."
              />
            )}
          </div>
        }
      />
    </Routes>
  )
}