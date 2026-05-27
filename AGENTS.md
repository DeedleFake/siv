# AGENTS.md

This file provides guidance for AI coding agents (Claude, Gemini, Cursor, Aider, etc.) working in the Simple Image Viewer codebase.

## Project Overview

**Simple Image Viewer** is a native Android application for viewing images. It is intended to be a modern, clean, high-quality example of Jetpack Compose best practices.

### Goals
- Deliver a fast, smooth, accessible image viewing experience
- Follow current (2026) Google architecture and Compose recommendations exactly
- Serve as a reference for idiomatic, maintainable Compose code
- Support modern Android features (large screens, foldables, predictive back, Photo Picker, etc.)

## Current State (Important)

This project is currently a minimal Android application skeleton:

- **No Jetpack Compose dependencies** yet (uses AppCompat + Material Components theme)
- **No activities** declared in the manifest
- **minSdk 36** (Android 16+ only — we can use the latest APIs without compat shims)
- Uses Gradle version catalogs (`gradle/libs.versions.toml`)
- Standard single-module structure (`:app`)

**When making changes, assume the project will be (or is being migrated to) 100% Jetpack Compose + Material 3.** Do not add or suggest any new XML layouts, View Binding, Data Binding, or legacy View system code.

## Core Principles

1. **100% Declarative UI** — All new UI must be built with Jetpack Compose and Material 3.
2. **Unidirectional Data Flow (UDF)** — State flows down, events flow up. ViewModels (or presenters) are the source of truth.
3. **Stateless Composables by default** — Hoist state aggressively. Keep UI pure.
4. **Performance first** — Every Composable must be written with recomposition in mind.
5. **Accessibility is non-negotiable** — Proper semantics, content descriptions, and support for TalkBack/large text from day one.
6. **Small, focused functions** — Prefer many small `@Composable` functions over large screens.

## Jetpack Compose Best Practices (Strict)

### Function Signature Order (Always Follow)
```kotlin
@Composable
fun ImageViewerScreen(
    modifier: Modifier = Modifier,      // 1. Modifier first (after any receiver/context)
    uiState: ImageViewerUiState,        // 2. Data / state
    onEvent: (ImageViewerEvent) -> Unit, // 3. Callbacks / events last
) { ... }
```

Public/reusable Composables **must** accept and apply a `Modifier`.

### State Management
- ViewModels expose `StateFlow<UiState>` (or `SharedFlow` for events).
- In Composables, collect with `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose`.
- Use `rememberSaveable` for UI state that must survive process death (rare).
- Use `derivedStateOf` for expensive derived values that should not cause extra recompositions.
- One-shot events: Use `Channel` + `receiveAsFlow()` or `SharedFlow` with `WhileSubscribed(5_000)`.
- **Never** create `mutableStateOf(...)` directly in a Composable body without `remember`.

### Effects & Side Effects
- Use `LaunchedEffect` for one-time work that depends on keys (navigation, snackbars, loading data).
- Use `DisposableEffect` for cleanup (listeners, observers).
- Use `SideEffect` only for immediate, non-suspending side effects.
- Never perform network, database, or heavy work directly in composition.

### Performance & Stability
- Keep Composables fast and side-effect free during composition.
- Provide stable `key` parameters to `LazyColumn` / `items()`.
- Prefer immutable data classes for UI state. Annotate with `@Immutable` or `@Stable` when the Compose compiler cannot infer stability.
- Use `kotlinx.collections.immutable` for lists/sets that must remain stable.
- Read state as close to the point of use as possible (avoid reading high-level state in leaf Composables).
- Use `Modifier.graphicsLayer` for transformations that would otherwise trigger recomposition.

### Theming & Design System
- Use the project's `AppTheme` wrapping `MaterialTheme`.
- Prefer tokens from `MaterialTheme.colorScheme`, `typography`, and `shapes`.
- For an image viewer, be thoughtful with dark theme (images often look best with minimal UI chrome).
- Support dynamic color (Android 12+) and large screen layouts via `WindowSizeClass`.

### Previews
- Every Composable must have at least one `@Preview`.
- Add multiple previews: Light/Dark, different font scales, small/medium/expanded window sizes.
- Use `@PreviewParameter` for complex states when helpful.
- Always enable `showBackground = true` and use realistic background colors.

### Accessibility & Semantics
- Provide meaningful `contentDescription` (or `clearAndSetSemantics` when visual-only).
- Use `semantics { ... }` for custom actions and states.
- Test with TalkBack in mind. Image viewer controls (zoom, pan, rotate, share) must be discoverable.

### Images & Media (Project-Specific)
- Use **Coil 3** (`coil-compose`) for image loading — it has excellent Compose integration.
- Handle large images carefully (downsampling, memory-efficient loading).
- Support common formats and EXIF orientation.
- Consider Photo Picker (`ActivityResultContracts.PickVisualMedia`) as the primary way to select images.
- For full-screen viewing: support pinch-to-zoom, double-tap, pan, and fling gestures (use `TransformableState` or a well-tested library).

## Intended Architecture (Once Implemented)

- **Single Activity** + Navigation Compose (type-safe destinations preferred)
- **MVVM** with ViewModels as state holders (or MVI-style events if complexity grows)
- **Repository pattern** in the data layer
- **Dependency Injection**: Hilt (recommended) or manual when simple enough
- Layers: `ui` (Composables + ViewModel) → `domain` (use cases / business rules) → `data` (repositories, local + remote sources)
- For a simple image viewer, the domain layer may be thin initially

## Naming Conventions

| Type                  | Naming Example                     | Notes |
|-----------------------|------------------------------------|-------|
| Screen / Route        | `ImageViewerScreen`, `GalleryRoute` | Top-level UI entry points |
| ViewModel             | `ImageViewerViewModel`             | Matches the screen |
| UI State              | `ImageViewerUiState` (sealed interface + data classes) | `Loading`, `Success`, `Error` variants |
| Events / Actions      | `ImageViewerEvent` (sealed interface) | User actions + one-time effects |
| Reusable Composables  | `ZoomableImage`, `ThumbnailGrid`, `ViewerTopBar` | PascalCase, descriptive |
| Use Cases             | `LoadImageUseCase`, `ShareImageUseCase` | When a domain layer exists |

## Development Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Run all tests
./gradlew test
./gradlew connectedAndroidTest   # requires device/emulator

# Format & lint (run before committing)
./gradlew spotlessApply          # if Spotless is configured
./gradlew ktlintFormat           # if using ktlint

# Dependency updates & checks
./gradlew dependencyUpdates      # if plugin present
```

**Note:** As of project creation, many of these tasks will not exist until the build is properly configured with Compose, testing frameworks, etc. Add them as the project grows.

## Testing Strategy

- **Unit tests** for ViewModels, use cases, and repositories (use `turbine` + Truth or Kotest).
- **Compose UI tests** using `ComposeTestRule` + `ComponentActivity` (never launch full activities in small tests).
- **Screenshot testing** (Roborazzi or Paparazzi) is strongly encouraged for visual components once the UI is non-trivial.
- Every new screen and major Composable should have meaningful tests.

## When Adding a New Screen or Feature

1. Update `libs.versions.toml` + `app/build.gradle.kts` first (Compose BOM, Coil, navigation, etc.).
2. Define the destination in the navigation graph.
3. Create `FeatureUiState` + `FeatureEvent` + `FeatureViewModel`.
4. Create the screen Composable + small focused child Composables.
5. Add comprehensive `@Preview` functions.
6. Add basic UI tests and (ideally) screenshot tests.
7. Wire up navigation and DI.
8. Verify accessibility and large-screen behavior.

## Common Pitfalls to Avoid

- Suggesting or writing any XML layouts for new UI.
- Forgetting the `modifier: Modifier = Modifier` parameter or placing it in the wrong position.
- Performing side effects or I/O directly inside `@Composable` functions.
- Using `remember` incorrectly or omitting it for mutable state.
- Capturing unstable values (e.g. lists, lambdas) in `remember` keys or `LaunchedEffect` keys without proper wrapping.
- Ignoring recomposition — always consider "what causes this to recompose?"
- Hardcoding colors, strings, or dimensions instead of using the theme or resources.
- Creating "God Composables" that do too much.
- Neglecting content descriptions on images and interactive controls.

## Useful References

- [Now in Android AGENTS.md](https://github.com/android/nowinandroid/blob/main/AGENTS.md) — the gold standard reference this file draws from.
- Official [Jetpack Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Compose mental model](https://developer.android.com/develop/ui/compose/mental-model)
- [Material 3 Compose documentation](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Coil 3 documentation](https://coil-kt.github.io/coil/compose/)

---

**This file is living documentation.** Update it whenever you establish new patterns, change architecture decisions, or discover rules that prevent the agent from repeating mistakes. Place the highest-signal rules near the top.
