import type { PageResponse as Page } from '../../lib/api'

export type { Page }

export interface AdmissionCampaign {
  id: string
  name: string
  code: string
  academicYearId: string | null
  openDate: string | null
  closeDate: string | null
  applicationFee: number | null
  capacity: number | null
  status: string
  documentRequirements: string[] | null
}

/** Mirrors CampaignRequest. Only the name and code are required by the server. */
export interface CampaignRequest {
  name: string
  code: string
  academicYearId?: string | null
  openDate?: string | null
  closeDate?: string | null
  applicationFee?: number | null
  capacity?: number | null
  status?: string
  documentRequirements?: string[]
}

export interface AdmissionApplication {
  id: string
  campaignId: string
  referenceCode: string
  firstName: string
  middleName: string | null
  lastName: string | null
  dateOfBirth: string | null
  gender: string | null
  nationality: string | null
  phone: string | null
  email: string | null
  address: string | null
  photoUrl: string | null
  applyingProgramId: string | null
  applyingClassId: string | null
  previousSchool: string | null
  previousQualification: string | null
  previousPercentage: number | null
  entranceScore: number | null
  meritScore: number | null
  status: string
  submittedAt: string | null
  decidedAt: string | null
  decisionNotes: string | null
  studentId: string | null
  createdAt: string
}

/** Mirrors ApplicationRequest; campaignId and firstName are required by the server. */
export interface ApplicationRequest {
  campaignId: string
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
  applyingProgramId?: string
  applyingClassId?: string
  previousSchool?: string
  previousQualification?: string
  previousPercentage?: number
  entranceScore?: number
  meritScore?: number
}

/** Mirrors ApplicationUpdate, whose absent fields keep their current value. */
export interface ApplicationUpdate {
  phone?: string
  email?: string
  address?: string
  entranceScore?: number
  meritScore?: number
}

export interface ApplicationDocument {
  id: string
  applicationId: string
  documentType: string
  fileName: string
  storageKey: string
  contentType: string | null
  sizeBytes: number | null
  status: string
  reviewNotes: string | null
  reviewedAt: string | null
}

export interface DocumentRequest {
  documentType: string
  fileName: string
  storageKey: string
  contentType?: string
  sizeBytes?: number
}

/** One document's verification outcome, posted as a batch to the verify endpoint. */
export interface DocumentVerification {
  documentType: string
  status: string
  notes?: string
}

export interface AdmissionDecision {
  id: string
  applicationId: string
  decision: string
  decidedBy: string | null
  decidedAt: string
  notes: string | null
}

/** approve and enrol answer with the affected objects rather than the application alone. */
export interface ApprovalResult {
  application: AdmissionApplication
  student: { id: string; studentNumber: string } | null
  created: boolean
}

export interface EnrolResult {
  application: AdmissionApplication
  enrollment: { id: string; rollNumber: string | null } | null
  created: boolean
}

export interface VerificationResult {
  application: AdmissionApplication
  missingDocuments: string[] | null
  rejected: boolean
}

/** The blueprint lifecycle, in the order the server enforces it. */
export const APPLICATION_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'UNDER_REVIEW',
  'DOCUMENT_VERIFICATION',
  'ELIGIBILITY',
  'ENTRANCE',
  'SELECTED',
  'WAITLISTED',
  'REJECTED',
  'ADMITTED',
  'ENROLLED',
] as const

export const ACTIVE_APPLICATION_STATUSES = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'DOCUMENT_VERIFICATION',
  'ELIGIBILITY',
  'ENTRANCE',
  'SELECTED',
  'WAITLISTED',
] as const

export const CAMPAIGN_STATUSES = ['PLANNED', 'OPEN', 'CLOSED', 'ARCHIVED'] as const

export const DOCUMENT_STATUSES = ['PENDING', 'ACCEPTED', 'REJECTED'] as const

/** The outcomes decideEligibility accepts; the server maps each to the next status. */
export const ELIGIBILITY_DECISIONS = ['SELECTED', 'WAITLISTED', 'REJECTED'] as const