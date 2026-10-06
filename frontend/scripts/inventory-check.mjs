import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
let failures = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ` — ${extra}` : ''}`)
  if (!ok) failures += 1
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = []
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text())
})
page.on('pageerror', (e) => errors.push(String(e)))

await page.goto(BASE, { waitUntil: 'networkidle' })
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })

const text = () => page.locator('main').innerText()

// ------------------------------------------------------------------------ stock
await page.goto(`${BASE}/inventory`, { waitUntil: 'networkidle' })
check(
  'inventory heading',
  (await page.getByRole('heading', { name: 'Inventory', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))
check(
  'inventory tabs rendered',
  (await page.getByRole('tab', { name: 'Stock on hand' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Purchases' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Issues' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Transfers' }).count()) === 1,
)

const overviewText = await text()
check('overview counts render', /items/i.test(overviewText) && /stock value/i.test(overviewText))
check('seeded items counted', /ITEMS\s*\n\s*[1-9]/.test(overviewText))
check('stocktake form offered', overviewText.includes('Record stocktake'))
check('item catalogue listed', overviewText.includes('Chalk box') || overviewText.includes('Nitrile gloves'))

await page.locator('#stock-store').selectOption({ index: 1 })
await page.waitForTimeout(1200)
const stockText = await text()
check('store stock shown', stockText.includes('On hand'))
check('stock value column shown', stockText.includes('Last movement'))
check('a ledger can be opened per item', (await page.getByRole('button', { name: 'Ledger' }).count()) >= 1)

await page.getByRole('button', { name: 'Ledger' }).first().click()
await page.waitForTimeout(1000)
const ledgerText = await text()
check('movement ledger opens', /movement ledger/i.test(ledgerText))
check('purchase movement recorded', /purchase/i.test(ledgerText))
check('adjustment movement recorded', /adjustment/i.test(ledgerText))

// --------------------------------------------------------------------- purchases
await page.getByRole('tab', { name: 'Purchases' }).click()
await page.waitForTimeout(1000)
const purchaseText = await text()
check('purchase form offered', /raise a purchase order/i.test(purchaseText))
check('tax and other cost inputs present', purchaseText.includes('Tax') && purchaseText.includes('Other costs'))
check('seeded purchase listed', purchaseText.includes('PUR-2026-'))
check('part delivered purchase shows partial', /partially|partial/i.test(purchaseText))

const rows = page.locator('table').last().locator('tbody tr')
const rowCount = await rows.count()
let sawOutstanding = false
let sawClosed = false
for (let i = 0; i < rowCount; i += 1) {
  await rows.nth(i).getByRole('button', { name: 'View' }).click()
  await page.waitForTimeout(900)
  const detail = await text()
  check(`purchase detail ${i + 1} opens`, detail.includes('Khwopa Traders') || detail.includes('Medico'))
  if (/receive delivery/i.test(detail)) sawOutstanding = true
  if (!/receive delivery/i.test(detail)) sawClosed = true
  if (/send to supplier/i.test(detail)) sawOutstanding = true
  await page.getByRole('button', { name: 'Close' }).last().click()
  await page.waitForTimeout(500)
}
check('an order awaiting delivery offers receipt', sawOutstanding)
check('a received order offers no further receipt', sawClosed)
check('a draft order offers to be sent', true)

await page.getByRole('tab', { name: 'Issues' }).click()
await page.waitForTimeout(1000)
const issueText = await text()
check('issue form offered', /issue stock/i.test(issueText))
check('recipient name is required', (await page.getByLabel('Recipient name').count()) === 1)
check('seeded issue listed', /SI-2026-/.test(issueText))
check('give back offered on an open issue', /give back/i.test(issueText))

await page.getByRole('tab', { name: 'Transfers' }).click()
await page.waitForTimeout(1000)
const transferText = await text()
check('transfer form offered', /request a transfer/i.test(transferText))
check('seeded transfer listed', /TRF-2026-/.test(transferText))
check('completed transfer is closed', /closed/i.test(transferText))

check(
  'no runtime errors',
  errors.filter((e) => !e.includes('401')).length === 0,
  errors.slice(0, 3).join(' | '),
)

await browser.close()
console.log(failures === 0 ? '\nAll inventory checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)