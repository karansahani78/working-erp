import { useEffect, useRef, type RefObject } from 'react'

/**
 * Keeps a modal's keyboard focus inside it.
 *
 * `aria-modal="true"` tells a screen reader to treat the rest of the page as gone, but it
 * does nothing for the keyboard: tabbing walks straight out into the page behind, which is
 * both unusable and misleading, since the person is told they are in a dialog. This wraps
 * focus back around at both ends, and closes on Escape, which every dialog is expected to do.
 *
 * Focus is restored to whatever was focused before, so closing a dialog does not throw the
 * person back to the top of the document.
 */
const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function useFocusTrap(
  ref: RefObject<HTMLElement | null>,
  active = true,
  onEscape?: () => void,
) {
  // Read during render, not in the effect: by the time the effect runs, autoFocus has already
  // moved focus inside the dialog and the element that opened it is forgotten. Rendering is
  // early enough to still see it.
  const opener = useRef<HTMLElement | null>(null)
  if (opener.current === null && document.activeElement instanceof HTMLElement) {
    opener.current = document.activeElement
  }

  useEffect(() => {
    if (!active) return
    const node = ref.current
    if (!node) return

    const previouslyFocused = opener.current

    const focusables = () =>
      Array.from(node.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (element) => element.offsetParent !== null || element === document.activeElement,
      )

    // Something inside has to hold focus from the moment the dialog opens, or the next Tab
    // starts from the page behind.
    const initial = focusables()[0]
    if (initial) initial.focus()
    else node.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        onEscape?.()
        return
      }
      if (event.key !== 'Tab') return

      const items = focusables()
      if (items.length === 0) {
        event.preventDefault()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      const current = document.activeElement as HTMLElement | null

      if (event.shiftKey && (current === first || !node.contains(current))) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && (current === last || !node.contains(current))) {
        event.preventDefault()
        first.focus()
      }
    }

    node.addEventListener('keydown', onKeyDown)
    return () => {
      node.removeEventListener('keydown', onKeyDown)
      // Deliberately not cleared: React replays mount effects in development, and blanking the
      // opener on the first cleanup would leave the real unmount with nothing to restore to.
      if (previouslyFocused && document.contains(previouslyFocused)) {
        previouslyFocused.focus()
      }
    }
  }, [ref, active, onEscape])
}
