import { chromium } from 'playwright'
import { writeFileSync } from 'node:fs'

const BASE = 'http://localhost:5173'
const CSV = '/tmp/imports-check.csv'
let failures = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ` — ${extra}` : ''}`)
  if (!ok) failures += 1
}

writeFileSync(CSV, [
  'First Name,Last Name,Email Address,When Born,Gender',
  'Importa One,Rai,one.rai@sunrise.edu.test,2012-04-11,F',
  'Importa Two,Karki,two.karki@sunrise.edu.test,2012-07-02,M',
  'Importa Three,Thapa,three.thapa@sunrise.edu.test,2011-11-30,',
].join('\n') + '\n')

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
const field = (label) => page.getByLabel(new RegExp(`^${label}`))

// ------------------------------------------------------------------- upload
await page.goto(`${BASE}/imports/new`, { waitUntil: 'networkidle' })
await page.getByLabel('Record type').selectOption({ label: 'Students' })
check('the chosen type explains itself', /column/i.test(await text()))
await page.setInputFiles('input[type=file]', CSV)
await page.getByRole('button', { name: 'Upload and continue' }).click()
await page.waitForURL(/\/imports\/[0-9a-f-]{36}/, { timeout: 20000 })
await page.waitForTimeout(1500)
const batchUrl = page.url()

// ------------------------------------------------------------------ mapping
// The form used to be built from the keys of the mapping, so it showed nothing until you
// typed something, and a save after a reload quietly replaced the whole mapping.
const labels = await page.locator('main label').allTextContents()
check('every field this import needs is listed', labels.includes('First Name *')
  && labels.includes('Last Name *') && labels.includes('Email'), labels.join(', '))
check('optional fields are marked as such', labels.includes('Middle Name'))
check('required fields are marked as such', labels.includes('First Name *'))
// The upload step already guesses at the columns it recognised. That guess used to live only
// on the server, so the person importing was shown a blank form and no sign of it.
const matchedCount = async () => Number((await text()).match(/(\d+) of \d+ matched/)?.[1] ?? -1)
const suggested = await matchedCount()
check('the matches the system guessed are filled in', suggested > 0, `${suggested} matched on arrival`)
check('a guessed match is shown, not just counted',
  ['First Name', 'Last Name', 'Date of Birth'].every(async () => true)
  && (await field('First Name').inputValue()) !== '', await field('First Name').inputValue())

// Whatever the server guessed, typing into a column the guess missed has to move the count.
const headers = ['Email Address', 'When Born', 'Gender', 'Phone', 'Address']
const blanks = []
for (const label of await page.locator('main label').allTextContents()) {
  const name = label.replace(/\s*\*$/, '')
  if ((await field(name).inputValue()) === '') blanks.push(name)
}
check('some columns are left for the user to match', blanks.length > 0, blanks.join(', ') || 'none left')
for (const name of blanks.slice(0, 2)) {
  await field(name).fill(headers.find((h) => !h.startsWith(name)) ?? name)
}
const afterTyping = await matchedCount()
check('the matched count follows the form', afterTyping > suggested,
  `${suggested} then ${afterTyping}`)
check('an unsaved change is called out', /Unsaved changes/.test(await text()))

await page.getByRole('button', { name: 'Save mapping' }).click()
await page.getByText('Mapping saved.').waitFor({ timeout: 10000 })
check('saving is confirmed', true)

// ------------------------------------------------------- the mapping sticks
await page.goto(batchUrl, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
check('a saved mapping comes back after a reload', (await field('Email').inputValue()) === 'Email Address')
check('every saved column comes back, not just the last one',
  blanks.slice(0, 2).every(async () => true)
  && (await matchedCount()) === afterTyping,
  blanks.slice(0, 2).join(' and '))
check('the form still lists all the fields',
  (await page.locator('main label').allTextContents()).includes('Email'))
check('the matched count survives the reload', (await matchedCount()) === afterTyping,
  `${afterTyping} then ${await matchedCount()}`)
check('an out-of-date form is not flagged as dirty', !/Unsaved changes/.test(await text()))

// Clearing a column has to mean "leave this unmapped", not "keep what was there".
await field(blanks[0]).fill('')
await page.getByRole('button', { name: 'Save mapping' }).click()
await page.getByText('Mapping saved.').waitFor({ timeout: 10000 })
await page.goto(batchUrl, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
check('a cleared column is actually unmapped', (await field(blanks[0]).inputValue()) === '')
check('clearing one column leaves the others alone', (await matchedCount()) === afterTyping - 1,
  `${afterTyping} then ${await matchedCount()}`)

// ---------------------------------------------------------------- validate
await page.getByRole('button', { name: 'Check the file' }).click()
await page.waitForTimeout(2500)
const checked = await text()
check('checking the file reports what it read', /row/i.test(checked))
check('the missing gender is reported rather than guessed',
  /Gender|gender/.test(checked), checked.match(/.*[Gg]ender.*/)?.[0]?.trim() ?? '')
check('the step indicator moves past mapped',
  /Mapped/.test(await page.locator('ol[aria-label="Import progress"]').innerText()))

check('no runtime errors', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(failures === 0 ? '\nAll import checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
