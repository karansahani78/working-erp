export interface ImportBatch {
  id: string
  batchNumber: string
  importType: string
  status: string
  originalFilename: string | null
  totalRows: number
  validRows: number
  invalidRows: number
  importedRows: number
  createdAt: string
  completedAt: string | null
  /** Field name to the column the file calls it, as saved on the batch. */
  columnMapping: Record<string, string> | null
}

export interface FieldHelp {
  /** The key a mapping is stored under. */
  name: string
  label: string
  required: boolean
  kind: string
  allowed: string[] | null
}

export interface ImportTypeHelp {
  type: string
  templateHeaders: string[]
  fields: FieldHelp[]
  confirmPermission: string
}

export interface UploadResult {
  batch: ImportBatch
  headers: string[]
  fields: FieldHelp[]
  unmapped: FieldHelp[]
}

export interface RowError {
  row: number
  field: string | null
  message: string
  value: string | null
}

export interface Duplicate {
  row: number
  field: string | null
  existing: string
  incoming: string
}

export interface ValidatedRow {
  rowNumber: number
  values: Record<string, string>
  errors: RowError[]
}

export interface ValidationResult {
  batch: ImportBatch
  errors: RowError[]
  preview: ValidatedRow[]
  duplicates: Duplicate[]
}

export interface ImportReport {
  total: number
  imported: number
  skipped: number
  notes: string[]
}

/** The staged order of a batch, and what each screen in the wizard is allowed to do next. */
export const IMPORT_STEPS = ['UPLOADED', 'MAPPED', 'VALIDATED', 'COMPLETED', 'FAILED'] as const

export type ImportStep = (typeof IMPORT_STEPS)[number]