export interface AuditEvent {
  id: string
  actorId: string | null
  actorUsername: string | null
  action: string
  entityType: string
  entityId: string | null
  entityLabel: string | null
  summary: string | null
  beforeState: string | null
  afterState: string | null
  module: string | null
  succeeded: boolean
  failureReason: string | null
  ipAddress: string | null
  requestId: string | null
  occurredAt: string
}

export interface AuditActionCount {
  action: string
  occurrences: number
}

export interface AuditFilter {
  term?: string
  action?: string
  entityType?: string
  from?: string
  to?: string
}