# Testing Checklist

Use this list as a quick manual QA guide. Mark items as you verify them.

## App Startup
- [ ] App launches without crash.
- [ ] Login state (server + API key) is respected.
- [ ] Offline mode toggle is honored on first screen.

## Library V2 UI
- [ ] Home loads (On Deck / Recently Updated / Newly Added).
- [ ] Want To Read loads and grid scrolls.
- [ ] Collections list loads and opens a collection detail.
- [ ] Reading Lists show empty state when no data.
- [ ] Browse People loads, paginates, and opens a person (toast).
- [ ] Bottom navigation stays visible when opening collections/tags/genres.

## Series Detail V2 UI
- [ ] Series metadata loads (cover, title, author, publication, year).
- [ ] Summary expands/collapses.
- [ ] Chips render for genres/tags; clicking opens Browse with filter.
- [ ] Collections button opens modal (online only).
- [ ] Want To Read toggle updates state (online only).
- [ ] Continue button shows correct label (Start/Continue/Re-read).
- [ ] Books tab shows volumes with download badges and progress bar.
- [ ] Chapters tab shows compact list and opens reader.
- [ ] Specials tab shows list and opens page list.
- [ ] Related/Collections horizontal lists render with images and labels.
- [ ] Menu actions: mark series read/unread (toast + state update).

## Volume Detail V2 UI
- [ ] Volume metadata loads (cover, title, summary, time, words).
- [ ] Chapters list shows download states and progress badges.
- [ ] Chapter page list renders with proper read/current styling.
- [ ] Swipe left on page downloads/removes.
- [ ] Swipe right on page marks read/unread and updates progress styles.
- [ ] Long-press chapter actions show correct modal.

## Reader - EPUB
- [ ] Pages load (online).
- [ ] Prefer offline pages uses downloaded HTML.
- [ ] Overlay shows title/page/time left.
- [ ] Progress updates when changing page/chapter.
- [ ] Returning to detail updates continue point.

## Reader - PDF
- [ ] PDF opens and renders pages.
- [ ] Progress updates when paging.
- [ ] Downloaded PDF opens from Download Queue.

## Reader - Image (CBZ/Images)
- [ ] Image page loads from `api/reader/image`.
- [ ] Swipe left/right changes pages.
- [ ] Progress updates when paging.
- [ ] Last page marks chapter as fully read.
- [ ] Returning to detail updates continue point.

## Downloads V2
- [ ] Download all/volume/chapter enqueues jobs.
- [ ] Queue shows progress and status updates.
- [ ] Completed tab lists finished jobs.
- [ ] Downloaded tab lists files/pages with series/volume/chapter labels.
- [ ] Clear downloaded pages removes items from Downloaded tab.
- [ ] Max concurrent downloads is respected.
- [ ] Retry settings (auto/manual) work.
- [ ] Metered/low-battery settings are respected.

## Cache
- [ ] Home cache loads when offline (if enabled).
- [ ] Want To Read cache loads when offline (if enabled).
- [ ] Series Detail cache loads when offline (if enabled).
- [ ] Always refresh when online bypasses cache.
- [ ] Cache refresh window triggers reload after TTL.

## History
- [ ] Reading history loads.
- [ ] No duplicate-key crash in list rendering.

## Settings
- [ ] Advanced toggles save and persist.
- [ ] Download settings apply to Download V2.
- [ ] Important info modal shows once per version.

## API / Backend
- [ ] All series/volume/reader endpoints return expected DTOs.
- [ ] Timeout handling shows error (no crash).
- [ ] Offline mode prevents network calls.
