# Terrific iOS embed example (SwiftUI)

Demonstrates Strategy 2 + 3 for accurate carousel impressions in a native app:

1. **Lazy mount** — WebView loads only when the carousel slot scrolls into view.
2. **Host visibility** — After `IFRAME_READY`, the app posts `CAROUSEL_HOST_VISIBLE` into the polls iframe.

## Integration sequence

| Step | Event | Action |
|------|--------|--------|
| 1 | `MARKUP_READY` | Page scripts ready (pre-React). **Do not** send `CAROUSEL_HOST_VISIBLE` yet. |
| 2 | `IFRAME_READY` (`id: terrific-timeline-iframe`) | Carousel React tree mounted. Start host visibility reporting. |
| 3 | Native scroll / layout | Post `{ type: "CAROUSEL_HOST_VISIBLE", isVisible: true/false }` to the carousel iframe when the slot enters or leaves the app viewport. |

Touch on the carousel still unlocks impressions as a fallback.

## Key files

- `ContentView.swift` — scrollable native page, lazy WebView mount, tracks slot visibility.
- `WebView.swift` — forwards Terrific `postMessage` events to Swift; posts `CAROUSEL_HOST_VISIBLE` into the shadow-DOM iframe.
- `TerrificHostMessages.swift` — message type constants and JS helper.

## Configuration

Update `storeId` and `embeddingId` in `ContentView.swift`, and `<your domain>` in `WebView.swift` / `CustomSchemeHandler.swift` to match your Terrific deployment.
