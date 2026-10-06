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

// ---------------------------------------------------------------------- register
await page.goto(`${BASE}/assets`, { waitUntil: 'networkidle' })
const heading = await text()
check('assets heading', (await page.getByRole('heading', { name: 'Assets', level: 1 }).count()) === 1)
check('no placeholder copy left', !heading.includes('Come soon'))
check('overview counts render', /purchase cost/i.test(heading) && /book value/i.test(heading))
check('seeded assets listed', /AST-0000/.test(heading), heading.replace(/\n+/g, ' ').slice(0, 90))
// Count these one at a time: a locator's count() is a promise, so folding it into
// every() would compare a promise to a number and quietly always fail.
const tabCounts = {}
for (const label of ['Register', 'Custody', 'Maintenance', 'Depreciation', 'Categories']) {
  tabCounts[label] = await page.getByRole('tab', { name: label, exact: true }).count()
}
check('all five tabs present', Object.values(tabCounts).every((n) => n === 1),
  Object.entries(tabCounts).filter(([, n]) => n !== 1).map(([l, n]) => `${l}=${n}`).join(' '))

const drawer = page.getByRole('dialog')
check('drawer starts closed', (await drawer.count()) === 0)

// Open a named asset rather than whatever sorts first, so these checks mean something
// whether the register is sorted by name, number or status.
const rowFor = (label) =>
  page.locator('tbody tr').filter({ hasText: label })

const openRow = async (label) => {
  await rowFor(label).getByRole('button', { name: 'Open' }).click()
  await drawer.waitFor({ timeout: 10000 })
  await drawer.getByText('Custody history').waitFor({ timeout: 10000 })
  return drawer.innerText()
}

let panel = await openRow('AST-00001')
check('asset drawer opens', /AST-00001/.test(panel))
check('drawer shows value and condition', /worth now/i.test(panel) && /condition/i.test(panel))
check('custody history shown', /custody history/i.test(panel))
check('servicing shown', /servicing/i.test(panel))
check('an assigned asset offers a return', /Take it back from/i.test(panel))
check('an assigned asset does not offer servicing booking', !/Book servicing/i.test(panel))
await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

// A second asset, available, so the servicing form should be there instead.
panel = await openRow('AST-00004')
check('an available asset offers servicing booking', /Book servicing/i.test(panel))
check('an available asset does not offer a return', !/Take it back from/i.test(panel))
await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

// ----------------------------------------------------------------------- filters
await page.fill('#asset-term', 'projector')
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1200)
const filtered = await text()
check('search narrows the register', /projector/i.test(filtered), filtered.replace(/\n+/g, ' ').slice(0, 80))
check('search excludes other assets', !/Bolero pickup/i.test(filtered))

await page.fill('#asset-term', '')
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1000)
await page.selectOption('#asset-status', 'ASSIGNED')
await page.waitForTimeout(1200)
const assigned = await text()
check('status filter works', /Assigned/i.test(assigned))
check('assigned filter hides available assets', !/Bolero pickup/i.test(assigned))
await page.selectOption('#asset-status', '')
await page.waitForTimeout(1000)

// ------------------------------------------------------------------------ custody
await page.getByRole('tab', { name: 'Custody' }).click()
await page.waitForTimeout(1200)
let custody = await text()
check('custody offers a handover', /hand an asset over/i.test(custody))
check('holder type picker offered', /holder type/i.test(custody))
check('seeded holders listed', /Rajesh Karki|Asha Rai/.test(custody))

await page.getByRole('button', { name: 'Hand an asset over' }).click()
await page.waitForTimeout(900)
custody = await text()
check('handover form opens', /Hand over/.test(custody))
check('handover lists only available assets', /Choose an available asset/i.test(custody))
const options = await page.locator('#assign-asset option').allTextContents()
check('assigned assets are not offered for handover',
  !options.some((o) => /projector|laptop|desk/i.test(o)), options.join(' | ').slice(0, 80))
await page.getByRole('button', { name: 'Close handover' }).click()
await page.waitForTimeout(500)

// -------------------------------------------------------------------- maintenance
await page.getByRole('tab', { name: 'Maintenance' }).click()
await page.waitForTimeout(1200)
let jobs = await text()
check('maintenance list shown', /pickup|printer/i.test(jobs), jobs.replace(/\n+/g, ' ').slice(0, 90))
check('a scheduled job can be started', /Start/.test(jobs))
check('an in-progress job can be completed', /Complete/.test(jobs))
check('an open job can be cancelled', /Cancel/.test(jobs))
check('vendor shown', /Techno Print|Sundar Motors/.test(jobs))

await page.selectOption('#job-status', 'COMPLETED')
await page.waitForTimeout(1200)
jobs = await text()
check('completed filter shows nothing yet', /No servicing has been booked/i.test(jobs))

// ------------------------------------------------------------------- depreciation
await page.getByRole('tab', { name: 'Depreciation' }).click()
await page.waitForTimeout(1200)
const value = await text()
check('depreciation table shown', /Worth now/i.test(value))
check('rate shown as a percentage', /\d+(\.\d+)?%/.test(value))
check('book value total shown', /book value/i.test(value))
check('expired warranty is called out', /Expired/i.test(value), value.replace(/\n+/g, ' ').slice(0, 80))
// The table carries the rate on each asset, not the category it came from.
check('assets show their depreciation rate', /\d+(\.\d+)?%/.test(value), value.replace(/\n+/g, ' ').slice(0, 90))
check('every seeded asset is valued', ['AST-00001', 'AST-00002', 'AST-00003', 'AST-00004', 'AST-00005']
  .every((n) => value.includes(n)))

// ------------------------------------------------- lost and written off
// A throwaway asset, because writing off a seeded one would retire seed data for every
// later run of this check.
const burner = `Bunsen burner ${Date.now()}`
await page.goto(`${BASE}/assets`, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: 'New asset' }).click()
await page.fill('#asset-name', burner)
await page.getByRole('button', { name: 'Record asset' }).click()
await page.waitForTimeout(1500)

await page.fill('#asset-term', burner)
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1200)
check('throwaway asset recorded', (await rowFor(burner).count()) === 1)

panel = await openRow(burner)
check('an asset on the shelf can be reported missing', /Report it missing/i.test(panel))
check('an asset on the shelf can be written off', /Write it off/i.test(panel))
check('an asset that is not missing cannot be found', !/Found it again/i.test(panel))

await page.fill('#lost-reason', 'Left in the lab')
await page.getByRole('button', { name: 'Report missing', exact: true }).click()
await page.waitForTimeout(1500)
panel = await drawer.innerText()
check('asset reported missing', /\bLost\b/.test(panel), panel.replace(/\n+/g, ' ').slice(0, 90))
check('the reason is kept on the row', /Left in the lab/.test(panel))
check('the loss is dated', /Missing since/i.test(panel))
check('a missing asset offers the way back', /Found it again/i.test(panel))
check('a missing asset cannot be reported missing twice', !/Report it missing/i.test(panel))
// A missing asset is exactly the one most likely to be written off, so it stays on offer.
check('a missing asset can still be written off', /Write it off/i.test(panel))

await page.fill('#found-location', 'Lab 2')
await page.getByRole('button', { name: 'Put back on the shelf' }).click()
await page.waitForTimeout(1500)
panel = await drawer.innerText()
check('the asset is back on the shelf', /Available/.test(panel))
check('it turned up somewhere recorded', /Lab 2/.test(panel))
check('the story of the loss is kept after it is found', /Left in the lab/.test(panel))
check('a recovered asset does not offer the way back again', !/Found it again/i.test(panel))

await page.selectOption('#disposal-method', 'SCRAPPED')
await page.fill('#disposal-value', '25')
await page.fill('#disposal-notes', 'Cracked beyond repair')
await page.getByRole('button', { name: 'Write off', exact: true }).click()
await page.waitForTimeout(1500)
panel = await drawer.innerText()
check('the asset is written off', /Disposed/.test(panel))
check('how it went is shown', /Scrapped/.test(panel), panel.replace(/\n+/g, ' ').slice(0, 120))
check('what it went for is shown', /25/.test(panel))
check('a disposed asset offers no ending of its own',
  !/Write it off/i.test(panel) && !/Report it missing/i.test(panel))
await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

// The register keeps it: disposal is a soft ending, not a deletion.
await page.goto(`${BASE}/assets`, { waitUntil: 'networkidle' })
await page.fill('#asset-term', burner)
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1200)
const disposed = await text()
check('a written-off asset stays on the register', /Bunsen burner/.test(disposed))
check('the register shows it as disposed', /Disposed/i.test(disposed))
check('no runtime errors', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(failures === 0 ? '\nAll asset checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)