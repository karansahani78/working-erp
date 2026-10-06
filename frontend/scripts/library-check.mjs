import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
let failures = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ` — ${extra}` : ''}`)
  if (!ok) failures += 1
}

const stamp = Date.now().toString().slice(-6)
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } })
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
const tab = async (name) => {
  await page.getByRole('tab', { name, exact: true }).click()
  await page.waitForTimeout(1200)
}

// --------------------------------------------------------------------- members
await page.goto(`${BASE}/library`, { waitUntil: 'networkidle' })
await tab('Members')
const memberCountBefore = (await page.locator('tbody tr').count())

const newMember = `Lend Test ${stamp}`
await page.getByRole('button', { name: /member/i }).first().click().catch(() => {})
await page.waitForTimeout(600)
const nameInput = page.locator('main input').filter({ hasNot: page.locator('[type=file]') }).first()
await nameInput.fill(newMember).catch(() => {})
const externalPhone = page.locator('input[type=tel], input[placeholder*=phone i]').first()
if (await externalPhone.count()) await externalPhone.fill(`98${stamp}`)
await page.getByRole('button', { name: /Add|Register|Catalogue member/i }).last().click()
await page.waitForTimeout(2000)
check('a member can be registered', (await text()).includes(newMember)
  || (await page.locator('tbody tr').count()) > memberCountBefore,
  newMember)

await page.getByLabel('Member status').selectOption('ACTIVE')
await page.waitForTimeout(1200)

// ----------------------------------------------------------------------- lend
// The desk used to offer a bare "Lend" button that sent no member, so lending to anybody
// but the librarian's own card could not be done from here at all.
await tab('Catalogue')
await page.getByLabel(/^Title/).fill(`Lend Desk ${stamp}`)
await page.getByRole('button', { name: 'Catalogue book' }).click()
await page.waitForTimeout(2000)
const bookRow = page.locator('tbody tr').filter({ hasText: `Lend Desk ${stamp}` })
check('the book is catalogued', (await bookRow.count()) === 1)

await bookRow.getByRole('button', { name: 'Copies' }).click()
await page.waitForTimeout(1500)
await page.getByRole('button', { name: 'Add copy' }).click()
await page.waitForTimeout(2000)
// The row that can actually be lent is the one carrying a Lend button.
const lendable = page.locator('tbody tr').filter({ has: page.getByRole('button', { name: 'Lend' }) })
check('a copy is registered and lendable', (await lendable.count()) >= 1, `${await lendable.count()} lendable`)
const barcode = (await lendable.first().innerText()).match(/[A-Z]{2}-\d{4,}/)?.[0] ?? ''
check('the copy has a barcode', barcode.length > 3, barcode)
check('the copy starts available', /Available/i.test(await lendable.first().innerText()))

await lendable.first().getByRole('button', { name: 'Lend' }).click()
await page.waitForTimeout(600)
check('lending asks who is borrowing', /Lend/i.test(await text()))
const memberSearch = page.locator('#lend-member-search')
check('there is a box to look the member up in', (await memberSearch.count()) === 1)
const memberSelect = page.locator('#lend-member')
check('there is a member to choose', (await memberSelect.count()) === 1)

await memberSearch.fill(newMember)
await page.waitForTimeout(1500)
const options = await memberSelect.locator('option').allTextContents()
check('searching finds the member by name', options.some((o) => o.includes(newMember)),
  options.join(' | ').slice(0, 120))
check('the choice shows how many books they already have', options.some((o) => /\d+\/\d+ out/.test(o)),
  options[1] ?? 'no options')

const code = options.find((o) => o.includes(newMember))?.match(/·\s*(\S+)/)?.[1]
check('the card number is shown with the name', Boolean(code), code ?? 'none')

await page.locator('#lend-member-search').fill('zzz-no-such-member')
await page.waitForTimeout(1500)
check('a search that matches nobody says so',
  /No active member matches/.test(await text()) || (await page.locator('#lend-member option').count()) === 1)

await page.locator('#lend-member-search').fill(newMember)
await page.waitForTimeout(1500)
await page.selectOption('#lend-member', { label: options.find((o) => o.includes(newMember)) })
await page.locator('#lend-days').fill('21')
await page.getByRole('button', { name: 'Lend the book' }).click()
await page.waitForTimeout(2500)

const afterLend = await text()
check('the copy is marked as issued', /Issued/i.test(afterLend), barcode)
check('the lending form closes once the book is out',
  (await page.locator('#lend-member').count()) === 0)

// --------------------------------------------------------------- circulation
await tab('Circulation')
check('loans are shown one member at a time', /Choose a member/.test(await text()))
await page.getByLabel('Member', { exact: true }).selectOption({ label: `${newMember} (${code})` })
await page.waitForTimeout(2000)

const loanRow = page.locator('tbody tr').filter({ hasText: `Lend Desk ${stamp}` })
check('the loan is listed under the borrower', (await loanRow.count()) === 1)
check('the book that went out is named', (await loanRow.innerText()).includes(`Lend Desk ${stamp}`))
check('the copy that went out is named', (await loanRow.innerText()).includes(barcode))
check('the due date is shown', (await loanRow.innerText()).match(/\w+ \d+/g)?.length >= 1,
  (await loanRow.innerText()).split('\n').join(' | '))
check('the loan starts as issued', /Issued/i.test(await loanRow.innerText()))
check('a loan can be renewed', (await loanRow.getByRole('button', { name: 'Renew' }).count()) === 1)

// --------------------------------------------------------------- give it back
await loanRow.getByRole('button', { name: 'Return' }).click()
await page.waitForTimeout(2500)
// History keeps the loan rather than dropping it, so the check is that it changed state.
check('the loan is marked returned',
  /Returned/i.test(await loanRow.innerText()), (await loanRow.innerText()).split('\n').join(' | '))
check('a returned loan cannot be returned again',
  (await loanRow.getByRole('button', { name: 'Return' }).count()) === 0)

await tab('Catalogue')
await bookRow.getByRole('button', { name: 'Copies' }).click()
await page.waitForTimeout(1500)
const barcodeCell = page.locator('td').filter({ hasText: new RegExp(`^${barcode}$`) })
const copyState = await barcodeCell.first().locator('..').innerText()
check('the copy is back on the shelf', /Available/i.test(copyState), copyState.split('\n').join(' | '))
check('the copy can be lent again',
  (await barcodeCell.first().locator('..').getByRole('button', { name: 'Lend' }).count()) === 1)

await tab('Overview')
const overview = await text()
check('the overview still loads', /Library/i.test(overview))
check('no runtime errors', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(failures === 0 ? '\nAll library checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
