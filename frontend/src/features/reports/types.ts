export interface ReportDescriptor {
  key: string
  title: string
  group: string
  description: string | null
  parameters: string[]
}

export type ReportCatalogue = Record<string, ReportDescriptor[]>

export interface ReportResult {
  title: string
  /** Column captions, in order. Rows are positional against these. */
  columns: string[]
  rows: Array<Array<string | number | boolean | null>>
  /** Headline figures keyed by their caption, in the order the report lists them. */
  summary: Record<string, string | number | boolean | null> | null
  notes: string[] | null
  rowCount: number
}

export const PARAMETER_LABELS: Record<string, string> = {
  from: 'From date',
  to: 'To date',
  date: 'Date',
  month: 'Month',
  studentId: 'Student ID',
  classId: 'Class ID',
  examId: 'Examination ID',
  storeId: 'Store ID',
  departmentId: 'Department ID',
  payrollRunId: 'Payroll run ID',
  status: 'Status',
  courseOfferingId: 'Course offering ID',
  staffId: 'Employee ID',
  category: 'Category',
}

export const DATE_PARAMETERS = new Set(['from', 'to', 'date'])