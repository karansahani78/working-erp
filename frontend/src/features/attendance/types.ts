export interface AttendanceRecord {
  id: string
  studentId: string
  courseOfferingId: string
  enrollmentId: string
  attendanceDate: string
  timeSlotId: string | null
  periodType: string | null
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED'
  minutesLate: number | null
  workflowStatus: string
  remarks: string | null
  recordedBy: string | null
  createdAt: string
  updatedAt: string
}

export interface AttendanceSummary {
  studentId: string
  from: string
  to: string
  totalPeriods: number
  presentPeriods: number
  absentPeriods: number
  latePeriods: number
  excusedPeriods: number
  attendancePercentage: string
}

export interface CourseOffering {
  id: string
  offeringCode: string
  academicModel: string
  courseId: string
  courseCode: string
  courseName: string
  academicYearId: string
  academicYearName: string
  semesterId: string | null
  semesterName: string | null
  programId: string | null
  programName: string | null
  curriculumId: string | null
  schoolClassId: string | null
  schoolClassName: string | null
  sectionId: string | null
  sectionName: string | null
  teacherId: string | null
  teacherName: string | null
  roomId: string | null
}

export type { PageResponse as Page } from '../../lib/api'

export const ATTENDANCE_STATUSES = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'] as const

/** Late and excused both count as attending; only ABSENT removes credit. */
export const STATUS_TONE: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  PRESENT: 'success',
  LATE: 'warning',
  EXCUSED: 'info',
  ABSENT: 'danger',
}