export type AccountGroup = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'

export interface Category {
  id: string
  code: string
  name: string
  parentId: string | null
  parentName: string | null
  description: string | null
}

export interface Store {
  id: string
  code: string
  name: string
  campusId: string | null
  campusName: string | null
  keeperUserId: string | null
  keeperName: string | null
  address: string | null
  phone: string | null
  active: boolean
}

export interface Item {
  id: string
  code: string
  name: string
  description: string | null
  categoryId: string | null
  categoryName: string | null
  unit: string
  reorderLevel: number
  reorderQuantity: number
  batchTracked: boolean
  expiryTracked: boolean
  active: boolean
  totalOnHand: number
  needsReorder: boolean
}

export interface StockLevel {
  itemId: string
  itemCode: string
  itemName: string
  unit: string
  storeId: string
  storeName: string
  quantity: number
  averageCost: number
  stockValue: number
  lastMovementAt: string | null
}

export type MovementType =
  | 'PURCHASE'
  | 'RECEIPT'
  | 'ISSUE'
  | 'RETURN'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'ADJUSTMENT'
  | 'WASTAGE'

export interface Movement {
  id: string
  itemId: string
  itemCode: string
  itemName: string
  storeId: string | null
  storeName: string | null
  type: MovementType
  quantity: number
  signedQuantity: number
  balanceAfter: number
  unitCost: number | null
  referenceType: string | null
  referenceId: string | null
  reason: string | null
  movedAt: string
  movedBy: string | null
}

export interface Stocktake {
  itemId: string
  itemCode: string
  itemName: string
  previousQuantity: number
  countedQuantity: number
  difference: number
}

export type PurchaseStatus = 'DRAFT' | 'ORDERED' | 'PARTIAL' | 'RECEIVED' | 'CANCELLED'

export interface PurchaseLine {
  id: string
  itemId: string
  itemCode: string
  itemName: string
  unit: string
  quantityOrdered: number
  quantityReceived: number
  outstanding: number
  unitCost: number | null
  lineTotal: number
  fullyReceived: boolean
}

export interface Purchase {
  id: string
  purchaseNumber: string
  supplierName: string
  supplierContact: string | null
  storeId: string | null
  storeName: string | null
  status: PurchaseStatus
  orderedOn: string | null
  expectedOn: string | null
  receivedOn: string | null
  subtotal: number
  taxAmount: number
  otherCosts: number
  totalAmount: number
  notes: string | null
  items: PurchaseLine[]
}

export type RecipientType = 'DEPARTMENT' | 'CLASS' | 'COURSE' | 'STUDENT' | 'EMPLOYEE' | 'USER' | 'EXTERNAL'

export interface Issue {
  id: string
  issueNumber: string
  itemId: string
  itemCode: string
  itemName: string
  unit: string
  storeId: string | null
  storeName: string | null
  quantity: number
  issuedToType: RecipientType
  issuedToId: string | null
  issuedToName: string | null
  reason: string | null
  status: 'ISSUED' | 'RETURNED' | 'CANCELLED'
  issuedAt: string
  returnedAt: string | null
}

export type TransferStatus = 'REQUESTED' | 'DISPATCHED' | 'COMPLETED' | 'CANCELLED'

export interface Transfer {
  id: string
  transferNumber: string
  itemId: string
  itemCode: string
  itemName: string
  fromStoreId: string | null
  fromStoreName: string | null
  toStoreId: string | null
  toStoreName: string | null
  quantity: number
  status: TransferStatus
  reason: string | null
  requestedAt: string
  dispatchedAt: string | null
  receivedAt: string | null
}

export interface LowStock {
  itemId: string
  itemCode: string
  itemName: string
  unit: string
  totalOnHand: number
  reorderLevel: number
  suggestedOrderQuantity: number
}

export interface Overview {
  itemCount: number
  categoryCount: number
  storeCount: number
  purchaseCount: number
  openIssueCount: number
  inTransitCount: number
  lowStockCount: number
  stockValue: number
}

export interface CreateCategory {
  code: string
  name: string
  parentId?: string
  description?: string
}

export interface CreateStore {
  code: string
  name: string
  campusId?: string
  keeperUserId?: string
  address?: string
  phone?: string
}

export interface CreateItem {
  code: string
  name: string
  description?: string
  categoryId?: string
  unit: string
  reorderLevel: number
  reorderQuantity: number
  batchTracked: boolean
  expiryTracked: boolean
  /** The API stores this as a plain boolean, so an omitted flag means inactive. */
  active: boolean
}

export interface PurchaseLineInput {
  itemId: string
  quantityOrdered: number
  unitCost: number
}

export interface CreatePurchase {
  supplierName: string
  supplierContact?: string
  storeId: string
  expectedOn?: string
  taxAmount: number
  otherCosts: number
  notes?: string
  items: PurchaseLineInput[]
}

export interface ReceiveLineInput {
  purchaseItemId: string
  quantityReceived: number
}

export interface ReceivePurchase {
  storeId: string
  receivedOn?: string
  lines: ReceiveLineInput[]
}

export interface CreateIssue {
  itemId: string
  storeId: string
  quantity: number
  issuedToType: RecipientType
  issuedToId?: string
  issuedToName: string
  reason?: string
}

export interface CreateTransfer {
  itemId: string
  fromStoreId: string
  toStoreId: string
  quantity: number
  reason?: string
}

export interface StockAdjustment {
  storeId: string
  itemId: string
  countedQuantity: number
  reason?: string
}

export type UpdateCategory = CreateCategory
export type UpdateStore = CreateStore
export type UpdateItem = CreateItem
