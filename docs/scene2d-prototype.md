# Scene2D UI prototype

This is an optional fork experiment on `scene2d-ui-prototype`, based on
`develop` at `4f8bc708`. It is not a replacement client, an upstream contribution,
or a migration of the production UI. No server or Illarion account is needed.

## Run

Use JDK 25 and the repository's Gradle wrapper:

```powershell
.\gradlew.bat :ui-prototype:run
```

In IntelliJ, run the `ui-prototype` module's Gradle `run` task. Use the existing
Gradle user home if the dependencies are cached there. The Gradle run stores
settings in `ui-prototype/build/prototype/settings.properties`.

Alternatively build `:ui-prototype:installDist`, then start
`ui-prototype/build/install/ui-prototype/bin/ui-prototype.bat`. The packaged
application stores its isolated settings in
`<user.home>/.illarion-ui-prototype/settings.properties`. Direct Java launches
can override this with `-Dillarion.prototype.settings=<file>`. The real client's
configuration, account information and servers are never accessed.

The native-access and Unsafe-denial options are inherited from the existing
Java 25 build. The lab uses the already selected LibGDX 1.13.1 and LWJGL 3.4.3;
no VisUI dependency, dependency upgrade or downloaded skin is required.

## Try it

- **Options:** the modal window implements a representative subset of the
  client's settings: movement keys, quest markers, own-character label and FPS.
  The extra UI-scale slider explores 100%, 125% and 150% scaling. Save persists
  and applies the values; Cancel or Escape discards the working copy. A failed
  save leaves the previous settings active and the window open with an error.
  These are lab settings, not the complete production options catalogue.
- **World:** A/D moves the green test character when movement is enabled.
  The yellow marker and own-character label respond to their display settings.
  Health is fixed synthetic text; there is no game simulation or server data.
- **Chat:** type and send text, or insert incoming messages. Scroll upwards and
  insert more messages: the visible message and offset are retained. At the
  bottom, incoming messages are followed. Resize the window while reading.
  History is bounded to 200 messages; when the anchor itself is evicted, the
  oldest remaining message becomes the fallback. This is not a performance-
  optimised chat implementation: it currently rebuilds the bounded label list.
- **Input ownership:** typing A/D in the chat field must not move the character.
  Modal settings and active dragging also block world keyboard movement.
  Click an unused world area or press Escape to release text focus.
- **Inventory:** drag the apple from the bag to the ground slot and back.
  Dropping outside a valid target keeps the original item. This exercises
  Scene2D's drag lifecycle; no actual inventory command or server is involved.
- **Layout:** resize the window and change UI scale. The checked desktop sizes
  are 1100x800 and 900x700. Small windows, OS-level DPI scaling and multiple
  monitors still need separate validation. Bitmap-font scaling is deliberately
  basic; the synthetic skin is not a proposed visual redesign.

## Boundaries and integration

`LabModel`, immutable `Settings` and `SettingsStore` have no GUI-library types.
`FileSettingsStore` implements persistence independently of widgets.
`PrototypeView` exposes only lifecycle operations and keyboard capture.
`PrototypeGame` uses that interface and the existing engine interfaces.
`PrototypeMain` is the composition root selecting `Scene2dView`.
Scene2D actors, events, colours, layout and input ownership stay inside the
`scene2d` package. Tests can exercise the model and storage without OpenGL.

This is an example of isolating dependencies at application boundaries, not an
attempt to wrap every widget in a new general-purpose UI framework. For future
migration, introduce interfaces around concrete features such as chat display,
inventory commands and settings. Avoid exposing `Actor`, `Skin`, library colours
or event classes through those interfaces. Translate at the adapter boundary.
The rendering adapter necessarily depends on LibGDX; interchangeability of the
model does not imply that swapping the whole renderer will be effortless.

The lab runs through the real `EngineManager`, `ApplicationGameContainer` and
`GameListener` lifecycle. A small library-neutral `Graphics.flush()` operation
finishes engine batches before Scene2D draws last with its own batch. Its existing
backend implementation delegates to `flushAll()`. Do not interleave open batches
or draw with cached engine GL state after the external UI pass. A 1x1 white PNG,
generated for this module, provides the engine's `gui/blank.png` texture.

Scene2D temporarily owns the native input processor and restores the previous
one on disposal. The legacy processor receives no events while the lab is active,
so it cannot accumulate unconsumed queued events. Engine polling still sees raw
held keys: the game therefore explicitly checks `capturesKeyboard()` before
moving. UI event consumption alone would not protect the real client's polling.

**Nifty and Scene2D are not running side by side in this prototype.** A mixed
production screen would additionally need explicit routing of mouse presses,
releases, drag ownership, keyboard focus and held-key state. Screen changes while
keys are held, input cancellation and coordinate conversion must be specified
before attempting coexistence. No such change is made to the production client.

## Validation and remaining decisions

```powershell
.\gradlew.bat --offline --no-daemon :ui-prototype:test :ui-prototype:nativeUiTest :ui-prototype:installDist :client:test :engine-libgdx:test
```

The native UI test is opt-in, requires a desktop OpenGL driver and creates a
hidden window. It runs the existing engine with the prototype view, sends real
Scene2D pointer/key events, checks Save/Cancel, text entry and capture, chat
scroll retention, drag-to-ground, window resizing, scale and modal dismissal.
It checks GL errors, resource/input teardown and a framebuffer pixel proving
that engine drawing remains visible below the UI. It writes framebuffer previews
to `ui-prototype/build/ui-preview/` for visual inspection. Model tests cover
file reload, invalid scale, failed saving, bounded history and movement capture.

On Windows with Java 25, validation on 18 October 2026 passed three model/storage
cases, one native UI interaction sequence, 109 existing client cases and 13
backend cases, without failures or skips. The application distribution was
built, and the generated 100% and 150% framebuffer previews were inspected.

This does not validate production game dialogs, real server interactions, Nifty
coexistence, international text input/IME, complete keyboard navigation,
accessibility, the shipped Illarion fonts/skins or non-Windows platforms. FPS
improvements and migration duration cannot be inferred from this lab.

Before a migration decision, the next useful experiment is one actual client
screen behind a feature flag, with clear input ownership and existing regression
coverage retained. Then evaluate rich chat behaviour and real inventory dragging.
The full options UI, style assets and localisation should follow that decision.
