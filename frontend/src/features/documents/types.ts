import type { PageResponse } from '../../lib/api'

export type DocumentStatus = 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'ARCHIVED'
export type VerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED'
export type PrincipalType = 'USER' | 'ROLE'
export type AccessLevel = 'VIEW' | 'EDIT' | 'MANAGE'

/** Who a document can be linked to, matching the `relatedType` the backend filters on. */
export const RELATED_TYPES = ['STUDENT', 'EMPLOYEE', 'ADMISSION', 'APPLICATION', 'OTHER'] as const
export type RelatedType = (typeof RELATED_TYPES)[number]

export interface StoredDocument {
  id: string
  documentNumber: string
  title: string
  description: string | null
  documentType: string
  category: string | null
  ownerUserId: string | null
  ownerName: string | null
  ownerDepartmentId: string | null
  ownerDepartmentName: string | null
  relatedType: string | null
  relatedId: string | null
  storageProvider: string
  originalFilename: string
  contentType: string
  fileSize: number
  checksumSha256: string
  pageCount: number | null
  currentVersion: number
  status: DocumentStatus
  verificationStatus: VerificationStatus
  verifiedBy: string | null
  verifiedAt: string | null
  issuedOn: string | null
  expiresOn: string | null
  retentionUntil: string | null
  expired: boolean
  deleted: boolean
  createdAt: string
  createdBy: string | null
}

export interface DocumentVersion {
  id: string
  versionNumber: number
  originalFilename: string
  contentType: string
  fileSize: number
  checksumSha256: string
  changeNote: string | null
  uploadedBy: string | null
  createdAt: string
}

export interface AccessGrant {
  id: string
  principalType: PrincipalType
  principalUserId: string | null
  principalName: string | null
  principalRole: string | null
  accessLevel: AccessLevel
  grantedBy: string | null
  grantedAt: string
  expiresAt: string | null
  expired: boolean
}

export interface DocumentDetail {
  document: StoredDocument
  versions: DocumentVersion[]
  access: AccessGrant[]
}

export interface AccessLevelFor {
  canView: boolean
  canEdit: boolean
  canManage: boolean
}

export interface DocumentOverview {
  totalDocuments: number
  active: number
  draft: number
  archived: number
  verified: number
  pendingVerification: number
  rejected: number
  expired: number
  expiringSoon: number
  totalBytes: number
}

/** The metadata half of an upload. The bytes travel alongside it as the `file` part. */
export interface UploadDocument {
  title: string
  description?: string | null
  documentType: string
  category?: string | null
  ownerDepartmentId?: string | null
  relatedType?: string | null
  relatedId?: string | null
  issuedOn?: string | null
  expiresOn?: string | null
  retentionUntil?: string | null
  pageCount?: number | null
  changeNote?: string | null
}

export interface NewVersion {
  changeNote?: string | null
  issuedOn?: string | null
  expiresOn?: string | null
}

export interface EditMetadata {
  title: string
  description?: string | null
  documentType: string
  category?: string | null
  ownerUserId?: string | null
  ownerDepartmentId?: string | null
  relatedType?: string | null
  relatedId?: string | null
  issuedOn?: string | null
  expiresOn?: string | null
  retentionUntil?: string | null
  status?: DocumentStatus
  pageCount?: number | null
  notes?: string | null
}

export interface GrantAccess {
  principalType: PrincipalType
  principalUserId?: string | null
  principalRole?: string | null
  accessLevel: AccessLevel
  expiresAt?: string | null
}

export interface VerifyDocument {
  approved: boolean
  note?: string | null
}

export interface DirectoryUser {
  id: string
  displayName: string
  username: string
}

export interface Department {
  id: string
  name: string
  code: string
}

export interface RoleSummary {
  id: string
  code: string
  name: string
}

export type DocumentPage = PageResponse<StoredDocument>
