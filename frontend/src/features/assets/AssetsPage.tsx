import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useFocusTrap } from '../../lib/useFocusTrap'
import { api, patch, post, type PageResponse } from '../../lib/api'
import { date, money, num, text } from '../../lib/format'
import { Button, Field, Panel, QueryBoundary, Select, StatusBadge, TextInput, humanise } from '../../components/ui'
import { Pager, PageHeader } from '../../components/DataTable'
import { useAuth } from '../../auth/AuthProvider'
import {
  DISPOSAL_METHODS,
  type Asset,
  type AssetCategory,
  type AssetCondition,
  type AssetDetail,
  type AssetOverview,
  type AssetStatus,
  type Assignment,
  type CreateAsset,
  type CreateAssetCategory,
  type CreateAssignment,
  type Depreciation,
  type DisposeAsset,
  type FoundAsset,
  type MarkLostAsset,
  type DirectoryEmployee,
  type DirectoryStudent,
  type DirectoryUser,
  type DisposalMethod,
  type HolderType,
  type MaintenanceJob,
  type MaintenanceStatus,
  type MaintenanceType,
  type ReturnAsset,
  type ScheduleMaintenance,
} from './types'

const PAGE_SIZE = 20

const ASSET_STATUSES: AssetStatus[] = [
  'AVAILABLE',
  'ASSIGNED',
  'IN_MAINTENANCE',
  'LOST',
  'DISPOSED',
]
const CONDITIONS: AssetCondition[] = ['NEW', 'GOOD', 'FAIR', 'POOR']
const MAINTENANCE_TYPES: MaintenanceType[] = [
  'PREVENTIVE',
  'REPAIR',
  'CALIBRATION',
  'INSPECTION',
  'UPGRADE',
]
const MAINTENANCE_STATUSES: MaintenanceStatus[] = [
  'SCHEDULED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
]
const HOLDER_TYPES: HolderType[] = ['EMPLOYEE', 'USER', 'STUDENT', 'EXTERNAL']

/** How a condition grade reads on a register, worst last. */
const CONDITION_TONE: Record<AssetCondition, string> = {
  NEW: 'text-success',
  GOOD: 'text-ink',
  FAIR: 'text-warning',
  POOR: 'text-danger',
}

const TH = 'px-4 py-3 font-medium text-ink-subtle'
const TDR = 'px-4 py-3'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

function say(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

/* ---------------------------------------------------------------------- shared */

function useCategories() {
  return useQuery({
    queryKey: ['assets', 'categories'],
    queryFn: () => api<AssetCategory[]>('/api/v1/assets/categories'),
  })
}

/** Every asset query hangs off one key, so a write can refresh all of it at once. */
function useRefreshAssets() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['assets'] })
}

/* ------------------------------------------------------------------- the page */

export default function AssetsPage() {
  const { can } = useAuth()
  const mayManage = can('ASSET_MANAGE')
  const [tab, setTab] = useState<'register' | 'custody' | 'maintenance' | 'value' | 'setup'>(
    'register',
  )
  const [selected, setSelected] = useState<string | null>(null)

  const overview = useQuery({
    queryKey: ['assets', 'overview'],
    queryFn: () => api<AssetOverview>('/api/v1/assets/overview'),
  })

  const tabs = [
    { key: 'register' as const, label: 'Register' },
    { key: 'custody' as const, label: 'Custody' },
    { key: 'maintenance' as const, label: 'Maintenance' },
    { key: 'value' as const, label: 'Depreciation' },
    ...(mayManage ? [{ key: 'setup' as const, label: 'Categories' }] : []),
  ]

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Assets"
        description="Everything the institution owns, what it is worth, and who has it."
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Assets" value={overview.data ? num(overview.data.totalAssets) : null} />
        <Stat label="Purchase cost" value={overview.data ? money(overview.data.purchaseCost) : null} />
        <Stat label="Book value" value={overview.data ? money(overview.data.bookValue) : null} />
        <Stat
          label="Out on loan"
          value={overview.data ? `${num(overview.data.assigned)} of ${num(overview.data.totalAssets)}` : null}
        />
      </div>

      <div role="tablist" aria-label="Asset views" className="flex flex-wrap gap-1 border-b border-border">
        {tabs.map((entry) => (
          <button
            key={entry.key}
            role="tab"
            type="button"
            aria-selected={tab === entry.key}
            onClick={() => setTab(entry.key)}
            className={
              tab === entry.key
                ? 'border-b-2 border-primary px-4 py-2 text-sm font-medium text-primary'
                : 'border-b-2 border-transparent px-4 py-2 text-sm text-ink-muted hover:text-ink'
            }
          >
            {entry.label}
          </button>
        ))}
      </div>

      {tab === 'register' && <Register onOpen={setSelected} mayManage={mayManage} />}
      {tab === 'custody' && <Custody onOpen={setSelected} mayManage={mayManage} />}
      {tab === 'maintenance' && <Maintenance onOpen={setSelected} mayManage={mayManage} />}
      {tab === 'value' && <Depreciation />}
      {tab === 'setup' && mayManage && <AssetCategories />}

      {selected && (
        <AssetDrawer assetId={selected} onClose={() => setSelected(null)} mayManage={mayManage} />
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string | null }) {
  return (
    <Panel className="px-5 py-4">
      <p className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-ink">{value ?? '—'}</p>
    </Panel>
  )
}

/* ------------------------------------------------------------------- register */

function Register({ onOpen, mayManage }: { onOpen: (id: string) => void; mayManage: boolean }) {
  const categories = useCategories()
  const refresh = useRefreshAssets()
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [status, setStatus] = useState('')
  const [creating, setCreating] = useState(false)

  const assets = useQuery({
    queryKey: ['assets', 'list', page, search, categoryId, status],
    queryFn: () =>
      api<PageResponse<Asset>>('/api/v1/assets', {
        query: {
          page,
          size: PAGE_SIZE,
          sort: 'name,asc',
          term: search || undefined,
          categoryId: categoryId || undefined,
          status: status || undefined,
        },
      }),
  })

  const filtered = Boolean(search || categoryId || status)

  return (
    <Panel>
      <div className="flex flex-wrap items-end gap-3 border-b border-border p-4">
        {/* Field copies its id onto its child, so the input is the child and the form
            wraps them both. Nesting the form inside Field would stamp the id twice. */}
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setSearch(term.trim())
          }}
        >
          <Field label="Search" htmlFor="asset-term">
            <TextInput
              id="asset-term"
              value={term}
              placeholder="Name, number or serial"
              onChange={(event) => setTerm(event.target.value)}
            />
          </Field>
          <Button type="submit" variant="secondary">Search</Button>
        </form>
        <Field label="Category" htmlFor="asset-category">
          <Select
            id="asset-category"
            value={categoryId}
            onChange={(event) => {
              setCategoryId(event.target.value)
              setPage(0)
            }}
          >
            <option value="">Every category</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </Select>
        </Field>
        <Field label="Status" htmlFor="asset-status">
          <Select
            id="asset-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(0)
            }}
          >
            <option value="">Any status</option>
            {ASSET_STATUSES.map((value) => (
              <option key={value} value={value}>{value.replace('_', ' ').toLowerCase()}</option>
            ))}
          </Select>
        </Field>
        {mayManage && (
          <Button onClick={() => setCreating((open) => !open)}>
            {creating ? 'Close form' : 'New asset'}
          </Button>
        )}
      </div>

      <QueryBoundary
        isLoading={assets.isLoading}
        error={assets.error}
        data={assets.data}
        onRetry={() => void assets.refetch()}
        loadingRows={5}
        isEmpty={(data) => data.data.length === 0}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">
              {filtered
                ? 'No asset matches those filters.'
                : 'The register is empty. Record the first asset to get started.'}
            </p>
          </div>
        }
      >
        {(data) => (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className={TH}>Asset</th>
                  <th className={`${TH} hidden md:table-cell`}>Category</th>
                  <th className={`${TH} hidden lg:table-cell`}>Condition</th>
                  <th className={`${TH} text-right`}>Value</th>
                  <th className={`${TH} hidden lg:table-cell`}>Location</th>
                  <th className={`${TH} hidden md:table-cell`}>Holder</th>
                  <th className={TH}>Status</th>
                  <th className={`${TH} text-right`}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.data.map((row) => (
                  <tr key={row.id} className="border-b border-border last:border-0">
                    <td className={TDR}>
                      <p className="font-medium text-ink">{row.name}</p>
                      <p className="text-xs text-ink-muted">
                        {row.assetNumber}
                        {row.serialNumber ? ` · ${row.serialNumber}` : ''}
                      </p>
                    </td>
                    <td className={`${TDR} hidden md:table-cell text-ink`}>{text(row.categoryName)}</td>
                    <td className={`${TDR} hidden lg:table-cell ${CONDITION_TONE[row.conditionStatus]}`}>
                      {row.conditionStatus}
                    </td>
                    <td className={`${TDR} nums text-right`}>{money(row.currentValue)}</td>
                    <td className={`${TDR} hidden lg:table-cell text-ink`}>{text(row.location)}</td>
                    <td className={`${TDR} hidden md:table-cell`}>
                      {row.holderName ? (
                        <div>
                          <p className="text-ink">{row.holderName}</p>
                          <p className="text-xs text-ink-muted">{date(row.assignedAt)}</p>
                        </div>
                      ) : (
                        <span className="text-ink-muted">—</span>
                      )}
                    </td>
                    <td className={TDR}><StatusBadge status={row.status} /></td>
                    <td className={`${TDR} text-right`}>
                      <Button size="sm" variant="ghost" onClick={() => onOpen(row.id)}>Open</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </QueryBoundary>

      <Pager
        page={assets.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }}
        onChange={setPage}
      />

      {creating && mayManage && (
        <AssetForm
          categories={categories.data ?? []}
          onDone={() => {
            setCreating(false)
            refresh()
          }}
        />
      )}
    </Panel>
  )
}

function AssetForm({ categories, onDone }: { categories: AssetCategory[]; onDone: () => void }) {
  const [values, setValues] = useState<CreateAsset>({ name: '', conditionStatus: 'NEW' })
  const set = <K extends keyof CreateAsset>(key: K, value: CreateAsset[K]) =>
    setValues((current) => ({ ...current, [key]: value }))

  const save = useMutation({
    mutationFn: (body: CreateAsset) => post<Asset>('/api/v1/assets', body),
    onSuccess: onDone,
  })

  return (
    <form
      className="grid gap-4 border-t border-border p-5 lg:grid-cols-3"
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate({
          ...values,
          purchaseCost:
            values.purchaseCost === null || values.purchaseCost === undefined
              ? null
              : Number(values.purchaseCost),
        })
      }}
    >
      <Field label="Name" htmlFor="asset-name" required>
        <TextInput id="asset-name" required value={values.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Category" htmlFor="asset-new-category">
        <Select
          id="asset-new-category"
          value={values.categoryId ?? ''}
          onChange={(e) => set('categoryId', e.target.value || null)}
        >
          <option value="">Uncategorised</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </Select>
      </Field>
      <Field label="Serial number" htmlFor="asset-serial">
        <TextInput id="asset-serial" value={values.serialNumber ?? ''} onChange={(e) => set('serialNumber', e.target.value || null)} />
      </Field>
      <Field label="Brand" htmlFor="asset-brand">
        <TextInput id="asset-brand" value={values.brand ?? ''} onChange={(e) => set('brand', e.target.value || null)} />
      </Field>
      <Field label="Model" htmlFor="asset-model">
        <TextInput id="asset-model" value={values.model ?? ''} onChange={(e) => set('model', e.target.value || null)} />
      </Field>
      <Field label="Condition" htmlFor="asset-condition">
        <Select
          id="asset-condition"
          value={values.conditionStatus ?? 'NEW'}
          onChange={(e) => set('conditionStatus', e.target.value as AssetCondition)}
        >
          {CONDITIONS.map((value) => <option key={value} value={value}>{value}</option>)}
        </Select>
      </Field>
      <Field label="Purchase date" htmlFor="asset-purchased">
        <TextInput id="asset-purchased" type="date" value={values.purchaseDate ?? ''} onChange={(e) => set('purchaseDate', e.target.value || null)} />
      </Field>
      <Field label="Purchase cost" htmlFor="asset-cost">
        <TextInput id="asset-cost" type="number" step="0.01" min="0" value={values.purchaseCost ?? ''} onChange={(e) => set('purchaseCost', e.target.value === '' ? null : Number(e.target.value))} />
      </Field>
      <Field label="Warranty expiry" htmlFor="asset-warranty">
        <TextInput id="asset-warranty" type="date" value={values.warrantyExpiry ?? ''} onChange={(e) => set('warrantyExpiry', e.target.value || null)} />
      </Field>
      <Field label="Location" htmlFor="asset-location">
        <TextInput id="asset-location" value={values.location ?? ''} onChange={(e) => set('location', e.target.value || null)} />
      </Field>
      <Field label="Notes" htmlFor="asset-notes">
        <TextInput id="asset-notes" value={values.notes ?? ''} onChange={(e) => set('notes', e.target.value || null)} />
      </Field>
      <div className="flex items-end gap-3 lg:col-span-3">
        <Button type="submit" loading={save.isPending}>Record asset</Button>
        <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
        {save.isError && (
          <p role="alert" className="text-sm text-danger">{say(save.error)}</p>
        )}
      </div>
    </form>
  )
}

/* -------------------------------------------------------------------- custody */

function Custody({ onOpen, mayManage }: { onOpen: (id: string) => void; mayManage: boolean }) {
  const refresh = useRefreshAssets()
  const [page, setPage] = useState(0)
  const [holderType, setHolderType] = useState<HolderType>('EMPLOYEE')
  const [assigning, setAssigning] = useState(false)
  const [selectedAsset, setSelectedAsset] = useState('')
  const [personId, setPersonId] = useState('')
  const [externalName, setExternalName] = useState('')
  const [notes, setNotes] = useState('')
  const [feedback, setFeedback] = useState('')

  const available = useQuery({
    queryKey: ['assets', 'available'],
    queryFn: () =>
      api<PageResponse<Asset>>('/api/v1/assets', {
        query: { status: 'AVAILABLE', size: 200, sort: 'name,asc' },
      }),
  })

  // Each directory sits behind its own permission, so each is asked for on its own terms
  // and only when the chosen holder type actually needs it.
  const students = useQuery({
    queryKey: ['assets', 'directory', 'students'],
    queryFn: () =>
      api<PageResponse<DirectoryStudent>>('/api/v1/students', {
        query: { size: 200, sort: 'fullName,asc' },
      }),
    enabled: holderType === 'STUDENT',
  })
  const employees = useQuery({
    queryKey: ['assets', 'directory', 'employees'],
    queryFn: () => api<DirectoryEmployee[]>('/api/v1/hr/employees'),
    enabled: holderType === 'EMPLOYEE',
  })
  const users = useQuery({
    queryKey: ['assets', 'directory', 'users'],
    queryFn: () =>
      api<PageResponse<DirectoryUser>>('/api/v1/admin/users', {
        query: { size: 200, sort: 'displayName,asc' },
      }),
    enabled: holderType === 'USER',
    // Listing accounts needs USER_READ, which asset management does not imply. When it is
    // missing, say so and let the register answer the question instead.
    retry: false,
  })

  const held = useQuery({
    queryKey: ['assets', 'held', holderType, personId, page],
    queryFn: () => {
      const query: Record<string, string | number | undefined> = { holderType, page, size: PAGE_SIZE }
      if (holderType === 'STUDENT' && personId) query.holderStudentId = personId
      if (holderType === 'EMPLOYEE' && personId) query.holderEmployeeId = personId
      if (holderType === 'USER' && personId) query.holderUserId = personId
      return api<PageResponse<Asset>>('/api/v1/assets/held', { query })
    },
  })

  const external = holderType === 'EXTERNAL'
  const options =
    holderType === 'STUDENT'
      ? (students.data?.data ?? []).map((row) => ({
          id: row.id,
          label: `${row.fullName} (${row.studentNumber})`,
        }))
      : holderType === 'EMPLOYEE'
        ? (employees.data ?? []).map((row) => ({
            id: row.id,
            label: `${row.fullName} (${row.employeeCode})`,
          }))
        : holderType === 'USER'
          ? (users.data?.data ?? []).map((row) => ({ id: row.id, label: row.displayName }))
          : []

  const assign = useMutation({
    mutationFn: (body: CreateAssignment) =>
      post<Assignment>(`/api/v1/assets/${selectedAsset}/assignments`, body),
    onSuccess: () => {
      setFeedback('Asset handed over.')
      setAssigning(false)
      setSelectedAsset('')
      setPersonId('')
      setExternalName('')
      setNotes('')
      refresh()
    },
  })

  return (
    <Panel>
      <div className="grid gap-4 border-b border-border p-5 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Holder type" htmlFor="custody-type" required>
          <Select
            id="custody-type"
            value={holderType}
            onChange={(event) => {
              setHolderType(event.target.value as HolderType)
              setPersonId('')
              setPage(0)
            }}
          >
            {HOLDER_TYPES.map((value) => <option key={value} value={value}>{value}</option>)}
          </Select>
        </Field>
        <Field label="Person" htmlFor="custody-person" hint="Leave empty for everybody of this type.">
          {external ? (
            <TextInput
              id="custody-person"
              value={externalName}
              placeholder="Not in the directory"
              onChange={(event) => setExternalName(event.target.value)}
            />
          ) : (
            <Select
              id="custody-person"
              value={personId}
              onChange={(event) => {
                setPersonId(event.target.value)
                setPage(0)
              }}
            >
              <option value="">Everybody</option>
              {options.map((option) => (
                <option key={option.id} value={option.id}>{option.label}</option>
              ))}
            </Select>
          )}
        </Field>
        {holderType === 'USER' && users.isError && (
          <p className="self-end text-sm text-ink-muted sm:col-span-2">
            Accounts cannot be listed with your permissions. Search the register instead.
          </p>
        )}
        {mayManage && (
          <div className="flex items-end">
            <Button onClick={() => setAssigning((open) => !open)}>
              {assigning ? 'Close handover' : 'Hand an asset over'}
            </Button>
          </div>
        )}
      </div>

      {assigning && mayManage && (
        <form
          className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
          onSubmit={(event) => {
            event.preventDefault()
            const chosen = options.find((option) => option.id === personId)
            assign.mutate({
              holderType,
              holderUserId: holderType === 'USER' && personId ? personId : null,
              holderEmployeeId: holderType === 'EMPLOYEE' && personId ? personId : null,
              holderStudentId: holderType === 'STUDENT' && personId ? personId : null,
              holderName: external ? externalName.trim() : (chosen?.label ?? ''),
              notes: notes.trim() || null,
            })
          }}
        >
          <Field label="Asset" htmlFor="assign-asset" required>
            <Select
              id="assign-asset"
              value={selectedAsset}
              onChange={(event) => setSelectedAsset(event.target.value)}
            >
              <option value="">Choose an available asset</option>
              {(available.data?.data ?? []).map((row) => (
                <option key={row.id} value={row.id}>{row.name} ({row.assetNumber})</option>
              ))}
            </Select>
          </Field>
          <Field label={external ? 'Their name' : 'Person'} htmlFor="assign-holder" required>
            {external ? (
              <TextInput
                id="assign-holder"
                required
                value={externalName}
                onChange={(event) => setExternalName(event.target.value)}
              />
            ) : (
              <Select
                id="assign-holder"
                required
                value={personId}
                onChange={(event) => setPersonId(event.target.value)}
              >
                <option value="">Choose someone</option>
                {options.map((option) => (
                  <option key={option.id} value={option.id}>{option.label}</option>
                ))}
              </Select>
            )}
          </Field>
          <Field label="Notes" htmlFor="assign-notes">
            <TextInput id="assign-notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
          </Field>
          <div className="flex items-center gap-3 sm:col-span-3">
            <Button type="submit" loading={assign.isPending} disabled={!selectedAsset}>
              Hand over
            </Button>
            {feedback && <span className="text-sm text-success">{feedback}</span>}
            {assign.isError && (
              <span role="alert" className="text-sm text-danger">{say(assign.error)}</span>
            )}
          </div>
        </form>
      )}

      <QueryBoundary
        isLoading={held.isLoading}
        error={held.error}
        data={held.data}
        onRetry={() => void held.refetch()}
        loadingRows={4}
        isEmpty={(data) => data.data.length === 0}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">Nobody of this type is holding an asset.</p>
          </div>
        }
      >
        {(data) => (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className={TH}>Asset</th>
                  <th className={TH}>Held by</th>
                  <th className={`${TH} hidden sm:table-cell`}>Since</th>
                  <th className={`${TH} hidden md:table-cell`}>Condition</th>
                  <th className={`${TH} text-right`}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.data.map((row) => (
                  <tr key={row.id} className="border-b border-border last:border-0">
                    <td className={TDR}>
                      <p className="font-medium text-ink">{row.name}</p>
                      <p className="text-xs text-ink-muted">{row.assetNumber}</p>
                    </td>
                    <td className={`${TDR} text-ink`}>{text(row.holderName)}</td>
                    <td className={`${TDR} hidden sm:table-cell whitespace-nowrap text-ink-muted`}>
                      {date(row.assignedAt)}
                    </td>
                    <td className={`${TDR} hidden md:table-cell ${CONDITION_TONE[row.conditionStatus]}`}>
                      {row.conditionStatus}
                    </td>
                    <td className={`${TDR} text-right`}>
                      <Button size="sm" variant="ghost" onClick={() => onOpen(row.id)}>Open</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </QueryBoundary>

      <Pager
        page={held.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }}
        onChange={setPage}
      />
    </Panel>
  )
}

/* ---------------------------------------------------------------- maintenance */

function Maintenance({
  onOpen,
  mayManage,
}: {
  onOpen: (id: string) => void
  mayManage: boolean
}) {
  const refresh = useRefreshAssets()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [feedback, setFeedback] = useState('')
  const [worker, setWorker] = useState('')

  const jobs = useQuery({
    queryKey: ['assets', 'jobs', page, status],
    queryFn: () =>
      api<PageResponse<MaintenanceJob>>('/api/v1/assets/maintenance', {
        query: { page, size: PAGE_SIZE, status: status || undefined },
      }),
  })

  const start = useMutation({
    mutationFn: (jobId: string) =>
      patch<MaintenanceJob>(`/api/v1/assets/maintenance/${jobId}/start`, {
        performedBy: worker.trim() || 'Rajesh Karki',
      }),
    onSuccess: () => {
      setFeedback('Work started.')
      refresh()
    },
  })
  const complete = useMutation({
    mutationFn: (jobId: string) =>
      patch<MaintenanceJob>(`/api/v1/assets/maintenance/${jobId}/complete`, {
        performedBy: worker.trim() || 'Rajesh Karki',
      }),
    onSuccess: () => {
      setFeedback('Job closed.')
      refresh()
    },
  })
  const cancel = useMutation({
    mutationFn: (jobId: string) =>
      patch<MaintenanceJob>(`/api/v1/assets/maintenance/${jobId}/cancel`),
    onSuccess: () => {
      setFeedback('Job cancelled.')
      refresh()
    },
  })

  const busy = start.isPending || complete.isPending || cancel.isPending
  const problem = start.error ?? complete.error ?? cancel.error

  return (
    <Panel>
      <div className="flex flex-wrap items-end gap-3 border-b border-border p-4">
        <Field label="Status" htmlFor="job-status">
          <Select
            id="job-status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(0)
            }}
          >
            <option value="">Any status</option>
            {MAINTENANCE_STATUSES.map((value) => (
              <option key={value} value={value}>{value.replace('_', ' ').toLowerCase()}</option>
            ))}
          </Select>
        </Field>
        {mayManage && (
          <Field label="Worked on by" htmlFor="job-worker">
            <TextInput
              id="job-worker"
              value={worker}
              placeholder="Rajesh Karki"
              onChange={(event) => setWorker(event.target.value)}
            />
          </Field>
        )}
        {feedback && <span className="pb-3 text-sm text-success">{feedback}</span>}
        {problem && (
          <span role="alert" className="pb-3 text-sm text-danger">{say(problem)}</span>
        )}
      </div>

      <QueryBoundary
        isLoading={jobs.isLoading}
        error={jobs.error}
        data={jobs.data}
        onRetry={() => void jobs.refetch()}
        loadingRows={4}
        isEmpty={(data) => data.data.length === 0}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">No servicing has been booked.</p>
          </div>
        }
      >
        {(data) => (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className={TH}>Asset</th>
                  <th className={TH}>Work</th>
                  <th className={`${TH} hidden lg:table-cell`}>Vendor</th>
                  <th className={`${TH} hidden sm:table-cell`}>Booked for</th>
                  <th className={`${TH} hidden md:table-cell text-right`}>Cost</th>
                  <th className={TH}>Status</th>
                  <th className={`${TH} text-right`}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.data.map((row) => (
                  <tr key={row.id} className="border-b border-border last:border-0">
                    <td className={TDR}>
                      <p className="font-medium text-ink">{row.assetName}</p>
                      <p className="text-xs text-ink-muted">{row.assetNumber}</p>
                    </td>
                    <td className={TDR}>
                      <p className="text-ink">
                        {row.type.charAt(0) + row.type.slice(1).toLowerCase()}
                      </p>
                      {row.description && (
                        <p className="text-xs text-ink-muted">{row.description}</p>
                      )}
                    </td>
                    <td className={`${TDR} hidden lg:table-cell text-ink`}>{text(row.vendor)}</td>
                    <td className={`${TDR} hidden sm:table-cell whitespace-nowrap text-ink-muted`}>
                      {date(row.scheduledFor)}
                    </td>
                    <td className={`${TDR} nums hidden md:table-cell text-right`}>{money(row.cost)}</td>
                    <td className={TDR}><StatusBadge status={row.status} /></td>
                    <td className={`${TDR} text-right whitespace-nowrap`}>
                      {row.status === 'SCHEDULED' && mayManage && (
                        <Button size="sm" variant="secondary" disabled={busy} onClick={() => start.mutate(row.id)}>
                          Start
                        </Button>
                      )}
                      {row.status === 'IN_PROGRESS' && mayManage && (
                        <Button size="sm" variant="secondary" disabled={busy} onClick={() => complete.mutate(row.id)}>
                          Complete
                        </Button>
                      )}
                      {(row.status === 'SCHEDULED' || row.status === 'IN_PROGRESS') && mayManage && (
                        <Button size="sm" variant="ghost" disabled={busy} onClick={() => cancel.mutate(row.id)}>
                          Cancel
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => onOpen(row.assetId)}>Open</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </QueryBoundary>

      <Pager
        page={jobs.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }}
        onChange={setPage}
      />
    </Panel>
  )
}

/* --------------------------------------------------------------- depreciation */

function Depreciation() {
  const rows = useQuery({
    queryKey: ['assets', 'depreciation'],
    queryFn: () => api<Depreciation[]>('/api/v1/assets/depreciation'),
  })

  const totals = useMemo(() => {
    const list = rows.data ?? []
    const cutoff = today()
    return {
      cost: list.reduce((sum, row) => sum + (row.purchaseCost ?? 0), 0),
      value: list.reduce((sum, row) => sum + (row.currentValue ?? 0), 0),
      outOfWarranty: list.filter(
        (row) => row.warrantyExpiry && row.warrantyExpiry < cutoff,
      ).length,
    }
  }, [rows.data])

  return (
    <Panel>
      <div className="grid gap-4 border-b border-border p-5 sm:grid-cols-3">
        <Stat label="Purchase cost" value={money(totals.cost)} />
        <Stat label="Book value" value={money(totals.value)} />
        <Stat label="Out of warranty" value={num(totals.outOfWarranty)} />
      </div>

      <QueryBoundary
        isLoading={rows.isLoading}
        error={rows.error}
        data={rows.data}
        onRetry={() => void rows.refetch()}
        loadingRows={4}
        empty={
          <div className="p-6">
            <p className="text-sm text-ink-muted">No assets to depreciate yet.</p>
          </div>
        }
      >
        {(list) => (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className={TH}>Asset</th>
                  <th className={`${TH} text-right`}>Cost</th>
                  <th className={`${TH} text-right`}>Rate</th>
                  <th className={`${TH} hidden sm:table-cell text-right`}>Life</th>
                  <th className={`${TH} hidden md:table-cell text-right`}>Per year</th>
                  <th className={`${TH} text-right`}>Worth now</th>
                  <th className={`${TH} hidden lg:table-cell`}>Warranty</th>
                  <th className={TH}>Status</th>
                </tr>
              </thead>
              <tbody>
                {list.map((row) => (
                  <tr key={row.assetId} className="border-b border-border last:border-0">
                    <td className={TDR}>
                      <p className="font-medium text-ink">{row.assetName}</p>
                      <p className="text-xs text-ink-muted">{row.assetNumber}</p>
                    </td>
                    <td className={`${TDR} nums text-right`}>{money(row.purchaseCost)}</td>
                    <td className={`${TDR} nums text-right`}>
                      {row.depreciationRate ? `${(row.depreciationRate * 100).toFixed(1)}%` : '—'}
                    </td>
                    <td className={`${TDR} nums hidden sm:table-cell text-right`}>
                      {row.usefulLifeYears ? `${row.usefulLifeYears} yr` : '—'}
                    </td>
                    <td className={`${TDR} nums hidden md:table-cell text-right`}>
                      {money(row.annualDepreciation)}
                    </td>
                    <td className={`${TDR} nums text-right font-medium`}>{money(row.currentValue)}</td>
                    <td className={`${TDR} hidden lg:table-cell`}>
                      {row.warrantyExpiry && row.warrantyExpiry < today() ? (
                        <span className="text-danger">Expired {date(row.warrantyExpiry)}</span>
                      ) : (
                        <span className="text-ink-muted">
                          {row.warrantyExpiry ? date(row.warrantyExpiry) : '—'}
                        </span>
                      )}
                    </td>
                    <td className={TDR}><StatusBadge status={row.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </QueryBoundary>
    </Panel>
  )
}

/* --------------------------------------------------------------- the drawer */

function AssetDrawer({
  assetId,
  onClose,
  mayManage,
}: {
  assetId: string
  onClose: () => void
  mayManage: boolean
}) {
  const panel = useRef<HTMLDivElement>(null)
  useFocusTrap(panel, true, onClose)

  const detail = useQuery({
    queryKey: ['assets', 'detail', assetId],
    queryFn: () => api<AssetDetail>(`/api/v1/assets/${assetId}`),
  })

  const [conditionIn, setConditionIn] = useState<AssetCondition>('GOOD')
  const [returnNotes, setReturnNotes] = useState('')
  const [feedback, setFeedback] = useState('')

  const returnAsset = useMutation({
    mutationFn: (body: ReturnAsset) =>
      patch<Assignment>(`/api/v1/assets/${assetId}/return`, body),
    onSuccess: () => {
      setFeedback('Taken back.')
      setReturnNotes('')
      void detail.refetch()
    },
  })

  const [jobType, setJobType] = useState<MaintenanceType>('PREVENTIVE')
  const [jobDescription, setJobDescription] = useState('')
  const [jobVendor, setJobVendor] = useState('')
  const [jobDate, setJobDate] = useState(today())

  const schedule = useMutation({
    mutationFn: (body: ScheduleMaintenance) =>
      post<MaintenanceJob>(`/api/v1/assets/${assetId}/maintenance`, body),
    onSuccess: () => {
      setFeedback('Servicing booked.')
      setJobDescription('')
      void detail.refetch()
    },
  })

  const [lostReason, setLostReason] = useState('')
  const markLost = useMutation({
    mutationFn: (body: MarkLostAsset) => patch<Asset>(`/api/v1/assets/${assetId}/lost`, body),
    onSuccess: () => {
      setFeedback('Reported missing.')
      setLostReason('')
      void detail.refetch()
    },
  })

  const [foundLocation, setFoundLocation] = useState('')
  const [foundCondition, setFoundCondition] = useState<AssetCondition>('GOOD')
  const markFound = useMutation({
    mutationFn: (body: FoundAsset) => patch<Asset>(`/api/v1/assets/${assetId}/found`, body),
    onSuccess: () => {
      setFeedback('Back on the shelf.')
      setFoundLocation('')
      void detail.refetch()
    },
  })

  const [disposalMethod, setDisposalMethod] = useState<DisposalMethod>('WRITE_OFF')
  const [disposalValue, setDisposalValue] = useState('')
  const [disposalNotes, setDisposalNotes] = useState('')
  const dispose = useMutation({
    mutationFn: (body: DisposeAsset) => patch<Asset>(`/api/v1/assets/${assetId}/dispose`, body),
    onSuccess: () => {
      setFeedback('Written off the register.')
      setDisposalValue('')
      setDisposalNotes('')
      void detail.refetch()
    },
  })

  const asset = detail.data?.asset
  const open = detail.data?.history.find((entry) => entry.open)

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-ink/40" role="dialog" aria-modal="true">
      <div
        ref={panel}
        tabIndex={-1}
        className="flex h-full w-full max-w-2xl flex-col overflow-y-auto bg-surface shadow-xl"
      >
        <div className="flex items-start justify-between border-b border-border p-5">
          <div>
            <h2 className="text-lg font-semibold text-ink">{asset?.name ?? 'Asset'}</h2>
            <p className="text-sm text-ink-muted">
              {asset?.assetNumber}
              {asset?.serialNumber ? ` · ${asset.serialNumber}` : ''}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose}>Close</Button>
        </div>

        <QueryBoundary
          isLoading={detail.isLoading}
          error={detail.error}
          data={detail.data}
          onRetry={() => void detail.refetch()}
        >
          {(data) => (
            <>
              <dl className="grid grid-cols-2 gap-4 border-b border-border p-5 sm:grid-cols-3">
                <Detail label="Category" value={text(data.asset.categoryName)} />
                <Detail label="Status" value={<StatusBadge status={data.asset.status} />} />
                <Detail label="Condition" value={data.asset.conditionStatus} />
                <Detail label="Cost" value={money(data.asset.purchaseCost)} />
                <Detail label="Worth now" value={money(data.asset.currentValue)} />
                <Detail label="Location" value={text(data.asset.location)} />
                <Detail
                  label="Warranty"
                  value={text(data.asset.warrantyExpiry ? date(data.asset.warrantyExpiry) : null)}
                />
                <Detail label="Holder" value={text(data.asset.holderName)} />
                <Detail
                  label="Since"
                  value={text(data.asset.assignedAt ? date(data.asset.assignedAt) : null)}
                />
                {data.asset.lostAt && (
                  <>
                    <Detail label="Missing since" value={date(data.asset.lostAt)} />
                    <Detail label="What happened" value={text(data.asset.lostReason)} />
                  </>
                )}
                {data.asset.disposedAt && (
                  <>
                    <Detail label="Written off" value={date(data.asset.disposedAt)} />
                    <Detail
                      label="How it went"
                      value={data.asset.disposalMethod
                        ? humanise(data.asset.disposalMethod)
                        : '—'}
                    />
                    <Detail label="Gone for" value={money(data.asset.disposalValue)} />
                    <Detail label="On" value={text(data.asset.disposalNotes)} />
                  </>
                )}
              </dl>

              {mayManage && data.asset.status === 'ASSIGNED' && open && (
                <form
                  className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    returnAsset.mutate({ conditionIn, notes: returnNotes.trim() || null })
                  }}
                >
                  <h3 className="text-sm font-semibold text-ink sm:col-span-3">
                    Take it back from {open.holderName}
                  </h3>
                  <Field label="Condition on return" htmlFor="return-condition">
                    <Select
                      id="return-condition"
                      value={conditionIn}
                      onChange={(event) => setConditionIn(event.target.value as AssetCondition)}
                    >
                      {CONDITIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                    </Select>
                  </Field>
                  <Field label="Notes" htmlFor="return-notes">
                    <TextInput
                      id="return-notes"
                      value={returnNotes}
                      onChange={(event) => setReturnNotes(event.target.value)}
                    />
                  </Field>
                  <div className="flex items-end">
                    <Button type="submit" loading={returnAsset.isPending}>Record return</Button>
                  </div>
                </form>
              )}

              {mayManage && data.asset.status === 'AVAILABLE' && (
                <form
                  className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    schedule.mutate({
                      type: jobType,
                      description: jobDescription.trim() || null,
                      vendor: jobVendor.trim() || null,
                      scheduledFor: jobDate || null,
                    })
                  }}
                >
                  <h3 className="text-sm font-semibold text-ink sm:col-span-3">Book servicing</h3>
                  <Field label="Kind of work" htmlFor="job-type">
                    <Select
                      id="job-type"
                      value={jobType}
                      onChange={(event) => setJobType(event.target.value as MaintenanceType)}
                    >
                      {MAINTENANCE_TYPES.map((value) => (
                        <option key={value} value={value}>{value}</option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Vendor" htmlFor="job-vendor">
                    <TextInput
                      id="job-vendor"
                      value={jobVendor}
                      onChange={(event) => setJobVendor(event.target.value)}
                    />
                  </Field>
                  <Field label="Booked for" htmlFor="job-date">
                    <TextInput
                      id="job-date"
                      type="date"
                      value={jobDate}
                      onChange={(event) => setJobDate(event.target.value)}
                    />
                  </Field>
                  <Field label="What is wrong" htmlFor="job-description">
                    <TextInput
                      id="job-description"
                      value={jobDescription}
                      onChange={(event) => setJobDescription(event.target.value)}
                    />
                  </Field>
                  <div className="flex items-end">
                    <Button type="submit" loading={schedule.isPending}>Book it</Button>
                  </div>
                </form>
              )}

              {mayManage &&
                (data.asset.status === 'AVAILABLE' || data.asset.status === 'ASSIGNED') && (
                  <form
                    className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
                    onSubmit={(event) => {
                      event.preventDefault()
                      markLost.mutate({ reason: lostReason.trim() })
                    }}
                  >
                    <h3 className="text-sm font-semibold text-ink sm:col-span-3">
                      Report it missing
                    </h3>
                    <Field label="What happened" htmlFor="lost-reason" required>
                      <TextInput
                        id="lost-reason"
                        required
                        value={lostReason}
                        onChange={(event) => setLostReason(event.target.value)}
                        placeholder="Left in the auditorium after a power cut"
                      />
                    </Field>
                    <div className="flex items-end sm:col-span-2">
                      <Button type="submit" loading={markLost.isPending}>
                        Report missing
                      </Button>
                    </div>
                  </form>
                )}

              {mayManage && data.asset.status === 'LOST' && (
                <form
                  className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    markFound.mutate({
                      location: foundLocation.trim() || null,
                      conditionStatus: foundCondition,
                    })
                  }}
                >
                  <h3 className="text-sm font-semibold text-ink sm:col-span-3">
                    Found it again
                  </h3>
                  <Field label="Where it turned up" htmlFor="found-location">
                    <TextInput
                      id="found-location"
                      value={foundLocation}
                      onChange={(event) => setFoundLocation(event.target.value)}
                    />
                  </Field>
                  <Field label="Condition now" htmlFor="found-condition">
                    <Select
                      id="found-condition"
                      value={foundCondition}
                      onChange={(event) => setFoundCondition(event.target.value as AssetCondition)}
                    >
                      {CONDITIONS.map((value) => (
                        <option key={value} value={value}>{value}</option>
                      ))}
                    </Select>
                  </Field>
                  <div className="flex items-end">
                    <Button type="submit" loading={markFound.isPending}>
                      Put back on the shelf
                    </Button>
                  </div>
                </form>
              )}

              {mayManage &&
                data.asset.status !== 'DISPOSED' &&
                data.asset.status !== 'IN_MAINTENANCE' && (
                  <form
                    className="grid gap-4 border-b border-border bg-surface-soft p-5 sm:grid-cols-3"
                    onSubmit={(event) => {
                      event.preventDefault()
                      dispose.mutate({
                        method: disposalMethod,
                        value: disposalValue.trim() === '' ? null : Number(disposalValue),
                        notes: disposalNotes.trim() || null,
                      })
                    }}
                  >
                    <h3 className="text-sm font-semibold text-ink sm:col-span-3">Write it off</h3>
                    <Field label="How it goes" htmlFor="disposal-method">
                      <Select
                        id="disposal-method"
                        value={disposalMethod}
                        onChange={(event) =>
                          setDisposalMethod(event.target.value as DisposalMethod)
                        }
                      >
                        {DISPOSAL_METHODS.map((value) => (
                          <option key={value} value={value}>{value}</option>
                        ))}
                      </Select>
                    </Field>
                    <Field label="Gone for" htmlFor="disposal-value">
                      <TextInput
                        id="disposal-value"
                        type="number"
                        min="0"
                        step="0.01"
                        value={disposalValue}
                        onChange={(event) => setDisposalValue(event.target.value)}
                      />
                    </Field>
                    <Field label="Notes" htmlFor="disposal-notes">
                      <TextInput
                        id="disposal-notes"
                        value={disposalNotes}
                        onChange={(event) => setDisposalNotes(event.target.value)}
                      />
                    </Field>
                    <div className="flex items-end sm:col-span-3">
                      <Button type="submit" loading={dispose.isPending}>
                        Write off
                      </Button>
                    </div>
                  </form>
                )}

              {feedback && <p className="border-b border-border p-4 text-sm text-success">{feedback}</p>}
              {returnAsset.isError && (
                <p role="alert" className="border-b border-border p-4 text-sm text-danger">
                  {say(returnAsset.error)}
                </p>
              )}
              {schedule.isError && (
                <p role="alert" className="border-b border-border p-4 text-sm text-danger">
                  {say(schedule.error)}
                </p>
              )}
              {markLost.isError && (
                <p role="alert" className="border-b border-border p-4 text-sm text-danger">
                  {say(markLost.error)}
                </p>
              )}
              {markFound.isError && (
                <p role="alert" className="border-b border-border p-4 text-sm text-danger">
                  {say(markFound.error)}
                </p>
              )}
              {dispose.isError && (
                <p role="alert" className="border-b border-border p-4 text-sm text-danger">
                  {say(dispose.error)}
                </p>
              )}

              <section className="border-b border-border p-5">
                <h3 className="mb-3 text-sm font-semibold text-ink">Custody history</h3>
                {data.history.length === 0 ? (
                  <p className="text-sm text-ink-muted">This asset has never left the building.</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {data.history.map((entry) => (
                      <li
                        key={entry.id}
                        className="flex flex-wrap items-baseline justify-between gap-2 text-sm"
                      >
                        <span className="text-ink">
                          {entry.holderName}
                          <span className="text-ink-muted"> ({entry.holderType.toLowerCase()})</span>
                        </span>
                        <span className="text-ink-muted">
                          {date(entry.assignedAt)} →{' '}
                          {entry.returnedAt ? date(entry.returnedAt) : 'still out'}
                          {entry.conditionIn ? ` · came back ${entry.conditionIn.toLowerCase()}` : ''}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <section className="p-5">
                <h3 className="mb-3 text-sm font-semibold text-ink">Servicing</h3>
                {data.maintenance.length === 0 ? (
                  <p className="text-sm text-ink-muted">Never serviced.</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {data.maintenance.map((entry) => (
                      <li
                        key={entry.id}
                        className="flex flex-wrap items-baseline justify-between gap-2 text-sm"
                      >
                        <span className="text-ink">
                          {entry.type.charAt(0) + entry.type.slice(1).toLowerCase()}
                          {entry.vendor ? (
                            <span className="text-ink-muted"> · {entry.vendor}</span>
                          ) : null}
                        </span>
                        <span className="flex items-center gap-2 text-ink-muted">
                          {date(entry.scheduledFor)}
                          <StatusBadge status={entry.status} />
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </QueryBoundary>
      </div>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink">{value}</dd>
    </div>
  )
}

/* ---------------------------------------------------------------- categories */

export function AssetCategories() {
  const queryClient = useQueryClient()
  const categories = useCategories()
  const [values, setValues] = useState<CreateAssetCategory>({ code: '', name: '' })
  const [feedback, setFeedback] = useState('')

  const save = useMutation({
    mutationFn: (body: CreateAssetCategory) => post<AssetCategory>('/api/v1/assets/categories', body),
    onSuccess: () => {
      setFeedback('Category added.')
      setValues({ code: '', name: '' })
      queryClient.invalidateQueries({ queryKey: ['assets'] })
    },
  })

  return (
    <Panel>
      <form
        className="grid gap-3 border-b border-border p-4 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate({
            code: values.code.trim(),
            name: values.name.trim(),
            depreciationRate:
              values.depreciationRate === null || values.depreciationRate === undefined
                ? null
                : Number(values.depreciationRate),
            usefulLifeYears: values.usefulLifeYears ?? null,
          })
        }}
      >
        <Field label="Code" htmlFor="cat-code" required>
          <TextInput
            id="cat-code"
            required
            value={values.code}
            onChange={(event) => setValues((v) => ({ ...v, code: event.target.value }))}
          />
        </Field>
        <Field label="Name" htmlFor="cat-name" required>
          <TextInput
            id="cat-name"
            required
            value={values.name}
            onChange={(event) => setValues((v) => ({ ...v, name: event.target.value }))}
          />
        </Field>
        <Field label="Depreciation rate" htmlFor="cat-rate" hint="A share, so 0.25 is a quarter a year.">
          <TextInput
            id="cat-rate"
            type="number"
            step="0.01"
            min="0"
            value={values.depreciationRate ?? ''}
            onChange={(event) =>
              setValues((v) => ({
                ...v,
                depreciationRate: event.target.value === '' ? null : Number(event.target.value),
              }))
            }
          />
        </Field>
        <Field label="Useful life" htmlFor="cat-life">
          <TextInput
            id="cat-life"
            type="number"
            min="1"
            value={values.usefulLifeYears ?? ''}
            onChange={(event) =>
              setValues((v) => ({
                ...v,
                usefulLifeYears: event.target.value === '' ? null : Number(event.target.value),
              }))
            }
          />
        </Field>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" loading={save.isPending}>Add category</Button>
          {feedback && <span className="text-sm text-success">{feedback}</span>}
          {save.isError && (
            <span role="alert" className="text-sm text-danger">{say(save.error)}</span>
          )}
        </div>
      </form>

      <ul className="flex flex-col">
        {(categories.data ?? []).map((category) => (
          <li
            key={category.id}
            className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 last:border-0"
          >
            <div>
              <p className="text-sm font-medium text-ink">{category.name}</p>
              <p className="text-xs text-ink-muted">
                {category.code}
                {category.depreciationRate
                  ? ` · ${(category.depreciationRate * 100).toFixed(1)}% a year`
                  : ''}
                {category.usefulLifeYears ? ` · ${category.usefulLifeYears} yr` : ''}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </Panel>
  )
}