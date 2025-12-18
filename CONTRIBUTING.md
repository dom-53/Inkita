# Contributing to Inkita

Thanks for considering a contribution! A few guidelines to keep things smooth:

## Getting started
- Use Android Studio Flamingo+ and JDK 17.
- Build/test: `./gradlew assembleDebug` (or `:app:compileDebugKotlin`), then `./gradlew detekt spotlessApply` before opening a PR.
- App needs your own Kavita server: set server URL and API key in Settings → Kavita (HTTPS recommended).

## Workflow
- Branch names: `feature/...`, `bugfix/...`, `refactor/...`.
- Commits: keep them scoped and descriptive; prefer present tense.
- Run `detekt` and `spotlessApply`; fix warnings where feasible.
- For UI changes, add a brief description/screenshots in the PR.

## Code style
- Kotlin + Compose: keep UI stateless where possible; move logic to ViewModels/repositories.
- Comments should explain intent for non-obvious code; avoid noise.

## Reporting issues
- Include device, app version, and reproduction steps.
- For crashes: share logs/stack traces if possible.
- Network issues: note server URL scheme/port (HTTP vs HTTPS) and network environment.

## Translations
- Help translate on POEditor: https://poeditor.com/projects/view?id=815516

## Forks
Forks are allowed as long as they follow the [project LICENSE](https://github.com/dom-53/Inkita/blob/master/LICENSE).

When creating a fork, to avoid confusion and install conflicts:
- Change the app name and icon.
- Change or disable the app [update checker](https://github.com/dom-53/Inkita/blob/master/app/src/main/java/net/dom53/inkita/core/update/UpdateChecker.kt).
- Change the `applicationId` in [`app/build.gradle.kts`](https://github.com/dom-53/Inkita/blob/master/app/build.gradle.kts#L20).

Thanks for helping improve Inkita!
