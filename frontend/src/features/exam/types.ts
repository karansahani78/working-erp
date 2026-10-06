export interface Examination {
  id: string
  name: string
  code: string
  academicYearId: string
  semesterId: string | null
  examType: string
  startDate: string
  endDate: string
  gradingScaleId: string | null
  maxTotalMarks: number
  passPercentage: number
  status: string
}

export interface ExamSubject {
  id: string
  examinationId: string
  courseOfferingId: string
  subjectName: string
  subjectCode: string
  examDate: string
  startTime: string
  endTime: string
  maxMarks: number
  passMarks: number
  roomId: string | null
}

export interface Result {
  id: string
  studentId: string
  examinationId: string
  examSubjectId: string
  enrollmentId: string
  marksObtained: number
  maxMarks: number
  percentage: number
  letterGrade: string | null
  gradePoint: number | null
  pass: boolean | null
  status: string
  publishedAt: string | null
}



export type { PageResponse as Page } from '../../lib/api'

export const EXAM_STATUSES = [
  'PLANNED',
  'SCHEDULED',
  'IN_PROGRESS',
  'MARKS_ENTERED',
  'VERIFIED',
  'APPROVED',
  'PUBLISHED',
  'ARCHIVED',
] as const

export const RESULT_STATUSES = ['DRAFT', 'MARKS_ENTERED', 'VERIFIED', 'APPROVED', 'PUBLISHED'] as const