import { chromium } from 'playwright'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080/api/v1'

let failures = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ` \u2014 ${extra}` : ''}`)
  if (!ok) failures += 1
}

// The read-only user below is created here so the check can be run against a bare
// database, rather than depending on someone having set it up by hand.
async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json().catch(() => null)
  return { status: res.status, json }
}

const adminSession = await api('POST', '/auth/login', { loginId: 'admin', password: 'Institution123!' })
const adminToken = adminSession.json.data.accessToken

const READER = { username: 'docreader', password: 'Document123!', role: 'STAFF' }
const created = await api('POST', '/admin/users', {
  username: READER.username,
  password: READER.password,
  displayName: 'Doc Reader',
  email: 'docreader@sunrise.edu.test',
  phone: '+977-1-5550199',
  role: READER.role,
}, adminToken)
if (created.status === 201) console.log('SETUP  created the read-only user')
await api('POST', `/admin/users/${created.json?.data?.id ?? ''}/password`, { password: READER.password }, adminToken)
const users = await api('GET', '/admin/users?size=200', undefined, adminToken)
const readerId = users.json.data.data.find((u) => u.username === READER.username)?.id
if (!readerId) throw new Error('the read-only user was not created')

const staffHandbook = 'Staff handbook'
const grants = await api('GET', '/documents?term=' + encodeURIComponent(staffHandbook), undefined, adminToken)
const handbookId = grants.json.data.data[0]?.id
if (!handbookId) throw new Error(`${staffHandbook} is missing; seed the library first`)
// Grants ride along on the document detail; there is no separate listing endpoint.
const detail = await api('GET', `/documents/${handbookId}`, undefined, adminToken)
if (!detail.json.data.access.some((g) => g.principalUserId === readerId)) {
  await api('POST', `/documents/${handbookId}/access`, {
    principalType: 'USER', principalUserId: readerId, accessLevel: 'VIEW',
  }, adminToken)
  console.log('SETUP  granted the read-only user a view on the handbook')
}

const browser = await chromium.launch()

const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
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

// ----------------------------------------------------------------------- library
await page.goto(`${BASE}/documents`, { waitUntil: 'networkidle' })
const heading = await text()
check('documents heading', (await page.getByRole('heading', { name: 'Documents', level: 1 }).count()) === 1)
check('no placeholder copy left', !heading.includes('Not built yet'))
check('overview counts render', /on file/i.test(heading) && /verified/i.test(heading))
check('seeded documents listed', /DOC-2026/.test(heading), heading.replace(/\n+/g, ' ').slice(0, 90))
check('file sizes are readable', /\d+(\.\d+)?\s?(B|KB|MB)/.test(heading))
check('document types shown', /certificate|policy|minutes/i.test(heading))
check('verification states shown', /checked|unchecked|in review|rejected/i.test(heading))
check('an expired document is called out', /Expired/.test(heading))

// Counting locators one at a time: a locator count() is a promise, so folding it into
// every() compares a promise to a number and quietly always fails.
const tabCounts = {}
for (const label of ['Library', 'To review', 'Expiring']) {
  tabCounts[label] = await page.getByRole('tab', { name: label, exact: true }).count()
}
check('all three tabs present', Object.values(tabCounts).every((n) => n === 1),
  Object.entries(tabCounts).filter(([, n]) => n !== 1).map(([l, n]) => `${l}=${n}`).join(' '))

// ---------------------------------------------------------------------- filters
await page.fill('#document-term', 'insurance')
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1200)
let filtered = await text()
check('search narrows the library', /insurance/i.test(filtered))
check('search excludes other documents', !/Land deed/i.test(filtered))

await page.fill('#document-term', '')
await page.getByRole('button', { name: 'Search' }).click()
await page.waitForTimeout(1000)
await page.selectOption('#document-verification', 'VERIFIED')
await page.waitForTimeout(1200)
const checked = await text()
check('checked filter works', /Staff handbook/i.test(checked))
check('checked filter hides unchecked documents', !/Land deed/i.test(checked))

await page.getByRole('button', { name: 'Clear' }).click()
await page.waitForTimeout(1200)
const cleared = await text()
check('clearing the filters brings everything back', /Land deed/i.test(cleared) && /insurance/i.test(cleared))

// ------------------------------------------------------------------------ drawer
const rowFor = (label) => page.locator('tbody tr').filter({ hasText: label })
const drawer = page.getByRole('dialog')
check('drawer starts closed', (await drawer.count()) === 0)

await rowFor('DOC-2026-00004').getByRole('button', { name: 'Open' }).click()
await drawer.waitFor({ timeout: 10000 })
await drawer.getByRole('heading', { name: 'Versions' }).waitFor({ timeout: 10000 })
let panel = await drawer.innerText()
check('document drawer opens', /Staff handbook/i.test(panel) && /DOC-2026-00004/.test(panel))
check('drawer shows the checksum', /SHA-256/i.test(panel) && /[0-9a-f]{64}/.test(panel))
check('drawer shows the file', /Filename/i.test(panel) && /\.txt/.test(panel))
check('download offered', /Download/.test(panel))
check('a verified document can still be re-checked', /Approve/i.test(panel) && /Reject/i.test(panel))
check('access panel shown', /Who may read this/i.test(panel))
check('versions listed', /Versions/i.test(panel) && /v1/.test(panel))
check('metadata can be edited', /Edit details/i.test(panel))
check('a new version can be added', /Add a version/i.test(panel))
check('it can be taken out of circulation', /Take out of circulation/i.test(panel))

// Downloading really goes over the wire and comes back byte for byte.
const [download] = await Promise.all([
  page.waitForEvent('download', { timeout: 15000 }).catch(() => null),
  drawer.getByRole('button', { name: 'Download', exact: true }).click(),
])
check('download hands back a file', download !== null, download ? download.suggestedFilename() : 'no download event')

await drawer.getByRole('button', { name: 'Edit details' }).click()
await page.waitForTimeout(500)
check('the edit form opens with the current title',
  (await page.inputValue('#edit-title')) === 'Staff handbook')
await drawer.getByRole('button', { name: 'Cancel' }).click()
await page.waitForTimeout(400)

await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

// A document with two versions and grants, so the interesting panels have something in them.
await rowFor('DOC-2026-00002').getByRole('button', { name: 'Open' }).click()
await drawer.waitFor({ timeout: 10000 })
await drawer.getByRole('heading', { name: 'Versions' }).waitFor({ timeout: 10000 })
panel = await drawer.innerText()
check('both versions listed', /v2/.test(panel) && /v1/.test(panel))
check('a superseded version can still be opened', /Open this one/i.test(panel))
check('an old version carries its own change note', /Notarised copy/i.test(panel))
await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

await rowFor('DOC-2026-00005').getByRole('button', { name: 'Open' }).click()
await drawer.waitFor({ timeout: 10000 })
await drawer.getByRole('heading', { name: 'Who may read this' }).waitFor({ timeout: 10000 })
panel = await drawer.innerText()
check('existing grants listed', /Principal/i.test(panel) && /Head of Department|Hod/i.test(panel))
check('grant levels read as plain words', /View/.test(panel) && /Manage/.test(panel))
check('a grant can be taken back', /Take back/i.test(panel))
check('a new grant can be made', /Grant access/i.test(panel))
await page.getByRole('button', { name: 'Close' }).first().click()
await page.waitForTimeout(600)

// -------------------------------------------------------------------- to review
await page.getByRole('tab', { name: 'To review' }).click()
await page.waitForTimeout(1200)
const queue = await text()
check('review queue shown', /waiting to be checked/i.test(queue))
check('an unchecked document is in the queue', /Land deed/i.test(queue))
check('an already checked document is not', !/Staff handbook/i.test(queue))
check('each queue row can be opened', (await page.getByRole('button', { name: 'Review' }).count()) > 0)

// ---------------------------------------------------------------------- expiring
await page.getByRole('tab', { name: 'Expiring' }).click()
await page.waitForTimeout(1200)
let expiring = await text()
check('expiring list shown', /going out of date/i.test(expiring))
check('a document expiring soon is listed', /Vehicle insurance/i.test(expiring))
check('a far-off expiry is not listed', !/Land deed/i.test(expiring))

await page.selectOption('#expiry-days', '730')
await page.waitForTimeout(1200)
expiring = await text()
check('widening the window brings more in', /Land deed/i.test(expiring))

await page.selectOption('#expiry-days', '30')
await page.waitForTimeout(1200)
expiring = await text()
check('narrowing the window drops the far one', !/Land deed/i.test(expiring))

// ------------------------------------------------------------- read-only user
// Nothing above says the library is actually governed: a reader who cannot open a
// document should not be offered the ways to change one, and should not be able to
// learn how many exist by reading the counts.
await page.getByRole('button', { name: 'Sign out' }).click()
await page.waitForTimeout(1500)
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByLabel(/username or email/i).fill(READER.username)
await page.getByLabel(/password/i).fill(READER.password)
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })

await page.goto(`${BASE}/documents`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
const readerTabs = await page.getByRole('tab').allTextContents()
check('a reader can reach the library', readerTabs.includes('Library'))
check('a reader is not sent to the review queue', !readerTabs.includes('To review'), readerTabs.join(', '))
check('a reader is not offered an upload',
  (await page.getByRole('button', { name: /Upload a document/ }).count()) === 0)

const readerView = await text()
check('a reader sees the one document granted to them', /Staff handbook/i.test(readerView))
check('a reader does not see the rest of the library', !/Vehicle insurance/i.test(readerView))
check('the counts a reader sees are their own', /on file\s*1/i.test(readerView),
  readerView.replace(/\n+/g, ' ').match(/on file.*?verified/i)?.[0] ?? 'not found')

await rowFor('DOC-2026-00004').getByRole('button', { name: 'Open' }).click()
await drawer.waitFor({ timeout: 10000 })
await drawer.getByRole('heading', { name: 'Versions' }).waitFor({ timeout: 10000 })
panel = await drawer.innerText()
check('a reader can download what was granted', /Download/.test(panel))
check('a reader is not offered verification', !/Approve/i.test(panel) && !/Reject/i.test(panel))
check('a reader is not offered a new version', !/Add a version/i.test(panel))
check('a reader is not offered access changes', !/Grant access/i.test(panel) && !/Take back/i.test(panel))
check('a reader is not offered edits', !/Edit details/i.test(panel))
check('a reader is not offered deletion', !/Take out of circulation/i.test(panel))

// --------------------------------------------------------------- uploading, for real
// Back to the administrator: everything above proved a reader cannot upload, which is only
// half the story. The reader's drawer is still open and traps focus, so close it first.
await page.keyboard.press('Escape')
await page.waitForTimeout(600)
await page.getByRole('button', { name: 'Sign out' }).click()
await page.waitForTimeout(1500)
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })
await page.goto(`${BASE}/documents`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)

const stamp = Date.now().toString().slice(-6)
// The header action used to switch to the library tab and stop, leaving the upload form
// closed below the filters, so the button looked dead. Actually put a file through it.
const tmpDir = mkdtempSync(join(tmpdir(), 'erp-doc-'))
const filePath = join(tmpDir, 'school-branded.txt')
writeFileSync(filePath, `Requisition ${stamp} — annual stationery order\n`)

const headerUpload = page.getByRole('button', { name: 'Upload a document' }).first()
await headerUpload.click()
await page.waitForTimeout(700)
check('the header action opens the upload form',
  (await page.locator('#upload-title').count()) === 1)

await page.locator('#upload-file').setInputFiles(filePath)
await page.locator('#upload-title').fill(`Stationery order ${stamp}`)
await page.locator('#upload-type').selectOption({ index: 1 })
await page.locator('#upload-category').fill('Procurement')
await page.locator('#upload-description').fill(`Uploaded by the documents check, ${stamp}`)
await page.getByRole('button', { name: 'Upload', exact: true }).click()

// Wait for the row rather than assuming the upload has landed: querying the API the instant
// the button is pressed is how this check came to believe the upload had failed.
const uploadedRow = page.locator('tbody tr', { hasText: `Stationery order ${stamp}` })
await uploadedRow.first().waitFor({ timeout: 20000 })
const number = (await uploadedRow.first().innerText()).match(/DOC-\d{4}-\d{5}/)?.[0]
check('the file reaches the library', Boolean(number), number ?? 'no document number appeared')
check('it gets a document number', /^DOC-\d{4}-\d{5}$/.test(number ?? ''), number ?? 'none')

const listed = (await api('GET', '/documents?size=200', undefined, adminToken)).json.data.data
const uploaded = listed.find((d) => d.documentNumber === number)
check('the file itself made it, not just the row', Boolean(uploaded),
  uploaded ? `${uploaded.category ?? 'no category'}` : 'not in the listing')
check('the description travels with it',
  /Uploaded by the documents check/.test(uploaded?.description ?? ''), uploaded?.description ?? 'none')

await uploadedRow.first().getByRole('button', { name: 'Open' }).click()
await drawer.waitFor({ timeout: 10000 })
await drawer.getByRole('heading', { name: 'Versions' }).waitFor({ timeout: 15000 })
const uploadedPanel = await drawer.innerText()
check('the new document carries the description that was typed',
  /Uploaded by the documents check/.test(uploadedPanel))
check('the category reaches the record',
  (await api('GET', `/documents/${uploaded.id}`, undefined, adminToken))
    .json.data.document.category === 'Procurement')
check('it arrives unverified rather than straight into circulation',
  /Unchecked/i.test(uploadedPanel) && /Draft/i.test(uploadedPanel),
  uploadedPanel.split('\n').slice(0, 12).join(' / '))
check('and lands in the review queue',
  (await api('GET', '/documents?verificationStatus=UNVERIFIED&size=200', undefined, adminToken))
    .json.data.data.some((d) => d.documentNumber === number))

await page.getByRole('button', { name: 'Close' }).click()
await page.waitForTimeout(500)
check('the upload form closes again when cancelled',
  await (async () => {
    await page.getByRole('button', { name: /Upload a document/ }).first().click()
    await page.waitForTimeout(600)
    const opened = (await page.locator('#upload-title').count()) === 1
    await page.getByRole('button', { name: /Cancel upload|Upload a document/ }).last().click()
    await page.waitForTimeout(600)
    return opened && (await page.locator('#upload-title').count()) === 0
  })())

check('no runtime errors', errors.length === 0, errors.slice(0, 3).join(' | '))

await browser.close()
console.log(failures === 0 ? '\nAll document checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
