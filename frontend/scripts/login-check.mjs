/**
 * Regression checks for the login screen.
 *
 * The login page has three obligations that are easy to break and hard to notice, so each
 * is asserted here rather than left to a visual review:
 *
 *   1. it matches the intended design (palette, centred card, no overflow, real headings);
 *   2. every part of it comes from institution branding, so one installation can be
 *      rebranded without touching the code;
 *   3. it still renders and still signs in when branding is missing, empty or malformed.
 *
 * Run with `node scripts/login-check.mjs` while the dev server is up.
 */
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const problems = []
let failures = 0

const check = (label, condition, detail = '') => {
  if (condition) {
    console.log(`  ok    ${label}${detail ? ` ${detail}` : ''}`)
  } else {
    console.log(`  FAIL  ${label}${detail ? ` ${detail}` : ''}`)
    problems.push(label)
    failures += 1
  }
}

/** First three channels of a computed colour, for comparison against the house palette. */
const rgb = (value) =>
  (value.match(/\d+/g) || [])
    .slice(0, 3)
    .map(Number)
    .join(',')

/** A stub login response, so the flow can be completed without depending on real accounts. */
const STUB_TOKENS = {
  data: {
    tokenType: 'Bearer',
    accessToken: 'stub-access',
    accessTokenExpiresAt: '2099-01-01T00:00:00Z',
    refreshToken: 'stub-refresh',
    refreshTokenExpiresAt: '2099-01-01T00:00:00Z',
    user: {
      id: 'stub-user',
      username: 'admin',
      displayName: 'Administrator',
      role: 'SUPER_ADMIN',
      status: 'ACTIVE',
      permissions: [],
      staff: true,
      student: false,
      parent: false,
      mustChangePassword: false,
    },
  },
  message: 'ok',
}

const stubBranding = (page, payload, status = 200) =>
  page.route('**/api/v1/public/institution', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(payload) }),
  )

async function open(browser, { branding, status = 200 } = {}) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  // A rejected request is announced by the browser itself, so it is not counted here.
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().includes('Failed to load resource')) {
      errors.push(message.text())
    }
  })
  if (branding !== undefined) await stubBranding(page, branding, status)
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.waitForSelector('#loginId')
  return { page, errors }
}

/* ------------------------------------------------------------ design and layout */

async function designChecks(browser) {
  for (const [name, viewport] of Object.entries({
    desktop: { width: 1440, height: 900 },
    tablet: { width: 820, height: 1100 },
    mobile: { width: 390, height: 844 },
  })) {
    console.log(`\n${name} ${viewport.width}x${viewport.height}`)
    const { page, errors } = await open(browser)
    await page.setViewportSize(viewport)

    check('warm ivory page background', rgb(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)) === '244,240,232')

    const card = page.locator('main > div').first()
    const box = await card.boundingBox()
    check('white card', rgb(await card.evaluate((el) => getComputedStyle(el).backgroundColor)) === '255,255,255')
    check(
      'card is horizontally centred',
      Math.abs(box.x + box.width / 2 - viewport.width / 2) < 2,
      `centre=${Math.round(box.x + box.width / 2)}`,
    )
    check(
      'no horizontal overflow',
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    )

    const button = page.getByRole('button', { name: 'Sign in' })
    check(
      'login button is forest green',
      rgb(await button.evaluate((el) => getComputedStyle(el).backgroundColor)) === '15,81,50',
    )
    const form = await page.locator('form').first().boundingBox()
    const buttonBox = await button.boundingBox()
    check('login button spans the form', Math.abs(buttonBox.width - form.width) <= 1)

    check(
      'institution heading uses the brand colour',
      rgb(await page.locator('h1').first().evaluate((el) => getComputedStyle(el).color)) === '15,81,50',
    )
    check('page names its purpose', (await page.getByRole('heading', { name: 'Sign in' }).count()) > 0)

    // A label that points at a wrapper rather than the control announces nothing useful.
    const unlabelled = await page.evaluate(() =>
      [...document.querySelectorAll('input, select, textarea')]
        .filter((el) => {
          if (el.getAttribute('aria-label')) return false
          const id = el.getAttribute('id')
          return !id || !document.querySelector(`label[for="${CSS.escape(id)}"]`)
        })
        .map((el) => `${el.tagName.toLowerCase()}#${el.getAttribute('id')}`),
    )
    check('every control is labelled', unlabelled.length === 0, unlabelled.join(', '))

    const duplicateIds = await page.evaluate(() =>
      [...document.querySelectorAll('[id]')]
        .map((el) => el.id)
        .filter((id, index, all) => all.indexOf(id) !== index),
    )
    check('no duplicate element ids', duplicateIds.length === 0, duplicateIds.join(', '))
    check('no application errors', errors.length === 0, errors.join(' | ').slice(0, 100))

    await page.close()
  }
}

/* ------------------------------------------------------------------- rebranding */

async function rebrandChecks(browser) {
  console.log('\nrebranding')
  const { page } = await open(browser, {
    branding: {
      data: {
        name: 'Kushma College of Management',
        shortName: 'Kushma College',
        institutionType: 'COLLEGE',
        logoUrl: 'https://example.test/logo.svg',
        faviconUrl: 'https://example.test/favicon.ico',
        primaryColor: '#7a1f3d',
        secondaryColor: '#f2e9ec',
        portalTitle: 'Kushma Student Portal',
        supportEmail: 'registrar@kushma.edu.test',
        supportPhone: '+977-61-444444',
        website: 'https://kushma.edu.test',
        address: 'Lalitpur',
        municipality: 'Lalitpur',
        district: 'Bagmati',
        country: 'Nepal',
        setupCompleted: true,
      },
      message: 'Success',
    },
  })

  check('institution name comes from configuration', (await page.locator('h1').innerText()) === 'Kushma College of Management')
  check('portal title comes from configuration', (await page.innerText('body')).includes('Kushma Student Portal'))
  check('browser title follows configuration', (await page.title()).includes('Kushma Student Portal'))
  check(
    'configured primary colour is applied',
    rgb(await page.locator('h1').evaluate((el) => getComputedStyle(el).color)) === '122,31,61',
  )
  check(
    'configured primary colour reaches the login button',
    rgb(await page.getByRole('button', { name: 'Sign in' }).evaluate((el) => getComputedStyle(el).backgroundColor)) === '122,31,61',
  )
  check('configured logo is used instead of a monogram', await page.locator('img[alt*="logo"]').isVisible())
  check('no other institution is named', !(await page.innerText('body')).match(/Riverside|Sundar/i))
  check('support email is shown', (await page.innerText('body')).includes('registrar@kushma.edu.test'))
  check('support phone is shown', (await page.innerText('body')).includes('+977-61-444444'))
  check('postal address is shown', (await page.innerText('body')).includes('Lalitpur, Bagmati, Nepal'))
  check(
    'favicon follows configuration',
    (await page.evaluate(() => document.querySelector('link[rel="icon"]')?.getAttribute('href'))) ===
      'https://example.test/favicon.ico',
  )

  await page.close()
}

/* ------------------------------------------------------- degraded branding inputs */

async function degradedBrandingChecks(browser) {
  const cases = [
    ['branding endpoint fails', { data: null, message: 'boom' }, 500],
    ['branding not yet configured', { data: null, message: 'Success' }, 200],
    [
      'malformed colour values',
      { data: { name: 'Bad Colours', primaryColor: 'javascript:alert(1)', secondaryColor: 'rgb(0,0,0)' }, message: 'Success' },
      200,
    ],
  ]

  for (const [label, payload, status] of cases) {
    console.log(`\n${label}`)
    const { page, errors } = await open(browser, { branding: payload, status })
    check(
      'falls back to the house palette',
      rgb(await page.getByRole('button', { name: 'Sign in' }).evaluate((el) => getComputedStyle(el).backgroundColor)) === '15,81,50',
    )
    check('login form is still usable', (await page.locator('#loginId').isEditable()) && (await page.locator('#password').isEditable()))
    check('no application errors', errors.length === 0, errors.join(' | ').slice(0, 100))

    // Branding is decoration; a failure there must never block a sign-in.
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(STUB_TOKENS) }),
    )
    await page.getByLabel(/username or email/i).fill('admin')
    await page.getByLabel(/password/i).fill('Institution123!')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10_000 }).catch(() => {})
    check('sign-in still succeeds', !page.url().includes('/login'), page.url())

    await page.close()
  }
}

/* -------------------------------------------------------------------- behaviour */

async function behaviourChecks(browser) {
  console.log('\nbehaviour')
  const { page } = await open(browser)

  await page.getByRole('radio', { name: 'Student / Parent' }).click()
  check('audience switch updates the field label', (await page.getByText('Student number or email').count()) > 0)
  await page.getByRole('radio', { name: 'Admin / Staff' }).click()
  check('audience switch is reversible', (await page.getByText('Username or email').count()) > 0)

  await page.getByRole('button', { name: 'Show' }).click()
  check('password can be revealed', (await page.locator('#password').getAttribute('type')) === 'text')

  await page.getByRole('button', { name: 'Forgot password?' }).click()
  check('forgot password opens a dialog', await page.getByRole('dialog').isVisible())
  await page.keyboard.press('Escape')
  check('Escape closes the dialog', (await page.getByRole('dialog').count()) === 0)

  await page.getByRole('button', { name: 'Forgot password?' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/username or email/i).fill('admin')
  await dialog.getByLabel(/email on the account/i).fill('office@sundar.edu.test')
  const [request] = await Promise.all([
    page.waitForRequest((r) => r.url().includes('/api/v1/auth/forgot-password')),
    dialog.getByRole('button', { name: 'Send reset link' }).click(),
  ])
  check('reset request reaches the real endpoint', request.url().endsWith('/api/v1/auth/forgot-password'), request.method())
  // The server answers identically either way, so the screen must not imply success is proof.
  check('response does not reveal whether the account exists', (await page.getByRole('status').innerText()).includes('If that account exists'))

  await page.close()
}

const browser = await chromium.launch()
try {
  await designChecks(browser)
  await rebrandChecks(browser)
  await degradedBrandingChecks(browser)
  await behaviourChecks(browser)
} finally {
  await browser.close()
}

console.log(
  problems.length === 0
    ? '\nAll login checks passed.'
    : `\n${problems.length} login check(s) failed: ${problems.join('; ')}`,
)
process.exit(problems.length === 0 ? 0 : 1)