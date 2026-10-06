import type { PageResponse as Page } from '../../lib/api'

export type { Page }

/* --------------------------------------------------------------- designations */

export interface Designation {
  id: string
  code: string
  name: string
  level: string | null
  description: string | null
  active: boolean
}

export interface CreateDesignation {
  code: string
  name: string
  level?: string
  description?: string
}

/* ------------------------------------------------------------------ employees */

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'VISITING' | 'INTERN'

/** Mirrors CreateEmployee; employeeCode and joinDate are required by the server. */
export interface CreateEmployee {
  employeeCode: string
  firstName: string
  middleName?: string
  lastName?: string
  dateOfBirth?: string
  gender?: string
  nationality?: string
  phone?: string
  email?: string
  address?: string
  photoUrl?: string
  employmentType?: EmploymentType
  joinDate: string
  departmentId?: string
  designationId?: string
  salaryStructureId?: string
  userId?: string
  bankName?: string
  bankAccount?: string
  taxNumber?: string
}

/** Mirrors UpdateEmployee, whose absent fields keep their current value. */
export interface UpdateEmployee {
  firstName?: string
  middleName?: string
  lastName?: string
  gender?: string
  nationality?: string
  phone?: string
  email?: string
  address?: string
  departmentId?: string
  designationId?: string
  salaryStructureId?: string
  bankName?: string
  bankAccount?: string
  taxNumber?: string
}

export interface TerminateEmployee {
  exitDate: string
  status?: string
  reason?: string
}

export interface Employee {
  id: string
  employeeCode: string
  userId: string | null
  firstName: string
  middleName: string | null
  lastName: string | null
  fullName: string
  dateOfBirth: string | null
  gender: string | null
  nationality: string | null
  phone: string | null
  email: string | null
  address: string | null
  photoUrl: string | null
  employmentType: string | null
  joinDate: string | null
  exitDate: string | null
  status: string
  departmentId: string | null
  departmentName: string | null
  designationId: string | null
  designationName: string | null
  salaryStructureId: string | null
  salaryStructureName: string | null
  bankName: string | null
  bankAccount: string | null
  taxNumber: string | null
}

export interface Employment {
  id: string
  employeeId: string
  designationId: string | null
  designationName: string | null
  employmentType: string | null
  startDate: string | null
  endDate: string | null
  notes: string | null
}

export interface Qualification {
  id: string
  code: string
  name: string
  level: string | null
}

export interface EmployeeQualification {
  id: string
  qualificationId: string
  qualificationName: string
  institution: string | null
  awardedOn: string | null
  expiresOn: string | null
  notes: string | null
}

export interface EmployeeDocument {
  id: string
  documentType: string
  fileName: string
  storageKey: string
  contentType: string | null
  sizeBytes: number | null
  status: string
  reviewNotes: string | null
  reviewedAt: string | null
}

export interface EmployeeProfile {
  employee: Employee
  employments: Employment[] | null
  qualifications: EmployeeQualification[] | null
  documents: EmployeeDocument[] | null
}

/* ---------------------------------------------------------------------- leave */

export interface LeaveType {
  id: string
  code: string
  name: string
  daysPerYear: number | null
  paid: boolean
  description: string | null
  active: boolean
}

export interface CreateLeaveType {
  code: string
  name: string
  daysPerYear?: number
  paid?: boolean
  description?: string
}

export interface LeaveRequest {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  leaveTypeId: string
  leaveTypeName: string
  startDate: string
  endDate: string
  days: number | null
  reason: string | null
  status: string
  decidedBy: string | null
  decidedAt: string | null
  decisionNotes: string | null
}

export interface LeaveBalance {
  id: string
  leaveTypeId: string
  leaveTypeName: string
  leaveYear: number
  entitled: number
  used: number
  remaining: number
}

export interface ApplyLeave {
  employeeId?: string
  leaveTypeId: string
  startDate: string
  endDate: string
  reason?: string
}

export interface DecideLeave {
  status: string
  notes?: string
}

/* ---------------------------------------------------------------- attendance */

export interface EmployeeAttendance {
  id: string
  employeeId: string
  employeeCode: string
  attendanceDate: string
  status: string
  checkIn: string | null
  checkOut: string | null
  overtimeMinutes: number
  remarks: string | null
}

export interface MarkAttendance {
  attendanceDate: string
  status: string
  checkIn?: string
  checkOut?: string
  overtimeMinutes?: number
  remarks?: string
}

export interface AttendanceEntry extends MarkAttendance {
  employeeId: string
}

export interface BulkAttendance {
  attendanceDate: string
  entries: AttendanceEntry[]
}

/* -------------------------------------------------------------------- salary */

export interface SalaryComponent {
  id?: string
  name: string
  componentType: string
  valueType: string
  value: number
  taxable: boolean
  amountOnBasic?: number | null
}

export interface SalaryStructure {
  id: string
  code: string
  name: string
  basicSalary: number
  currency: string | null
  overtimeRate: number | null
  effectiveFrom: string | null
  description: string | null
  status: string
  components: SalaryComponent[] | null
}

export interface CreateSalaryStructure {
  code: string
  name: string
  basicSalary: number
  currency?: string
  overtimeRate?: number
  effectiveFrom?: string
  description?: string
  components?: SalaryComponent[]
}

export interface TaxBracket {
  upTo: number | null
  rate: number
}

export interface TaxRule {
  id: string
  code: string
  name: string
  brackets: TaxBracket[] | null
  effectiveFrom: string
  effectiveTo: string | null
  description: string | null
  status: string
}

export interface EmployeeLoan {
  id: string
  employeeId: string
  employeeName: string
  principal: number
  outstanding: number
  monthlyInstalment: number | null
  status: string
}

export interface ProcessPayroll {
  periodYear: number
  periodMonth: number
  employeeIds?: string[]
  bonuses?: Record<string, number>
  notes?: string
}

export interface PayrollRun {
  id: string
  periodYear: number
  periodMonth: number
  period: string
  status: string
  employeeCount: number
  totalGross: number
  totalDeductions: number
  totalTax: number
  totalNet: number
  processedAt: string | null
  approvedAt: string | null
  notes: string | null
  journalEntryId: string | null
}

export interface PayslipItem {
  code: string
  name: string
  componentType: string
  amount: number
}

export interface Payslip {
  id: string
  payrollRunId: string
  employeeId: string
  employeeCode: string
  employeeName: string
  designation: string | null
  basicSalary: number
  allowances: number
  overtime: number
  bonus: number | null
  gross: number
  tax: number
  otherDeductions: number
  loanDeduction: number | null
  net: number
  currency: string | null
  items: PayslipItem[] | null
  taxBreakdown: Record<string, number> | null
}

export interface LedgerAccounts {
  salaryExpense: string
  netPayable: string
  taxPayable: string
  loanReceivable: string
}

export const EMPLOYEE_STATUSES = ['ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED', 'RETIRED'] as const
export const EMPLOYMENT_TYPES: EmploymentType[] = [
  'FULL_TIME',
  'PART_TIME',
  'CONTRACT',
  'VISITING',
  'INTERN',
]
export const ATTENDANCE_STATUSES = ['PRESENT', 'ABSENT', 'HALF_DAY', 'LEAVE', 'HOLIDAY'] as const
/** The server only accepts these two outcomes for a leave decision; cancelling is a separate action. */
export const LEAVE_DECISIONS = ['APPROVED', 'REJECTED'] as const
export const PAYROLL_STATUSES = ['DRAFT', 'PROCESSED', 'APPROVED', 'POSTED'] as const
export const COMPONENT_TYPES = ['ALLOWANCE', 'DEDUCTION'] as const
export const VALUE_TYPES = ['FIXED', 'PERCENT_OF_BASIC'] as const
export const GENDERS = ['MALE', 'FEMALE', 'OTHER'] as const