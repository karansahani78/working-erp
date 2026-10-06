/**
 * Navigation is described once, in data, and filtered by the permissions the signed-in
 * user actually holds. A link never appears that would lead to a 403.
 */
export interface NavItem {
  to: string
  label: string
  /** Every permission listed must be held; an empty list means always visible. */
  permissions?: string[]
  group: string
  icon: string
}

export const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', group: 'Overview', icon: 'grid' },

  { to: '/students', label: 'Students', permissions: ['STUDENT_READ'], group: 'People', icon: 'users' },
  { to: '/admissions', label: 'Admissions', permissions: ['ADMISSION_READ'], group: 'People', icon: 'inbox' },
  { to: '/admissions/campaigns', label: 'Campaigns', permissions: ['ADMISSION_READ'], group: 'People', icon: 'flag' },
  { to: '/guardians', label: 'Guardians', permissions: ['GUARDIAN_READ'], group: 'People', icon: 'user' },
  { to: '/employees', label: 'Employees', permissions: ['EMPLOYEE_READ'], group: 'People', icon: 'badge' },
  { to: '/payroll', label: 'Payroll', permissions: ['PAYROLL_READ'], group: 'People', icon: 'wallet' },
  { to: '/leave', label: 'Leave', permissions: ['LEAVE_READ'], group: 'People', icon: 'sun' },

  { to: '/academics', label: 'Academic structure', permissions: ['ACADEMIC_READ'], group: 'Academic', icon: 'school' },
  { to: '/curriculum', label: 'Curriculum', permissions: ['ACADEMIC_READ'], group: 'Academic', icon: 'list' },
  { to: '/timetable', label: 'Timetable', permissions: ['ACADEMIC_READ'], group: 'Academic', icon: 'calendar' },
  { to: '/examinations', label: 'Examinations', permissions: ['EXAM_READ'], group: 'Academic', icon: 'award' },
  { to: '/results', label: 'Results', permissions: ['RESULT_READ'], group: 'Academic', icon: 'chart' },
  { to: '/report-cards', label: 'Report cards', permissions: ['REPORT_CARD_READ'], group: 'Academic', icon: 'file' },
  { to: '/transcripts', label: 'Transcripts', permissions: ['TRANSCRIPT_READ'], group: 'Academic', icon: 'scroll' },

  { to: '/attendance', label: 'Attendance', permissions: ['ATTENDANCE_READ'], group: 'Operations', icon: 'check' },
  { to: '/schedules', label: 'Calendar', permissions: ['ACADEMIC_READ'], group: 'Operations', icon: 'calendar' },

  { to: '/fees', label: 'Fee structures', permissions: ['FEE_READ'], group: 'Finance', icon: 'coins' },
  { to: '/invoices', label: 'Invoices', permissions: ['INVOICE_READ'], group: 'Finance', icon: 'receipt' },
  { to: '/payments', label: 'Payments', permissions: ['PAYMENT_READ'], group: 'Finance', icon: 'card' },
  { to: '/refunds', label: 'Refunds', permissions: ['PAYMENT_REFUND'], group: 'Finance', icon: 'undo' },
  { to: '/scholarships', label: 'Scholarships', permissions: ['FEE_READ'], group: 'Finance', icon: 'award' },
  { to: '/accounting', label: 'Accounting', permissions: ['ACCOUNTING_READ'], group: 'Finance', icon: 'ledger' },

  { to: '/library', label: 'Library', permissions: ['LIBRARY_READ'], group: 'Modules', icon: 'library' },
  { to: '/inventory', label: 'Inventory', permissions: ['INVENTORY_READ'], group: 'Modules', icon: 'box' },
  { to: '/assets', label: 'Assets', permissions: ['ASSET_READ'], group: 'Modules', icon: 'tag' },
  { to: '/documents', label: 'Documents', permissions: ['DOCUMENT_READ'], group: 'Modules', icon: 'folder' },

  { to: '/reports', label: 'Reports', permissions: ['REPORT_READ'], group: 'Insight', icon: 'doc' },
  { to: '/imports', label: 'Data imports', permissions: ['IMPORT_READ'], group: 'Insight', icon: 'upload' },
  { to: '/audit', label: 'Audit log', permissions: ['AUDIT_READ'], group: 'Insight', icon: 'shield' },

  { to: '/users', label: 'Users', permissions: ['USER_READ'], group: 'Administration', icon: 'key' },
  { to: '/roles', label: 'Roles', permissions: ['ROLE_READ'], group: 'Administration', icon: 'lock' },
  { to: '/settings', label: 'Modules', permissions: ['MODULE_CONFIG_READ'], group: 'Administration', icon: 'cog' },
]

/** Group order in the sidebar. Anything unlisted falls to the end. */
export const NAV_GROUP_ORDER = [
  'Overview',
  'People',
  'Academic',
  'Operations',
  'Finance',
  'Modules',
  'Insight',
  'Administration',
]