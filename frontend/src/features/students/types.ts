export interface Student {
  id: string
  studentNumber: string
  userId: string | null
  admissionId: string | null
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
  enrollmentDate: string | null
  status: string
  createdAt: string
}

export type { PageResponse as Page } from '../../lib/api'

/** Mirrors StudentRequest on the backend: firstName is the only required field. */
export interface StudentRequest {
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
  status?: string
}

export const STUDENT_STATUSES = [
  'APPLICANT',
  'ACTIVE',
  'SUSPENDED',
  'TRANSFERRED',
  'WITHDRAWN',
  'GRADUATED',
  'ALUMNI',
] as const

/** The column is free text; these are the values the rest of the system counts on. */
export const GENDERS = ['MALE', 'FEMALE', 'OTHER'] as const