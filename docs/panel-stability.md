# Player panel stability — pending source update

This update follows the audit of v0.21.0. The Android application has **not been compiled, built into an APK, or published as a release** at the user's request. Android unit/UI regressions are added for a later authorized validation run. A Kotlin parser/formatter and source/resource checks are not substitutes for compilation or device testing.

| Audit group | Implementation |
| --- | --- |
| 1. UI-thread native work | Production property subscriptions dispatch through the core worker. Native reads happen outside the session state lock; newer observer revisions win over late initial snapshots. Core subscriptions survive media changes; media-specific writes remain generation-bound. Panel speed, chapter and reset actions use the command queue. |
| 2. External tracks | Picker requests retain media generation and subtitle target. Provider metadata and descriptors open on IO with caught failures. Descriptors have explicit media/ViewModel ownership and cancellation cleanup. Stale results are rejected with feedback. |
| 3. Primary/Secondary | Target is part of the settings/delay route identity and external subtitle request. Secondary styling no longer edits Primary; supported secondary scale has its own control. |
| 4. Manual delay measurement | Measurement has a monotonic start and an immutable initial value. Target/media changes cancel it. Polling updates preview only; the completion action commits once. |
| 5. Delay side effects | Opening does not issue a write. Reset follows the selected target. Default saves read back the native value through the serialized worker. |
| 6. Numeric validity | Localized editable drafts reject non-finite, out-of-range and overflowing values before callbacks. Steps saturate through Long arithmetic. Fields share panel geometry and expose error text. |
| 7. Audio channel policy | Auto/Auto Safe mappings are corrected. Existing explicit preferences and legacy backups migrate once to preserve their previous effective policy. Reverse stereo owns a named filter and never assigns the complete af list. |
| 8. Backup/presets | Restore validates complete types and object/enum domains before atomic commit. Speed sets normalize into finite unique keys. UI parsing tolerates legacy corrupt presets with a safe fallback. Schema discovery uses inert preference stubs. |
| 9. Accessibility | Repeating buttons retain a real semantic click and native press interaction. Multi-choice rows use checkbox semantics. Closing and collapsing content loses actionable accessibility descendants and pointer access. Standalone actions have labels. |
| 10. Panel geometry | Width uses window orientation rather than IME-reduced height. Entrance damping respects the maximum panel width/insets; drag bounds update with geometry. Return settling is critically damped. Closing clears focus/keyboard. |
| 11. Opacity | Opacity applies to the outer pane and player-local card backgrounds; text/icon opacity is independent. |
| 12. UI consistency | Numeric and inline provider-key fields share panel fields. Filters preserve expansion; expandable sections share motion and reduced-motion behavior. Navigation respects RTL. |
| 13. Property ownership | mpv.conf-owned controls are disabled with a notice. Writes read back actual values and report rejection. Colors keep a bounded pending draft, then resume native observations. CPU debanding has an actual gradfun radius control; GPU parameters only appear for GPU mode. |
| 14. Search/posters | Source completion only updates data and never steals the expanded section. Failure/retry states are visible. Posters distinguish loading/missing/failure and support individual retries. |
| 15. Chapters/timer | Empty chapters have a message; selecting a chapter preserves pause state. The timer uses an elapsed-realtime deadline off the UI thread; its editor keeps seconds and does not replace an unedited deadline. |

## Validation status

Twenty isolated JVM tests passed using Kotlin 2.2.20 and JUnit 4.13.2 directly, without Gradle or the Android build system. The three tested classes are `PanelDomainTest`, `StablePropertySessionTest` and `PlaybackCoordinatorTest`. Production domain/coordinator/session source files were used directly; route enum definitions were extracted from the real source into the isolated harness.

These results do not cover Android resource compilation, Compose behavior, provider integration, import integration or physical devices. Those added regressions remain pending.

## Regression coverage

- Domain cases: invalid/localized numeric drafts, bounded stepping, canonical speed keys, monotonic delay and timer math, subtitle route identity.
- Native session cases: deferred subscription, late initial snapshot versus an event, old binding invalidation, core work surviving a media change while stale media work is discarded.
- Import cases: malformed speeds and enum domains never partially apply; legacy channel migration runs once.
- Compose cases: semantic increment, no write on panel open, target-change cancellation, stable portrait width under reduced height, inaccessible closing content, all subtitle toolbar targets, unedited timer seconds.

## Follow-up validation when building is authorized

Compile and run the Android test suite and lint, then verify on hardware: cold start, rapid input, large fonts/RTL, rotation and IME during transitions, TalkBack, delayed engine echoes, revoked/cloud document providers, subtitle import for both slots, source failures and poster retries, and background timer expiry.

No version/tag/release is created by this source update. Its commit uses `[skip ci]` so pushing it does not start the APK workflow.
