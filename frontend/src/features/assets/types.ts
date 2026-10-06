import type { PageResponse } from '../../lib/api'

export type AssetStatus = 'AVAILABLE' | 'ASSIGNED' | 'IN_MAINTENANCE' | 'LOST' | 'DISPOSED'
export type AssetCondition = 'NEW' | 'GOOD' | 'FAIR' | 'POOR'
export type HolderType = 'USER' | 'EMPLOYEE' | 'STUDENT' | 'EXTERNAL'
export type MaintenanceType = 'PREVENTIVE' | 'REPAIR' | 'CALIBRATION' | 'INSPECTION' | 'UPGRADE'
export type MaintenanceStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type DisposalMethod = 'SALE' | 'RECYCLE' | 'DONATION' | 'SCRAPPED' | 'WRITE_OFF'

export const DISPOSAL_METHODS: DisposalMethod[] = [
  'SALE',
  'RECYCLE',
  'DONATION',
  'SCRAPPED',
  'WRITE_OFF',
]

export interface AssetCategory {
  id: string
  code: string
  name: string
  depreciationRate: number | null
  usefulLifeYears: number | null
}

export interface CreateAssetCategory {
  code: string
  name: string
  depreciationRate?: number | null
  usefulLifeYears?: number | null
}

export interface Asset {
  id: string
  assetNumber: string
  name: string
  description: string | null
  categoryId: string | null
  categoryName: string | null
  serialNumber: string | null
  brand: string | null
  model: string | null
  purchaseDate: string | null
  purchaseCost: number | null
  depreciationRate: number | null
  currentValue: number | null
  warrantyExpiry: string | null
  location: string | null
  departmentId: string | null
  departmentName: string | null
  status: AssetStatus
  conditionStatus: AssetCondition
  holderUserId: string | null
  holderEmployeeId: string | null
  holderStudentId: string | null
  holderName: string | null
  assignedAt: string | null
  lostAt: string | null
  lostReason: string | null
  disposedAt: string | null
  disposalMethod: DisposalMethod | null
  disposalNotes: string | null
  disposalValue: number | null
  notes: string | null
}

export interface CreateAsset {
  name: string
  description?: string | null
  categoryId?: string | null
  serialNumber?: string | null
  brand?: string | null
  model?: string | null
  purchaseDate?: string | null
  purchaseCost?: number | null
  warrantyExpiry?: string | null
  location?: string | null
  departmentId?: string | null
  conditionStatus?: AssetCondition
  notes?: string | null
}

export interface Assignment {
  id: string
  assetId: string
  assetNumber: string
  assetName: string
  holderType: HolderType
  holderUserId: string | null
  holderEmployeeId: string | null
  holderStudentId: string | null
  holderName: string
  assignedAt: string
  assignedBy: string | null
  returnedAt: string | null
  conditionOut: AssetCondition | null
  conditionIn: AssetCondition | null
  notes: string | null
  open: boolean
}

/**
 * The API insists on a name even when a directory identifier is supplied, so the form always
 * sends one. For the three linked holder types the backend replaces it with the record's own
 * name; only an external holder keeps what was typed.
 */
export interface CreateAssignment {
  holderType: HolderType
  holderUserId?: string | null
  holderEmployeeId?: string | null
  holderStudentId?: string | null
  holderName: string
  notes?: string | null
}

export interface ReturnAsset {
  conditionIn?: AssetCondition | null
  notes?: string | null
}

/** Why the asset is missing — the reason is required, and shown to whoever goes looking. */
export interface MarkLostAsset {
  reason: string
}

export interface FoundAsset {
  location?: string | null
  conditionStatus?: AssetCondition | null
}

export interface DisposeAsset {
  method: DisposalMethod
  value?: number | null
  notes?: string | null
}

export interface MaintenanceJob {
  id: string
  assetId: string
  assetNumber: string
  assetName: string
  type: MaintenanceType
  description: string | null
  vendor: string | null
  cost: number | null
  performedBy: string | null
  scheduledFor: string | null
  startedAt: string | null
  completedAt: string | null
  status: MaintenanceStatus
}

export interface ScheduleMaintenance {
  type: MaintenanceType
  description?: string | null
  vendor?: string | null
  cost?: number | null
  performedBy?: string | null
  scheduledFor?: string | null
}

export interface CompleteMaintenance {
  cost?: number | null
  performedBy: string
  notes?: string | null
}

export interface Depreciation {
  assetId: string
  assetNumber: string
  assetName: string
  purchaseCost: number | null
  depreciationRate: number | null
  usefulLifeYears: number | null
  annualDepreciation: number | null
  currentValue: number | null
  warrantyExpiry: string | null
  status: AssetStatus
}

export interface AssetOverview {
  totalAssets: number
  available: number
  assigned: number
  inMaintenance: number
  lost: number
  disposed: number
  purchaseCost: number
  bookValue: number
  warrantyExpiringSoon: number
}

export interface AssetDetail {
  asset: Asset
  history: Assignment[]
  maintenance: MaintenanceJob[]
}

export interface DirectoryStudent {
  id: string
  fullName: string
  studentNumber: string
}

export interface DirectoryEmployee {
  id: string
  fullName: string
  employeeCode: string
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

export type AssetPage = PageResponse<Asset>