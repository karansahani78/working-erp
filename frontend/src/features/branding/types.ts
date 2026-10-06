/**
 * Public institution branding, fetched before anyone signs in.
 *
 * Mirrors `InstitutionBranding` on the backend. Every field is optional: the login page has
 * to render usefully on a brand-new installation whose setup has not been finished yet, so
 * nothing here may be assumed to be present.
 */
export interface InstitutionBranding {
  name?: string | null
  shortName?: string | null
  institutionCode?: string | null
  institutionType?: string | null
  logoUrl?: string | null
  faviconUrl?: string | null
  primaryColor?: string | null
  secondaryColor?: string | null
  academicModel?: string | null
  portalTitle?: string | null
  portalDescription?: string | null
  supportEmail?: string | null
  supportPhone?: string | null
  phone?: string | null
  email?: string | null
  website?: string | null
  address?: string | null
  municipality?: string | null
  district?: string | null
  province?: string | null
  country?: string | null
  timezone?: string | null
  currency?: string | null
  dateFormat?: string | null
  setupCompleted?: boolean
}

/** The palette a branding payload may override. */
export const DEFAULT_PRIMARY = '#0F5132'
export const DEFAULT_SECONDARY = '#EAE5DB'

/**
 * Only a literal 3- or 6-digit hex colour is accepted from configuration. Anything else is
 * ignored, so a mistyped value falls back to the house palette instead of producing an
 * unreadable screen.
 */
export function normaliseHex(value?: string | null): string | null {
  if (!value) return null
  const trimmed = value.trim()
  if (!/^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(trimmed)) return null
  return trimmed.toUpperCase()
}

/** Relative luminance, used to decide whether text on a brand colour should be light. */
function luminance(hex: string): number {
  const body = hex.slice(1)
  const full =
    body.length === 3
      ? body
          .split('')
          .map((c) => c + c)
          .join('')
      : body
  const channels = [0, 2, 4].map((offset) => parseInt(full.slice(offset, offset + 2), 16) / 255)
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * Reads as near-black on a pale brand colour and as white on a deep one, so a logo mark
 * stays legible whichever colour an institution configures.
 */
export function onBrandColor(hex?: string | null): '#FFFFFF' | '#1F1D19' {
  const colour = normaliseHex(hex) ?? DEFAULT_PRIMARY
  return luminance(colour) > 0.45 ? '#1F1D19' : '#FFFFFF'
}

/** Mixes a colour towards white by `amount` (0–1), used for tinted brand backgrounds. */
function tint(hex: string, amount: number): string {
  const body = hex.slice(1)
  const full =
    body.length === 3
      ? body
          .split('')
          .map((c) => c + c)
          .join('')
      : body
  const channels = [0, 2, 4].map((offset) => {
    const value = parseInt(full.slice(offset, offset + 2), 16)
    return Math.round(value + (255 - value) * amount)
  })
  return `#${channels.map((c) => c.toString(16).padStart(2, '0')).join('')}`.toUpperCase()
}

export function darken(hex: string, amount = 0.14): string {
  const body = hex.slice(1)
  const channels = [0, 2, 4].map((offset) => {
    const value = parseInt(body.slice(offset, offset + 2), 16)
    return Math.max(0, Math.round(value * (1 - amount)))
  })
  return `#${channels.map((c) => c.toString(16).padStart(2, '0')).join('')}`.toUpperCase()
}

/**
 * Turns the configured brand colours into the custom properties the Tailwind theme exposes.
 * Scoping these to a wrapper lets one installation be rebranded without a rebuild.
 */
export function brandingStyle(branding?: InstitutionBranding): React.CSSProperties {
  const primary = normaliseHex(branding?.primaryColor) ?? DEFAULT_PRIMARY
  const secondary = normaliseHex(branding?.secondaryColor) ?? DEFAULT_SECONDARY
  return {
    '--color-primary': primary,
    '--color-primary-hover': darken(primary),
    '--color-primary-soft': tint(primary, 0.88),
    '--color-primary-ring': `${primary}33`,
    '--color-surface-soft': secondary,
  } as React.CSSProperties
}

/** Two-letter monogram for the logo placeholder, so an institution without a logo still looks branded. */
export function initialsFor(branding?: InstitutionBranding): string {
  const source = branding?.shortName?.trim() || branding?.name?.trim() || ''
  const words = source.split(/\s+/).filter(Boolean)
  if (words.length === 0) return 'E'
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}

/** Support contact, preferring the dedicated support fields over the general ones. */
export function supportContact(branding?: InstitutionBranding): {
  email: string | null
  phone: string | null
  website: string | null
} {
  return {
    email: branding?.supportEmail?.trim() || branding?.email?.trim() || null,
    phone: branding?.supportPhone?.trim() || branding?.phone?.trim() || null,
    website: branding?.website?.trim() || null,
  }
}

/** A one-line postal address assembled from whichever parts are configured. */
export function postalAddress(branding?: InstitutionBranding): string | null {
  const parts = [branding?.address, branding?.municipality, branding?.district, branding?.country].filter(
    (part): part is string => Boolean(part?.trim()),
  )
  const unique = parts.filter((part, index) => parts.indexOf(part) === index)
  return unique.length > 0 ? unique.join(', ') : null
}