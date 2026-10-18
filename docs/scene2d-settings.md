# Optional Scene2D client settings

The fork branch `scene2d-client-settings` builds on `scene2d-ui-prototype`
(`2dbe23b5`). It adds one real client screen. The standalone lab remains available;
neither this branch nor the lab has been merged into `develop` or proposed upstream.
Nifty remains the default for the entire client.

## Enable and try

With JDK 25 and the existing Gradle wrapper:

```powershell
.\gradlew.bat :client:run -Pscene2dSettings=true
```

In IntelliJ, add `-Pscene2dSettings=true` to the arguments of the **Gradle**
`:client:run` configuration. For a direct Java application configuration, add
`-Dillarion.ui.scene2dSettings=true` to its VM options, alongside the existing
native-access/Unsafe options documented in [the Java 25 notes](java25-migration.md).
For the generated distribution script, the property can be supplied through
`JAVA_OPTS`. Omit the property or set it to `false` to use the original dialog.
This is a startup choice, not a saved client preference.

Open **Options** from the normal login screen. No game account is required.
Unlike the lab, this dialog edits the **real client configuration**. General,
graphics, sound and development-server settings use the existing configuration
keys, defaults, English/German labels and change subscribers. The custom-server
section is hidden in production-server mode, just as in Nifty. Resetting server
settings restores the same defaults as the original dialog.

- Scroll through the sections; Save and Cancel stay visible at the bottom.
- Save validates all fields before applying them, persists through `Config.save()`
  and returns to login. Audio and graphics changes use the existing event handlers.
- Cancel or Escape discards the draft, including sound-volume edits. There is no
  live audio preview; changes take effect on Save. The existing background-FPS
  option retains its restart requirement.
- Open and close repeatedly, type in server fields, and resize the window.
  Held keys/buttons must be released before input returns to login. This includes
  the Escape key used to dismiss the dialog.

The deliberately plain skin uses libGDX's bundled bitmap font and generated
colours. It is not the final Illarion visual design. The settings form scrolls
vertically instead of reproducing Nifty's tab layout.

## Boundaries and lifecycle

`SettingsService` describes client settings, choices and draft values with Java
types only. `ClientSettingsService` adapts the existing configuration, validates
values and applies changes. `SettingsView` exposes only lifecycle operations.
Widgets, native input, skin ownership and rendering remain in `gui.scene2d`.
`Game` selects the optional adapter at the composition boundary. No dependency
on the standalone `ui-prototype` module is added to the client.

The login controller requests opening; `Game` switches after Nifty finishes its
input/update cycle. While Scene2D is open, Nifty and the login state do not update
or render. Sounds continue polling. There is no mixed widget input routing and
no attempt to expose this dialog over active gameplay. Pending game-state changes
cancel the dialog before the state transition continues.

The adapter owns the native input processor exclusively and restores the original
processor on close. Input is swallowed during the initial release barrier and
after dismissal until all keys/buttons are up. Nifty's mouse bookkeeping and the
engine-to-Nifty key-repeat state are reset at both boundaries. The login Options
button also handles Nifty's multi-click event when the opt-in is enabled: a rapid
return can otherwise suppress the next ordinary click. Default Nifty behaviour
is unchanged when the flag is absent.

Scene2D renders last after `Graphics.flush()` and owns/disposes its Stage, batch,
font and texture. Each opening creates a fresh draft and view. A view-construction
failure is logged and falls back to the existing Nifty options screen.

## Validation

```powershell
.\gradlew.bat --offline --no-daemon :client:test :client:nativeSettingsTest :client:installDist :engine-libgdx:test
.\gradlew.bat --offline --no-daemon :client:nativeSettingsTest -PsettingsTestLanguage=de
```

The opt-in native test opens a hidden OpenGL window with the real `Game`, Nifty
login XML/controllers and Scene2D settings. It uses an isolated profile under
`illaclient/build/native-settings-home`, never the user's client configuration.
It loads the local graphical resources and opens login directly, without loading
world/audio catalogues, submitting credentials or contacting a game server.
Audio output is disabled because the backend's existing music wrapper requires
OpenAL rather than libGDX's dummy audio implementation. Volume assertions check
the real sound system's values rather than audible playback. A separate test
verifies that enabling music starts the last track, without restarting an
already playing track. The audio configuration subscriber now handles that case
without relying on Nifty's live preview.

Coverage includes repeated native mouse clicks on the login Options button,
Cancel/Save/Escape, disk reload, audio-volume application, selection of an actual
display resolution, resizing to 800x600, focus separation and resource/input
teardown. Held-key/button states are simulated at the native input boundary. Framebuffer
previews are written to `illaclient/build/settings-preview`. Model tests cover
both languages, invalid values before mutation, server reset, production-server
restrictions and preserving a current resolution absent from the display list.
The ordinary routing test checks that Nifty remains the default and pauses while
the alternative view owns the screen.

On Windows with JDK 25, validation on 18 October 2026 passed **114 client tests**,
**13 backend tests** and the native dialog sequence separately in **English and
German**, without failures or skips. The client distribution was built and
framebuffer previews were inspected. No additional dependencies were downloaded.

## Limits

- `Config.save()` currently logs storage failures without returning success or
  throwing them to callers. The new adapter preserves that contract; validation
  is atomic before application, but applying and persisting the configuration is
  **not** an atomic transaction. A disk write failure cannot yet be reported
  reliably in either settings dialog. Improving that contract is separate work.
- Existing graphics handlers log unsupported mode changes and may leave the
  requested configuration different from the active display. There is no new
  timed display-mode confirmation/rollback in this experiment.
- Fullscreen transitions, audible playback, international IME, full keyboard
  navigation, accessibility, OS DPI scaling and non-Windows platforms need
  additional validation. The minimum tested window size is 800x600.
- This confirms a bounded login-screen integration, not coexistence with the
  in-game Nifty HUD. Chat, inventory dragging and gameplay polling still require
  their own ownership boundaries before further migration.
