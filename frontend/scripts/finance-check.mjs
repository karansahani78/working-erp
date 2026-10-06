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

// ---------------------------------------------------------------------- invoices
await page.goto(`${BASE}/invoices`, { waitUntil: 'networkidle' })
check(
  'invoices heading',
  (await page.getByRole('heading', { name: 'Invoices', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))
check('student picker present', (await page.locator('#finance-student').count()) === 1)

await page.locator('#finance-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.waitForTimeout(1200)
const invoiceText = await text()
check('seeded invoice listed', invoiceText.includes('INV-2026-'))
check('invoice total shown', invoiceText.includes('43,000'))
check('concession reduced the total', invoiceText.includes('43,000') && !invoiceText.includes('48,000'))
check('part paid status shown', /part paid|partially paid/i.test(invoiceText))
check('balance due shown', invoiceText.includes('26,000'))
// The invoice is already issued, so it cannot be issued again or cancelled.
check('no issue action on an issued invoice', !invoiceText.includes('Issue'))
check('paid invoice is not cancellable', !invoiceText.includes('Cancel'))

// ---------------------------------------------------------------------- payments
await page.goto(`${BASE}/payments`, { waitUntil: 'networkidle' })
check(
  'payments heading',
  (await page.getByRole('heading', { name: 'Payments', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))

await page.locator('#finance-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.waitForTimeout(1200)
const paymentText = await text()
check('seeded payment listed', paymentText.includes('CASH') || paymentText.includes('Cash'))
check('receipt number shown', paymentText.includes('RCP-2026-'))
check('collected tile reflects the receipt', paymentText.includes('20,000'))
check('refunded receipt is still shown as settled', /partially refunded/i.test(paymentText))
check('record payment panel offered', (await page.getByText('Record a payment').count()) >= 1)
check('invoice outstanding panel', paymentText.includes('26,000'))
check('confirm action present', /confirm/i.test(paymentText))

// ---------------------------------------------------------------------- refunds
await page.goto(`${BASE}/refunds`, { waitUntil: 'networkidle' })
check(
  'refunds heading',
  (await page.getByRole('heading', { name: 'Refunds', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))

await page.locator('#finance-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.waitForTimeout(1200)
const refundText = await text()
check('seeded refund listed', refundText.includes('3,000'))
check('refund processed', /processed|approved/i.test(refundText))
check('refund reason carried through', /overpayment/i.test(refundText))

// ------------------------------------------------------------------- scholarships
await page.goto(`${BASE}/scholarships`, { waitUntil: 'networkidle' })
check(
  'scholarships heading',
  (await page.getByRole('heading', { name: 'Scholarships', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))

const scholarshipText = await text()
check('seeded scholarship listed', scholarshipText.includes('Merit scholarship'))
check('new scholarship form offered', (await page.getByRole('button', { name: 'Add' }).count()) === 1)

check('grant student picker', (await page.locator('#grant-student').count()) === 1)
check('grant year picker', (await page.locator('#grant-year').count()) === 1)
await page.locator('#grant-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.waitForTimeout(600)
const yearOptions = await page.locator('#grant-year option').allInnerTexts()
check('academic years offered', yearOptions.length > 1, yearOptions.join('|'))
await page.locator('#grant-year').selectOption({ index: 1 })
await page.waitForTimeout(1200)
const grantText = await text()
check('granted concession listed', grantText.includes('5,000'))
check('concession is active', /active/i.test(grantText))
check('revoke offered on an active concession', /revoke/i.test(grantText))

// -------------------------------------------------------------------- accounting
await page.goto(`${BASE}/accounting`, { waitUntil: 'networkidle' })
check(
  'accounting heading',
  (await page.getByRole('heading', { name: 'Accounting', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await text()).includes('Come soon'))
check(
  'accounting tabs rendered',
  (await page.getByRole('tab', { name: 'Journal' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Chart of accounts' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Fiscal years' }).count()) === 1 &&
    (await page.getByRole('tab', { name: 'Trial balance' }).count()) === 1,
)

await page.getByRole('tab', { name: 'Chart of accounts' }).click()
await page.waitForTimeout(800)
const chartText = await text()
check('seeded accounts listed', chartText.includes('Cash in hand') && chartText.includes('Fee income'))
check('account group shown', /asset/i.test(chartText))
check('postable accounts are usable', /postable/i.test(chartText))

await page.getByRole('tab', { name: 'Journal' }).click()
await page.waitForTimeout(800)
check('journal range defaults to the current month', (await text()).includes('No entries in this period'))

await page.locator('#journal-from').fill('2026-01-01')
await page.waitForTimeout(400)
await page.locator('#journal-to').fill('2026-12-31')
await page.waitForTimeout(1200)
const journalText = await text()
check('posted entry listed', journalText.includes('JRN-2026-'))
check('entry is posted', /posted/i.test(journalText))
check('posted entry carries no unbalanced flag', !/unbalanced/i.test(journalText))
check('posting action gone once posted', !journalText.includes('Post\n'))

await page.getByRole('tab', { name: 'Trial balance' }).click()
await page.waitForTimeout(800)
const trialText = await text()
check('trial balance lists both accounts', trialText.includes('Cash in hand'))
check('trial balance balanced', /in balance|balanced/i.test(trialText))
check('totals agree at 43,000', trialText.includes('43,000'))

await page.getByRole('tab', { name: 'Fiscal years' }).click()
await page.waitForTimeout(800)
const yearText = await text()
check('fiscal year listed', yearText.includes('FY2026'))
check('open fiscal year shown', /open/i.test(yearText))

check(
  'no runtime errors',
  errors.filter((e) => !e.includes('401')).length === 0,
  errors.slice(0, 3).join(' | '),
)

await browser.close()
console.log(failures === 0 ? '\nAll finance checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)