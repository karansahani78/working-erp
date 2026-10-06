import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, patch, post, type PageResponse } from '../../lib/api'
import { date, dateTime, money, num, text } from '../../lib/format'
import { Button, Field, Panel, QueryBoundary, Select, StatusBadge, TextInput } from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { useAuth } from '../../auth/AuthProvider'
import {
  type Category,
  type CreateCategory,
  type CreateItem,
  type CreatePurchase,
  type CreateStore,
  type CreateTransfer,
  type Item,
  type Issue,
  type LowStock,
  type Movement,
  type Overview,
  type Purchase,
  type RecipientType,
  type ReceivePurchase,
  type StockAdjustment,
  type StockLevel,
  type Store,
  type Transfer,
} from './types'

const PAGE_SIZE = 20

const UNITS = ['PIECE', 'BOX', 'PACKET', 'SET', 'LITRE', 'KG', 'METRE', 'BOTTLE']
const RECIPIENT_TYPES: RecipientType[] = [
  'DEPARTMENT',
  'CLASS',
  'COURSE',
  'STUDENT',
  'EMPLOYEE',
  'USER',
  'EXTERNAL',
]

/** Purchases, issues and transfers all move stock, so each needs its own workflow. */
type View = 'stock' | 'purchases' | 'issues' | 'transfers'

function describeError(error: unknown): string {
  const body = (error as { body?: { message?: string } } | undefined)?.body
  return body?.message ?? 'That did not work. Try again.'
}

export function InventoryPage() {
  const [view, setView] = useState<View>('stock')

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Inventory"
        description="Items, what is on hand, and every movement between stores."
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <OverviewTile title="Items" queryKey={['inventory', 'overview']} pick={(o: Overview) => o.itemCount} />
        <OverviewTile title="Stock value" queryKey={['inventory', 'overview']} pick={(o: Overview) => money(o.stockValue)} />
        <OverviewTile title="Open issues" queryKey={['inventory', 'overview']} pick={(o: Overview) => o.openIssueCount} />
        <OverviewTile title="Low stock" queryKey={['inventory', 'overview']} pick={(o: Overview) => o.lowStockCount} />
      </div>

      <div role="tablist" aria-label="Inventory sections" className="flex flex-wrap gap-2">
        {(
          [
            ['stock', 'Stock on hand'],
            ['purchases', 'Purchases'],
            ['issues', 'Issues'],
            ['transfers', 'Transfers'],
          ] as const
        ).map(([value, label]) => (
          <Button
            key={value}
            role="tab"
            aria-selected={view === value}
            variant={view === value ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => setView(value)}
          >
            {label}
          </Button>
        ))}
      </div>

      {view === 'stock' && <StockView />}
      {view === 'purchases' && <PurchasesView />}
      {view === 'issues' && <IssuesView />}
      {view === 'transfers' && <TransfersView />}

      <SetupPanels />
    </div>
  )
}

function OverviewTile({
  title,
  queryKey,
  pick,
}: {
  title: string
  queryKey: string[]
  pick: (overview: Overview) => string | number
}) {
  const overview = useQuery({ queryKey, queryFn: () => api<Overview>('/api/v1/inventory/overview') })
  return (
    <Panel title={title}>
      <p className="nums text-2xl font-semibold text-ink">
        {overview.data ? pick(overview.data) : '—'}
      </p>
    </Panel>
  )
}

// ------------------------------------------------------------------ stock on hand

function StockView() {
  const { can } = useAuth()
  const [storeId, setStoreId] = useState('')
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')
  const [ledgerItemId, setLedgerItemId] = useState('')

  const stores = useQuery({
    queryKey: ['inventory', 'stores'],
    queryFn: () => api<PageResponse<Store>>('/api/v1/inventory/stores', { query: { page: 0, size: 100 } }),
  })

  const stock = useQuery({
    queryKey: ['inventory', 'stock', storeId],
    queryFn: () => api<StockLevel[]>(`/api/v1/inventory/stores/${storeId}/stock`),
    enabled: Boolean(storeId),
  })

  const items = useQuery({
    queryKey: ['inventory', 'items', term, page],
    queryFn: () =>
      api<PageResponse<Item>>('/api/v1/inventory/items', { query: { page, size: PAGE_SIZE, search: term || undefined } }),
  })

  const lowStock = useQuery({
    queryKey: ['inventory', 'low-stock'],
    queryFn: () => api<LowStock[]>('/api/v1/inventory/low-stock'),
  })

  const ledger = useQuery({
    queryKey: ['inventory', 'ledger', ledgerItemId],
    queryFn: () =>
      api<PageResponse<Movement>>(`/api/v1/inventory/items/${ledgerItemId}/movements`, {
          query: {
            page: 0,
            size: 50,
          },
      }),
    enabled: Boolean(ledgerItemId),
  })

  return (
    <div className="flex flex-col gap-5">
      <Panel title="Stock by store" description="Pick a store to see what it holds.">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Store" htmlFor="stock-store" required>
            <Select id="stock-store" value={storeId} onChange={(event) => setStoreId(event.target.value)}>
              <option value="">Choose a store</option>
              {(stores.data?.data ?? []).map((store) => (
                <option key={store.id} value={store.id}>
                  {store.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Panel>

      {storeId && (
        <Panel title="On hand" padded={false}>
          <QueryBoundary
            isLoading={stock.isLoading}
            error={stock.error}
            data={stock.data}
            onRetry={() => void stock.refetch()}
            loadingRows={5}
            empty={
              <div className="p-6">
                <p className="text-sm text-ink-muted">This store holds nothing yet.</p>
              </div>
            }
          >
            {(rows) => (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <th className="px-4 py-3 font-medium text-ink-subtle">Code</th>
                      <th className="px-4 py-3 font-medium text-ink-subtle">Item</th>
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Quantity</th>
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Value</th>
                      <th className="px-4 py-3 font-medium text-ink-subtle">Last movement</th>
                      {can('INVENTORY_READ') && (
                        <th className="px-4 py-3 text-right font-medium text-ink-subtle">Actions</th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr key={`${row.storeId}-${row.itemId}`} className="border-b border-border last:border-0">
                        <td className="nums px-4 py-3 font-medium text-ink">{row.itemCode}</td>
                        <td className="px-4 py-3 text-ink">{row.itemName}</td>
                        <td className="nums px-4 py-3 text-right">
                          {num(row.quantity)} {row.unit.toLowerCase()}
                        </td>
                        <td className="nums px-4 py-3 text-right">{money(row.stockValue)}</td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {dateTime(row.lastMovementAt)}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setLedgerItemId(row.itemId)}
                          >
                            Ledger
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </QueryBoundary>
        </Panel>
      )}

      {ledgerItemId && (
        <Panel
          title="Movement ledger"
          description={`Every movement of this item, newest first.`}
          padded={false}
        >
          <QueryBoundary
            isLoading={ledger.isLoading}
            error={ledger.error}
            data={ledger.data?.data}
            onRetry={() => void ledger.refetch()}
            loadingRows={5}
            empty={
              <div className="p-6">
                <p className="text-sm text-ink-muted">This item has never moved.</p>
              </div>
            }
          >
            {(rows) => (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <th className="px-4 py-3 font-medium text-ink-subtle">When</th>
                      <th className="px-4 py-3 font-medium text-ink-subtle">Type</th>
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Change</th>
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Balance</th>
                      <th className="px-4 py-3 font-medium text-ink-subtle">Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row) => (
                      <tr key={row.id} className="border-b border-border last:border-0">
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {dateTime(row.movedAt)}
                        </td>
                        <td className="px-4 py-3 text-ink">{row.type.replace(/_/g, ' ')}</td>
                        <td
                          className={`nums px-4 py-3 text-right ${
                            Number(row.signedQuantity) < 0 ? 'text-danger' : 'text-primary'
                          }`}
                        >
                          {Number(row.signedQuantity) > 0 ? '+' : ''}
                          {num(row.signedQuantity)}
                        </td>
                        <td className="nums px-4 py-3 text-right">{num(row.balanceAfter)}</td>
                        <td className="px-4 py-3 text-ink-muted">{row.reason ?? text(row.referenceType)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </QueryBoundary>
        </Panel>
      )}

      <Panel title="Items" padded={false}>
        <div className="border-b border-border px-4 py-3">
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Search items">
              <TextInput
                value={term}
                onChange={(event) => {
                  setTerm(event.target.value)
                  setPage(0)
                }}
                placeholder="Name or code"
              />
            </Field>
          </div>
        </div>
        <QueryBoundary
          isLoading={items.isLoading}
          error={items.error}
          data={items.data?.data}
          onRetry={() => void items.refetch()}
          loadingRows={6}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">No items defined yet.</p>
            </div>
          }
        >
          {(rows) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-3 font-medium text-ink-subtle">Code</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Name</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Category</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Unit</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">On hand</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Reorder at</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((item) => (
                    <tr key={item.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{item.code}</td>
                      <td className="px-4 py-3 text-ink">{item.name}</td>
                      <td className="px-4 py-3 text-ink-muted">{item.categoryName ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-muted">{item.unit.toLowerCase()}</td>
                      <td className="nums px-4 py-3 text-right">{num(item.totalOnHand)}</td>
                      <td className="nums px-4 py-3 text-right text-ink-muted">
                        {num(item.reorderLevel)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge
                          status={item.needsReorder ? 'NEEDS REORDER' : item.active ? 'ACTIVE' : 'INACTIVE'}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
        <Pager
          page={
            items.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }
          }
          onChange={setPage}
        />
      </Panel>

      <Panel title="Low stock" description="Items at or below their reorder level." padded={false}>
        <QueryBoundary
          isLoading={lowStock.isLoading}
          error={lowStock.error}
          data={lowStock.data}
          onRetry={() => void lowStock.refetch()}
          loadingRows={4}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">Nothing needs reordering.</p>
            </div>
          }
        >
          {(rows) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-3 font-medium text-ink-subtle">Item</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">On hand</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Reorder level</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Suggested order</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.itemId} className="border-b border-border last:border-0">
                      <td className="px-4 py-3 font-medium text-ink">
                        {row.itemCode} · {row.itemName}
                      </td>
                      <td className="nums px-4 py-3 text-right">{num(row.totalOnHand)}</td>
                      <td className="nums px-4 py-3 text-right text-ink-muted">
                        {num(row.reorderLevel)}
                      </td>
                      <td className="nums px-4 py-3 text-right font-medium">
                        {num(row.suggestedOrderQuantity)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
      </Panel>

      <StocktakeForm items={items.data?.data ?? []} stores={stores.data?.data ?? []} />
    </div>
  )
}

/** A stocktake records what was actually counted, and the ledger corrects itself from it. */
function StocktakeForm({ items, stores }: { items: Item[]; stores: Store[] }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const adjust = useMutation({
    mutationFn: (payload: StockAdjustment) => post('/api/v1/inventory/adjustments', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (!can('INVENTORY_TRANSACT')) return null

  return (
    <Panel title="Stocktake" description="Record a counted quantity and the ledger adjusts to it.">
      {error && (
        <p role="alert" className="mb-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <form
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          const counted = String(data.get('countedQuantity') ?? '')
          if (!counted || Number.isNaN(Number(counted))) {
            setError('Enter the quantity you counted.')
            return
          }
          adjust.mutate({
            storeId: String(data.get('storeId') ?? ''),
            itemId: String(data.get('itemId') ?? ''),
            countedQuantity: Number(counted),
            reason: String(data.get('reason') ?? '') || undefined,
          })
        }}
      >
        <Field label="Store" required>
          <Select name="storeId" required>
            <option value="">Choose a store</option>
            {stores.map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Item" required>
          <Select name="itemId" required>
            <option value="">Choose an item</option>
            {items.map((item) => (
              <option key={item.id} value={item.id}>
                {item.code} · {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Counted quantity" required>
          <TextInput name="countedQuantity" type="number" step="0.01" min="0" required />
        </Field>
        <Field label="Reason">
          <TextInput name="reason" placeholder="Annual stocktake" />
        </Field>
        <div className="lg:col-span-4">
          <Button type="submit" loading={adjust.isPending}>
            Record stocktake
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// ---------------------------------------------------------------------- purchases

function PurchasesView() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Purchase | null>(null)

  const purchases = useQuery({
    queryKey: ['inventory', 'purchases', page],
    queryFn: () =>
      api<PageResponse<Purchase>>('/api/v1/inventory/purchases', { query: { page, size: PAGE_SIZE } }),
  })

  const detail = useQuery({
    queryKey: ['inventory', 'purchase', selected?.id],
    queryFn: () => api<Purchase>(`/api/v1/inventory/purchases/${selected!.id}`),
    enabled: Boolean(selected),
  })

  return (
    <div className="flex flex-col gap-5">
      {can('INVENTORY_MANAGE') && <PurchaseForm />}

      <Panel title="Purchase orders" padded={false}>
        <QueryBoundary
          isLoading={purchases.isLoading}
          error={purchases.error}
          data={purchases.data?.data}
          onRetry={() => void purchases.refetch()}
          loadingRows={6}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">No purchase orders raised yet.</p>
            </div>
          }
        >
          {(rows) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-3 font-medium text-ink-subtle">Number</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Supplier</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Store</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Total</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Status</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{row.purchaseNumber}</td>
                      <td className="px-4 py-3 text-ink">{row.supplierName}</td>
                      <td className="px-4 py-3 text-ink-muted">{row.storeName ?? '—'}</td>
                      <td className="nums px-4 py-3 text-right">{money(row.totalAmount)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={row.status} />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button size="sm" variant="ghost" onClick={() => setSelected(row)}>
                          View
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
        <Pager
          page={
            purchases.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }
          }
          onChange={setPage}
        />
      </Panel>

      {selected && (
        <PurchaseDetail
          purchase={detail.data ?? selected}
          isLoading={detail.isLoading}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  )
}

function PurchaseDetail({
  purchase,
  isLoading,
  onClose,
}: {
  purchase: Purchase
  isLoading: boolean
  onClose: () => void
}) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [receiving, setReceiving] = useState(false)
  const [quantities, setQuantities] = useState<Record<string, string>>({})

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['inventory'] })
  }

  const transition = useMutation({
    mutationFn: (action: 'place-order' | 'cancel') =>
      patch<Purchase>(`/api/v1/inventory/purchases/${purchase.id}/${action}`),
    onSuccess: () => {
      setError(null)
      refresh()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const receive = useMutation({
    mutationFn: (payload: ReceivePurchase) =>
      post<Purchase>(`/api/v1/inventory/purchases/${purchase.id}/receive`, payload),
    onSuccess: () => {
      setError(null)
      setReceiving(false)
      setQuantities({})
      refresh()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const outstanding = purchase.items.filter((line) => !line.fullyReceived)

  return (
    <Panel
      title={purchase.purchaseNumber}
      description={`${purchase.supplierName}${purchase.supplierContact ? ` · ${purchase.supplierContact}` : ''}`}
    >
      {error && (
        <p role="alert" className="mb-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
        <StatusBadge status={purchase.status} />
        <span>Expected {date(purchase.expectedOn)}</span>
        {purchase.receivedOn && <span>· Received {date(purchase.receivedOn)}</span>}
        <span className="nums font-medium text-ink">{money(purchase.totalAmount)}</span>
      </div>

      {isLoading ? (
        <p className="text-sm text-ink-muted">Loading lines…</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <th className="px-4 py-3 font-medium text-ink-subtle">Item</th>
                <th className="px-4 py-3 text-right font-medium text-ink-subtle">Ordered</th>
                <th className="px-4 py-3 text-right font-medium text-ink-subtle">Received</th>
                <th className="px-4 py-3 text-right font-medium text-ink-subtle">Outstanding</th>
                <th className="px-4 py-3 text-right font-medium text-ink-subtle">Unit cost</th>
                <th className="px-4 py-3 text-right font-medium text-ink-subtle">Line total</th>
              </tr>
            </thead>
            <tbody>
              {purchase.items.map((line) => (
                <tr key={line.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 text-ink">
                    {line.itemCode} · {line.itemName}
                  </td>
                  <td className="nums px-4 py-3 text-right">{num(line.quantityOrdered)}</td>
                  <td className="nums px-4 py-3 text-right">{num(line.quantityReceived)}</td>
                  <td className="nums px-4 py-3 text-right text-ink-muted">
                    {num(line.outstanding)}
                  </td>
                  <td className="nums px-4 py-3 text-right text-ink-muted">{money(line.unitCost)}</td>
                  <td className="nums px-4 py-3 text-right">{money(line.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {can('INVENTORY_MANAGE') && purchase.status === 'DRAFT' && (
        <div className="mt-4 flex gap-2">
          <Button
            variant="secondary"
            loading={transition.isPending}
            onClick={() => transition.mutate('place-order')}
          >
            Send to supplier
          </Button>
          <Button variant="ghost" onClick={() => transition.mutate('cancel')}>
            Cancel order
          </Button>
        </div>
      )}

      {can('INVENTORY_TRANSACT') && purchase.status !== 'CANCELLED' && outstanding.length > 0 && (
        <div className="mt-5 border-t border-border pt-4">
          {!receiving ? (
            <Button onClick={() => setReceiving(true)}>Receive delivery</Button>
          ) : (
            <form
              className="grid gap-4 sm:grid-cols-2"
              onSubmit={(event) => {
                event.preventDefault()
                const data = new FormData(event.currentTarget)
                const receivedOn = String(data.get('receivedOn') ?? '')
                const lines = outstanding
                  .map((line) => ({
                    purchaseItemId: line.id,
                    quantityReceived: Number(quantities[line.id] ?? 0),
                  }))
                  .filter((line) => line.quantityReceived > 0)
                if (lines.length === 0) {
                  setError('Enter at least one quantity to receive.')
                  return
                }
                receive.mutate({ storeId: purchase.storeId ?? '', receivedOn: receivedOn || undefined, lines })
              }}
            >
              <Field label="Received on">
                <TextInput name="receivedOn" type="date" />
              </Field>
              <div className="sm:col-span-2">
                <p className="mb-2 text-sm text-ink-muted">
                  Leave a line at zero to keep it outstanding.
                </p>
                <div className="grid gap-3">
                  {outstanding.map((line) => (
                    <div key={line.id} className="flex items-center justify-between gap-3 text-sm">
                      <span className="text-ink">
                        {line.itemName}{' '}
                        <span className="nums text-ink-muted">
                          ({num(line.outstanding)} {line.unit.toLowerCase()} outstanding)
                        </span>
                      </span>
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        max={line.outstanding}
                        value={quantities[line.id] ?? ''}
                        onChange={(event) =>
                          setQuantities((current) => ({
                            ...current,
                            [line.id]: event.target.value,
                          }))
                        }
                        className="nums w-28 rounded-lg border border-border px-3 py-2 text-right text-sm"
                        aria-label={`Quantity received for ${line.itemName}`}
                      />
                    </div>
                  ))}
                </div>
              </div>
              <div className="flex gap-2 sm:col-span-2">
                <Button type="submit" loading={receive.isPending}>
                  Confirm receipt
                </Button>
                <Button type="button" variant="ghost" onClick={() => setReceiving(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          )}
        </div>
      )}

      <div className="mt-5">
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Panel>
  )
}

function PurchaseForm() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [lines, setLines] = useState<Array<{ itemId: string; quantityOrdered: string; unitCost: string }>>([
    { itemId: '', quantityOrdered: '', unitCost: '' },
  ])

  const stores = useQuery({
    queryKey: ['inventory', 'stores'],
    queryFn: () => api<PageResponse<Store>>('/api/v1/inventory/stores', { query: { page: 0, size: 100 } }),
  })

  const items = useQuery({
    queryKey: ['inventory', 'items', 'purchase-form'],
    queryFn: () => api<PageResponse<Item>>('/api/v1/inventory/items', { query: { page: 0, size: 200 } }),
  })

  const create = useMutation({
    mutationFn: (payload: CreatePurchase) => post<Purchase>('/api/v1/inventory/purchases', payload),
    onSuccess: () => {
      setError(null)
      setLines([{ itemId: '', quantityOrdered: '', unitCost: '' }])
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <Panel title="Raise a purchase order" description="It stays a draft until you send it.">
      {error && (
        <p role="alert" className="mb-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <form
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          const usable = lines.filter((line) => line.itemId && Number(line.quantityOrdered) > 0)
          if (usable.length === 0) {
            setError('Add at least one item with a quantity.')
            return
          }
          create.mutate({
            supplierName: String(data.get('supplierName') ?? ''),
            supplierContact: String(data.get('supplierContact') ?? '') || undefined,
            storeId: String(data.get('storeId') ?? ''),
            expectedOn: String(data.get('expectedOn') ?? '') || undefined,
            taxAmount: Number(String(data.get('taxAmount') ?? 0)),
            otherCosts: Number(String(data.get('otherCosts') ?? 0)),
            notes: String(data.get('notes') ?? '') || undefined,
            items: usable.map((line) => ({
              itemId: line.itemId,
              quantityOrdered: Number(line.quantityOrdered),
              unitCost: Number(line.unitCost || 0),
            })),
          })
        }}
      >
        <Field label="Supplier" required>
          <TextInput name="supplierName" required placeholder="Khwopa Traders" />
        </Field>
        <Field label="Contact">
          <TextInput name="supplierContact" placeholder="Phone or email" />
        </Field>
        <Field label="Deliver to" required>
          <Select name="storeId" required>
            <option value="">Choose a store</option>
            {(stores.data?.data ?? []).map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Expected on">
          <TextInput name="expectedOn" type="date" />
        </Field>
        <Field label="Tax" hint="Added on top of the line totals.">
          <TextInput name="taxAmount" type="number" min="0" step="0.01" defaultValue="0" />
        </Field>
        <Field label="Other costs" hint="Freight, loading and similar.">
          <TextInput name="otherCosts" type="number" min="0" step="0.01" defaultValue="0" />
        </Field>

        <div className="sm:col-span-2 lg:col-span-4">
          <p className="mb-2 text-sm font-medium text-ink">Items</p>
          <div className="grid gap-3">
            {lines.map((line, index) => (
              <div key={index} className="grid items-end gap-3 sm:grid-cols-[1fr_8rem_8rem_auto]">
                <Field label={`Item${index > 0 ? ` ${index + 1}` : ''}`} required>
                  <Select
                    value={line.itemId}
                    onChange={(event) =>
                      setLines((current) =>
                        current.map((row, i) =>
                          i === index ? { ...row, itemId: event.target.value } : row,
                        ),
                      )
                    }
                  >
                    <option value="">Choose an item</option>
                    {(items.data?.data ?? []).map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.code} · {item.name}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field label={`Quantity${index > 0 ? ` ${index + 1}` : ''}`}>
                  <TextInput
                    type="number"
                    min="0"
                    step="0.01"
                    value={line.quantityOrdered}
                    onChange={(event) =>
                      setLines((current) =>
                        current.map((row, i) =>
                          i === index ? { ...row, quantityOrdered: event.target.value } : row,
                        ),
                      )
                    }
                  />
                </Field>
                <Field label={`Unit cost${index > 0 ? ` ${index + 1}` : ''}`}>
                  <TextInput
                    type="number"
                    min="0"
                    step="0.01"
                    value={line.unitCost}
                    onChange={(event) =>
                      setLines((current) =>
                        current.map((row, i) =>
                          i === index ? { ...row, unitCost: event.target.value } : row,
                        ),
                      )
                    }
                  />
                </Field>
                <Button
                  type="button"
                  variant="ghost"
                  disabled={lines.length === 1}
                  onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
                >
                  Remove
                </Button>
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="mt-3"
            onClick={() =>
              setLines((current) => [...current, { itemId: '', quantityOrdered: '', unitCost: '' }])
            }
          >
            Add another item
          </Button>
        </div>

        <Field label="Notes">
          <TextInput name="notes" placeholder="Anything the supplier should know" />
        </Field>
        <div className="sm:col-span-2 lg:col-span-4">
          <Button type="submit" loading={create.isPending}>
            Save as draft
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// ------------------------------------------------------------------------- issues

function IssuesView() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)

  const issues = useQuery({
    queryKey: ['inventory', 'issues', page],
    queryFn: () => api<PageResponse<Issue>>('/api/v1/inventory/issues', { query: { page, size: PAGE_SIZE } }),
  })

  return (
    <div className="flex flex-col gap-5">
      {can('INVENTORY_TRANSACT') && <IssueForm />}

      <Panel title="Issues" description="Stock handed out, and what came back." padded={false}>
        <QueryBoundary
          isLoading={issues.isLoading}
          error={issues.error}
          data={issues.data?.data}
          onRetry={() => void issues.refetch()}
          loadingRows={6}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">Nothing has been issued yet.</p>
            </div>
          }
        >
          {(rows) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-3 font-medium text-ink-subtle">Number</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Item</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Quantity</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">To</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Issued</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Status</th>
                    {can('INVENTORY_TRANSACT') && (
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Actions</th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{row.issueNumber}</td>
                      <td className="px-4 py-3 text-ink">
                        {row.itemName}{' '}
                        <span className="nums text-ink-muted">
                          ({num(row.quantity)} {row.unit.toLowerCase()})
                        </span>
                      </td>
                      <td className="nums px-4 py-3 text-right">{num(row.quantity)}</td>
                      <td className="px-4 py-3 text-ink-muted">
                        {row.issuedToName ?? row.issuedToType}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                        {dateTime(row.issuedAt)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={row.status} />
                      </td>
                      {can('INVENTORY_TRANSACT') && (
                        <td className="px-4 py-3 text-right">
                          <ReturnButton issue={row} />
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
        <Pager
          page={issues.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }}
          onChange={setPage}
        />
      </Panel>
    </div>
  )
}

function ReturnButton({ issue }: { issue: Issue }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')

  const giveBack = useMutation({
    mutationFn: () =>
      // The API also expects the issue id in the body, not only in the path.
      patch<Issue>(`/api/v1/inventory/issues/${issue.id}/return`, {
        issueId: issue.id,
        quantityReturned: Number(quantity || issue.quantity),
        reason: reason || undefined,
      }),
    onSuccess: () => {
      setError(null)
      setQuantity('')
      setReason('')
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (issue.status !== 'ISSUED') {
    return <span className="text-xs text-ink-subtle">Settled</span>
  }

  return (
    <div className="flex flex-col items-end gap-1">
      {error && <span className="text-xs text-danger">{error}</span>}
      <div className="flex gap-2">
        <input
          type="number"
          min="0"
          step="0.01"
          value={quantity}
          onChange={(event) => setQuantity(event.target.value)}
          placeholder={String(issue.quantity)}
          aria-label={`Quantity returned for ${issue.issueNumber}`}
          className="nums w-20 rounded-lg border border-border px-2 py-1 text-right text-xs"
        />
        <input
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Reason"
          aria-label={`Reason for returning ${issue.issueNumber}`}
          className="w-28 rounded-lg border border-border px-2 py-1 text-xs"
        />
        <Button size="sm" variant="ghost" loading={giveBack.isPending} onClick={() => giveBack.mutate()}>
          Give back
        </Button>
      </div>
    </div>
  )
}

function IssueForm() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const stores = useQuery({
    queryKey: ['inventory', 'stores'],
    queryFn: () => api<PageResponse<Store>>('/api/v1/inventory/stores', { query: { page: 0, size: 100 } }),
  })

  const items = useQuery({
    queryKey: ['inventory', 'items', 'issue-form'],
    queryFn: () => api<PageResponse<Item>>('/api/v1/inventory/items', { query: { page: 0, size: 200 } }),
  })

  const issue = useMutation({
    mutationFn: (payload: Record<string, unknown>) => post<Issue>('/api/v1/inventory/issues', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <Panel title="Issue stock" description="Hand items to a department, a class or a person.">
      {error && (
        <p role="alert" className="mb-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <form
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          const quantity = String(data.get('quantity') ?? '')
          if (!quantity || Number(quantity) <= 0) {
            setError('Enter how many you are handing over.')
            return
          }
          const issuedToType = String(data.get('issuedToType') ?? 'DEPARTMENT') as RecipientType
          const issuedToName = String(data.get('issuedToName') ?? '')
          if (!issuedToName) {
            setError('Say who the stock is going to.')
            return
          }
          issue.mutate({
            itemId: String(data.get('itemId') ?? ''),
            storeId: String(data.get('storeId') ?? ''),
            quantity: Number(quantity),
            issuedToType,
            issuedToName,
            reason: String(data.get('reason') ?? '') || undefined,
          })
        }}
      >
        <Field label="Item" required>
          <Select name="itemId" required>
            <option value="">Choose an item</option>
            {(items.data?.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.code} · {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="From store" required>
          <Select name="storeId" required>
            <option value="">Choose a store</option>
            {(stores.data?.data ?? []).map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Quantity" required>
          <TextInput name="quantity" type="number" min="0" step="0.01" required />
        </Field>
        <Field label="Recipient type" required>
          <Select name="issuedToType" defaultValue="DEPARTMENT">
            {RECIPIENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Recipient name" required hint="A department, class or person.">
          <TextInput name="issuedToName" required placeholder="Science department" />
        </Field>
        <Field label="Reason">
          <TextInput name="reason" placeholder="Term start issue" />
        </Field>
        <div className="sm:col-span-2 lg:col-span-3">
          <Button type="submit" loading={issue.isPending}>
            Issue stock
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// ---------------------------------------------------------------------- transfers

function TransfersView() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)

  const transfers = useQuery({
    queryKey: ['inventory', 'transfers', page],
    queryFn: () => api<PageResponse<Transfer>>('/api/v1/inventory/transfers', { query: { page, size: PAGE_SIZE } }),
  })

  return (
    <div className="flex flex-col gap-5">
      {can('INVENTORY_TRANSACT') && <TransferForm />}

      <Panel title="Store transfers" padded={false}>
        <QueryBoundary
          isLoading={transfers.isLoading}
          error={transfers.error}
          data={transfers.data?.data}
          onRetry={() => void transfers.refetch()}
          loadingRows={6}
          empty={
            <div className="p-6">
              <p className="text-sm text-ink-muted">No transfers requested.</p>
            </div>
          }
        >
          {(rows) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <th className="px-4 py-3 font-medium text-ink-subtle">Number</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Item</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">From</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">To</th>
                    <th className="px-4 py-3 text-right font-medium text-ink-subtle">Quantity</th>
                    <th className="px-4 py-3 font-medium text-ink-subtle">Status</th>
                    {can('INVENTORY_TRANSACT') && (
                      <th className="px-4 py-3 text-right font-medium text-ink-subtle">Actions</th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{row.transferNumber}</td>
                      <td className="px-4 py-3 text-ink">{row.itemName}</td>
                      <td className="px-4 py-3 text-ink-muted">{row.fromStoreName ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-muted">{row.toStoreName ?? '—'}</td>
                      <td className="nums px-4 py-3 text-right">{num(row.quantity)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={row.status} />
                      </td>
                      {can('INVENTORY_TRANSACT') && (
                        <td className="px-4 py-3 text-right">
                          <TransferActions transfer={row} />
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
        <Pager
          page={
            transfers.data ?? { page: 0, totalPages: 0, totalElements: 0, first: true, last: true }
          }
          onChange={setPage}
        />
      </Panel>
    </div>
  )
}

function TransferActions({ transfer }: { transfer: Transfer }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const act = useMutation({
    mutationFn: (action: 'dispatch' | 'complete' | 'cancel') =>
      patch<Transfer>(`/api/v1/inventory/transfers/${transfer.id}/${action}`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (transfer.status === 'CANCELLED' || transfer.status === 'COMPLETED') {
    return <span className="text-xs text-ink-subtle">Closed</span>
  }

  return (
    <div className="flex flex-col items-end gap-1">
      {error && <span className="text-xs text-danger">{error}</span>}
      <div className="flex gap-2">
        {transfer.status === 'REQUESTED' && (
          <>
            <Button size="sm" variant="ghost" onClick={() => act.mutate('dispatch')}>
              Dispatch
            </Button>
            <Button size="sm" variant="ghost" onClick={() => act.mutate('cancel')}>
              Cancel
            </Button>
          </>
        )}
        {transfer.status === 'DISPATCHED' && (
          <Button size="sm" variant="ghost" onClick={() => act.mutate('complete')}>
            Confirm arrival
          </Button>
        )}
      </div>
    </div>
  )
}

function TransferForm() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const stores = useQuery({
    queryKey: ['inventory', 'stores'],
    queryFn: () => api<PageResponse<Store>>('/api/v1/inventory/stores', { query: { page: 0, size: 100 } }),
  })

  const items = useQuery({
    queryKey: ['inventory', 'items', 'transfer-form'],
    queryFn: () => api<PageResponse<Item>>('/api/v1/inventory/items', { query: { page: 0, size: 200 } }),
  })

  const request = useMutation({
    mutationFn: (payload: CreateTransfer) => post<Transfer>('/api/v1/inventory/transfers', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <Panel title="Request a transfer" description="Stock leaves on dispatch and arrives on confirmation.">
      {error && (
        <p role="alert" className="mb-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <form
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          const fromStoreId = String(data.get('fromStoreId') ?? '')
          const toStoreId = String(data.get('toStoreId') ?? '')
          if (fromStoreId === toStoreId) {
            setError('A transfer needs two different stores.')
            return
          }
          const quantity = Number(String(data.get('quantity') ?? ''))
          if (!quantity || quantity <= 0) {
            setError('Enter how many you are moving.')
            return
          }
          request.mutate({
            itemId: String(data.get('itemId') ?? ''),
            fromStoreId,
            toStoreId,
            quantity,
            reason: String(data.get('reason') ?? '') || undefined,
          })
        }}
      >
        <Field label="Item" required>
          <Select name="itemId" required>
            <option value="">Choose an item</option>
            {(items.data?.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.code} · {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="From store" required>
          <Select name="fromStoreId" required>
            <option value="">Choose a store</option>
            {(stores.data?.data ?? []).map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="To store" required>
          <Select name="toStoreId" required>
            <option value="">Choose a store</option>
            {(stores.data?.data ?? []).map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Quantity" required>
          <TextInput name="quantity" type="number" min="0" step="0.01" required />
        </Field>
        <Field label="Reason" hint="Why the stock is moving.">
          <TextInput name="reason" placeholder="Rebalancing for exam week" />
        </Field>
        <div className="sm:col-span-2 lg:col-span-3">
          <Button type="submit" loading={request.isPending}>
            Request transfer
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// ------------------------------------------------------- categories, stores, items

function SetupPanels() {
  const { can } = useAuth()
  const [creating, setCreating] = useState<'category' | 'store' | 'item' | null>(null)

  if (!can('INVENTORY_MANAGE')) return null

  return (
    <Panel
      title="Catalogue"
      description="Categories, stores and the items they hold."
    >
      <div className="flex flex-wrap gap-2">
        <Button
          variant={creating === 'category' ? 'primary' : 'secondary'}
          size="sm"
          onClick={() => setCreating(creating === 'category' ? null : 'category')}
        >
          New category
        </Button>
        <Button
          variant={creating === 'store' ? 'primary' : 'secondary'}
          size="sm"
          onClick={() => setCreating(creating === 'store' ? null : 'store')}
        >
          New store
        </Button>
        <Button
          variant={creating === 'item' ? 'primary' : 'secondary'}
          size="sm"
          onClick={() => setCreating(creating === 'item' ? null : 'item')}
        >
          New item
        </Button>
      </div>

      {creating === 'category' && <CategoryForm onDone={() => setCreating(null)} />}
      {creating === 'store' && <StoreForm onDone={() => setCreating(null)} />}
      {creating === 'item' && <ItemForm onDone={() => setCreating(null)} />}
    </Panel>
  )
}

function CategoryForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const parents = useQuery({
    queryKey: ['inventory', 'categories'],
    queryFn: () => api<Category[]>('/api/v1/inventory/categories'),
  })

  const create = useMutation({
    mutationFn: (payload: CreateCategory) => post<Category>('/api/v1/inventory/categories', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      onDone()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <form
      className="mt-4 grid gap-4 border-t border-border pt-4 sm:grid-cols-2 lg:grid-cols-4"
      onSubmit={(event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        create.mutate({
          code: String(data.get('code') ?? ''),
          name: String(data.get('name') ?? ''),
          parentId: String(data.get('parentId') ?? '') || undefined,
          description: String(data.get('description') ?? '') || undefined,
        })
      }}
    >
      {error && (
        <p role="alert" className="sm:col-span-2 lg:col-span-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <Field label="Code" required>
        <TextInput name="code" required placeholder="LAB" />
      </Field>
      <Field label="Name" required>
        <TextInput name="name" required placeholder="Laboratory" />
      </Field>
      <Field label="Parent">
        <Select name="parentId">
          <option value="">No parent</option>
          {(parents.data ?? []).map((parent) => (
            <option key={parent.id} value={parent.id}>
              {parent.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Description">
        <TextInput name="description" />
      </Field>
      <div className="sm:col-span-2 lg:col-span-4">
        <Button type="submit" loading={create.isPending}>
          Add category
        </Button>
      </div>
    </form>
  )
}

function StoreForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const campuses = useQuery({
    queryKey: ['inventory', 'campuses'],
    queryFn: () => api<PageResponse<{ id: string; name: string }>>('/api/v1/academic/campuses', {
        query: {
          page: 0,
          size: 100,
        },
    }),
  })

  const create = useMutation({
    mutationFn: (payload: CreateStore) => post<Store>('/api/v1/inventory/stores', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      onDone()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <form
      className="mt-4 grid gap-4 border-t border-border pt-4 sm:grid-cols-2 lg:grid-cols-3"
      onSubmit={(event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        create.mutate({
          code: String(data.get('code') ?? ''),
          name: String(data.get('name') ?? ''),
          campusId: String(data.get('campusId') ?? '') || undefined,
          address: String(data.get('address') ?? '') || undefined,
          phone: String(data.get('phone') ?? '') || undefined,
        })
      }}
    >
      {error && (
        <p role="alert" className="sm:col-span-2 lg:col-span-3 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <Field label="Code" required>
        <TextInput name="code" required placeholder="MAIN" />
      </Field>
      <Field label="Name" required>
        <TextInput name="name" required placeholder="Main store" />
      </Field>
      <Field label="Campus">
        <Select name="campusId">
          <option value="">Any campus</option>
          {(campuses.data?.data ?? []).map((campus) => (
            <option key={campus.id} value={campus.id}>
              {campus.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Address">
        <TextInput name="address" />
      </Field>
      <Field label="Phone">
        <TextInput name="phone" />
      </Field>
      <div className="sm:col-span-2 lg:col-span-3">
        <Button type="submit" loading={create.isPending}>
          Add store
        </Button>
      </div>
    </form>
  )
}

function ItemForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['inventory', 'categories'],
    queryFn: () => api<Category[]>('/api/v1/inventory/categories'),
  })

  const create = useMutation({
    mutationFn: (payload: CreateItem) => post<Item>('/api/v1/inventory/items', payload),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      onDone()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <form
      className="mt-4 grid gap-4 border-t border-border pt-4 sm:grid-cols-2 lg:grid-cols-4"
      onSubmit={(event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const reorderLevel = String(data.get('reorderLevel') ?? '')
        const reorderQuantity = String(data.get('reorderQuantity') ?? '')
        create.mutate({
          code: String(data.get('code') ?? ''),
          name: String(data.get('name') ?? ''),
          categoryId: String(data.get('categoryId') ?? '') || undefined,
          unit: String(data.get('unit') ?? 'PIECE'),
          description: String(data.get('description') ?? '') || undefined,
          reorderLevel: reorderLevel ? Number(reorderLevel) : 0,
          reorderQuantity: reorderQuantity ? Number(reorderQuantity) : 0,
          batchTracked: data.get('batchTracked') === 'on',
          expiryTracked: data.get('expiryTracked') === 'on',
          active: true,
        })
      }}
    >
      {error && (
        <p role="alert" className="sm:col-span-2 lg:col-span-4 rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}
      <Field label="Code" required>
        <TextInput name="code" required placeholder="CHLK-001" />
      </Field>
      <Field label="Name" required>
        <TextInput name="name" required placeholder="Chalk box" />
      </Field>
      <Field label="Category">
        <Select name="categoryId">
          <option value="">No category</option>
          {(categories.data ?? []).map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Unit" required>
        <Select name="unit" defaultValue="PIECE">
          {UNITS.map((unit) => (
            <option key={unit} value={unit}>
              {unit.toLowerCase()}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Reorder level" hint="Warn when stock falls to this.">
        <TextInput name="reorderLevel" type="number" min="0" step="0.01" />
      </Field>
      <Field label="Reorder quantity" hint="Suggested amount to buy.">
        <TextInput name="reorderQuantity" type="number" min="0" step="0.01" />
      </Field>
      <Field label="Description">
        <TextInput name="description" />
      </Field>
      <div className="flex items-end gap-4 pb-1 text-sm text-ink">
        <label className="flex items-center gap-2">
          <input type="checkbox" name="batchTracked" />
          Batch tracked
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" name="expiryTracked" />
          Expiry tracked
        </label>
      </div>
      <div className="sm:col-span-2 lg:col-span-4">
        <Button type="submit" loading={create.isPending}>
          Add item
        </Button>
      </div>
    </form>
  )
}