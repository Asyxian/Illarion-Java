# Issue-Entwürfe zur Client-Performance

Stand: 3. Oktober 2026. Die folgenden fünf Entwürfe sind für einzelne GitHub-Issues gedacht.
Jeweils den Text unter **Title** ins Titelfeld und den gesamten Abschnitt unter **Body**
bis zur nächsten Trennlinie ins Beschreibungsfeld kopieren. Die Markdown-Quellansicht verwenden.

Die Codeverweise zeigen auf den lokal überprüften Upstream-Stand `d71f2c3a`.
Die betroffenen Implementierungen entsprechen diesem Stand. Es wurde nicht erneut online geprüft,
ob bereits Tickets oder neuere Korrekturen existieren. Die Texte setzen weder unsere
Build-Modernisierung noch lokale Hilfsskripte voraus.

Einordnung: Tickets 1 und 2 beschreiben konkrete Berechnungs- bzw. Ressourcenfehler.
Ticket 3 ist ein möglicher Performance-Bug mit belegtem Mehraufwand, aber ohne gemessene Ruckler.
Ticket 4 beschreibt einen blockierenden Ladepfad. Ticket 5 beschreibt eine Abweichung vom
dokumentierten Aufschubverhalten mit möglicher Auswirkung auf die Bildausgabe.
Allgemeines Text-Caching und weniger Szenenberechnungen sind ohne Laufzeitprofil keine ausreichend
belegten separaten Bugs und deshalb nicht als Tickets enthalten.

---

## Ticket 1: Namensschild-Berechnung

### Title

Avatar label dimensions are recalculated every update because dimensionsDirty is never cleared

### Body

#### Summary

`AvatarTextTag` has a dirty flag intended to avoid recalculating unchanged text dimensions.
The flag is set when the character name or health text changes, but it is never reset.
After the first text change, each label update measures the same text again, even when its
contents have not changed.

This is a source-level finding. The redundant work is identifiable in the code; its effect
on frame time has not been measured in a running game.

#### Relevant code and execution path

- [AvatarTextTag](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illaclient/src/main/java/illarion/client/graphics/AvatarTextTag.java#L175):
  `setCharacterName()` and `setHealthState()` set `dimensionsDirty = true`.
- In the same class, `calculateTextLocations()` checks the flag, measures the text and updates
  the rectangle, but never sets `dimensionsDirty = false`.
- [Avatar.update()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illaclient/src/main/java/illarion/client/graphics/Avatar.java#L366)
  updates the label on each game update while `renderName` is true.
- [GdxFont.getWidth()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxFont.java#L63)
  performs glyph layout for the text and, when present, its outline. Pooling the layout object
  does not avoid repeating the measurement.

#### Suggested verification

1. Display another character's name, for example by holding Right Alt.
2. Count calls to `Font.getWidth()` from `AvatarTextTag.calculateTextLocations()`.
3. Keep the character name and health text unchanged over several updates.

The code currently repeats width measurements on each label update. Expected behaviour is to
reuse the dimensions until the text or relevant font metrics change.

#### Possible fix

Separate dimension calculation from screen positioning. Cache widths, heights and internal
text offsets, and clear the dimension flag after recalculation. Continue updating the screen
rectangle when the avatar moves or its height changes.

Simply clearing the flag at the end of the existing method is insufficient: that method also
positions the label, while `setDisplayLocation()` and `setAvatarHeight()` do not invalidate it.
Such a change could leave labels at stale positions.

Regression coverage should include unchanged text, changed names/health text, movement,
avatar height changes and hiding/revealing labels.

---

## Ticket 2: Native Pixelpuffer

### Title

Native Pixmap buffers are not released after texture upload or world-map disposal

### Body

#### Summary

Two pixel-buffer lifecycle paths appear to omit required native-memory cleanup:

- Temporary image data loaded by `GdxTextureManager` is not disposed after texture upload.
- `GdxWorldMap.dispose()` releases its GPU texture but not its CPU-side `worldMapPixels`.

These buffers occupy native memory outside the Java heap. This finding concerns resource
ownership and memory consumption; it does not establish a leak on every frame or a measured
FPS loss.

#### Relevant code and ownership

[GdxTextureManager](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxTextureManager.java#L38)
creates a `Pixmap` in `loadTextureData()` and passes it to `new Texture(preLoadData, false)`
in `loadTexture()`. There is no corresponding `preLoadData.dispose()` on success or failure.
[TextureAtlasFinalizeTask.run()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine/src/main/java/org/illarion/engine/backend/shared/TextureAtlasFinalizeTask.java#L92)
does not release that data either.

In libGDX 1.13.1, which this revision declares, `Texture(Pixmap, boolean)` constructs
`PixmapTextureData` with `disposePixmap = false` and `managed = false`. The boolean supplied
to the texture constructor controls mipmaps, not pixmap ownership. `Texture.dispose()` does
not dispose this caller-owned pixmap. The libGDX implementation and ownership contract were
checked for that version.

[GdxWorldMap](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxWorldMap.java#L80)
creates a 1024 by 1024 RGB888 pixmap. Its `dispose()` method only disposes the texture.
The missing pixmap cleanup concerns approximately 3 MiB of pixel storage per instance,
excluding additional overhead. This is calculated from the dimensions and format.

#### Suggested verification

1. In a test with a valid graphics context, upload a pixmap through `GdxTextureManager` and
   check its disposal state after the method returns. Also exercise an upload failure.
2. Create and dispose a world-map instance, checking both texture and pixmap cleanup.
3. Repeat the resource lifecycle and inspect native allocations or process memory, not
   only Java heap usage. Process memory need not fall immediately after a native free.

These are proposed verification steps; no repeated-lifecycle memory benchmark has been run.

#### Possible fix

Make ownership explicit and dispose temporary loading pixmaps once upload is complete,
including exception paths. Check context-recreation requirements before releasing any
retained data. Release the world-map pixmap when the map is disposed, after stopping or
synchronising any producers that might still write to it.

Do not solve this by making every `GdxTexture.dispose()` delete its backing texture:
multiple atlas regions share that texture. Cleanup must occur once at the owning resource.
Tests should cover successful upload, failed upload, normal map updates, disposal and
protection against double disposal or access after disposal.

---

## Ticket 3: Minimap-Uploads

### Title

A single minimap tile change triggers a full 1024 by 1024 texture upload

### Body

#### Summary

The minimap tracks changes with one boolean. Updating even one pixel causes the complete
1024 by 1024 RGB888 image to be uploaded on the next render, while holding the pixel-buffer
monitor. This transfers 3 MiB of pixel data for a potentially tiny change.

This is a potential performance bug based on the upload path. Transfer size is calculated
from the format and dimensions; gameplay stutter and GPU timings have not been measured.
Clean frames already skip the upload, so this is not an unconditional upload on every frame.

#### Relevant code and execution path

- [GameMiniMap.update()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illaclient/src/main/java/illarion/client/world/GameMiniMap.java#L579)
  filters unchanged saved tile data and forwards accepted changes to the world map.
- [GdxWorldMap.setTile()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxWorldMap.java#L107)
  writes a pixel and sets `mapDirty = true`.
- [GdxWorldMap.render()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxWorldMap.java#L220)
  calls `Texture.draw(worldMapPixels, 0, 0)` for the entire pixmap.
- [WorldMap](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine/src/main/java/org/illarion/engine/graphic/WorldMap.java#L39)
  defines both dimensions as 1024.

#### Suggested verification

1. Start with a populated, clean world map.
2. Change a single valid tile within the current map level and bounds.
3. Render once and inspect the texture upload dimensions.
4. Render again without further changes and confirm that no upload occurs.

The current path uploads the complete image after step 2. For sparse changes, the expected
improvement would be an upload proportional to the affected region, while retaining full
uploads for complete refreshes.

#### Possible fix

Accumulate dirty rectangles or chunks and upload them once per frame, with a full-upload
fallback when a sufficiently large part of the map changes. Avoid creating one temporary
pixmap or issuing one upload for every individual tile.

Preserve synchronisation between rendering and map updates, account for RGB row alignment,
and ensure that changes arriving during an upload are not lost. Compare final map pixels
for sparse updates, full refreshes and map origin/level changes. Measure upload time and
transferred bytes before choosing the threshold for the full-upload fallback.

---

## Ticket 4: Blockierende Texturladung

### Title

Texture atlas finalisation can block the render thread waiting for background decoding

### Body

#### Summary

Texture decoding is scheduled in the background, but its finalisation can reach the render
thread before decoding finishes. The finalisation task then calls `FutureTask.get()` and
blocks the render thread until the background task completes.

This defeats the separation between background decoding and graphics-context upload and
can interrupt loading-screen rendering and input processing. The blocking path is visible
in the code; no duration or gameplay-frequency claim is based on a runtime measurement.

#### Relevant code and execution path

1. [TextureAtlasListXmlLoadingTask](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine/src/main/java/org/illarion/engine/backend/shared/TextureAtlasListXmlLoadingTask.java#L133)
   schedules a preload future and queues atlas finalisation when parsing reaches the end
   of the atlas entry. It does not require the preload future to be complete.
2. [ListenerApplication.render()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/ListenerApplication.java#L69)
   calls the texture manager's `update()` before rendering the game.
3. [AbstractTextureManager.update()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine/src/main/java/org/illarion/engine/backend/shared/AbstractTextureManager.java#L145)
   executes queued tasks. Its 100 ms time check happens only after a task returns.
4. [TextureAtlasFinalizeTask.run()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illagameengine/src/main/java/org/illarion/engine/backend/shared/TextureAtlasFinalizeTask.java#L92)
   calls `preLoadTask.get()` without checking readiness.

The 100 ms check cannot bound a wait inside a task. Even without that wait, processing
uploads for roughly 100 ms can span several frames at 60 FPS. Neither observation implies
that every frame incurs this cost; the path applies while resources are loading.

#### Suggested verification

Use a controlled test in which a queued atlas preload is held on a latch. Allow the rendering
update to process its finalisation task before releasing the latch. The current path will
wait inside `preLoadTask.get()`. A corrected path should return without waiting and finalise
the atlas in a later update after decoding has finished.

Also test failed decoding and cold startup. This test scenario is proposed, not a report of
an already executed reproduction.

#### Possible fix

Queue finalisation only when both decoding and atlas metadata preparation are complete, or
defer incomplete tasks without blocking or repeatedly polling them in the same frame.
Do not mark a deferred task as completed: the current `finally` block sets `done = true`,
so a readiness check inserted inside that block's `try` would require particular care.

Keep OpenGL upload on the graphics thread. Preserve failure handling, progress tracking and
the synchronous loading fallback. Consider a smaller upload budget based on a monotonic
clock, with different budgets for loading screens and active gameplay. Budgeting alone
cannot interrupt a single long upload or replace the readiness check.

---

## Ticket 5: Aufgeschobene Update-Aufgaben

### Title

addTaskForLater can execute newly queued tasks in the current update instead of the next one

### Body

#### Summary

`UpdateTaskManager.addTaskForLater()` is documented as scheduling a task for the next update.
However, `onUpdateGame()` drains the same queue until it becomes empty. If a running task
calls `addTaskForLater()`, the new task can execute in that same update invocation.

Apart from the scheduling-contract mismatch, chains of deferred tasks can extend one update
and postpone rendering. A task that continually requeues itself can prevent that invocation
from returning. This last case is a controlled failure scenario, not a claim that an
existing gameplay task has been observed doing so indefinitely.

#### Relevant code

[UpdateTaskManager](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illaclient/src/main/java/illarion/client/util/UpdateTaskManager.java#L73)
contains the drain-until-empty loop. `addTaskForLater()` at line 112 appends to the same queue,
without separating current-update work from next-update work.

[PlayingState.update()](https://github.com/Illarion-eV/Illarion-Java/blob/d71f2c3a883dd1c0cd7d950ecf83e0c78cafb75e/illaclient/src/main/java/illarion/client/states/PlayingState.java#L82)
runs the task manager before the map update. Rendering cannot proceed through the normal
game loop until this work returns.

#### Minimal verification scenario

1. Enqueue task A using `addTaskForLater()`.
2. Let A record its execution and enqueue task B using `addTaskForLater()`.
3. Call `onUpdateGame()` once with a valid game container.

Following the current code, both A and B run during step 3. Under the documented next-update
behaviour, only A should run, with B waiting for the second `onUpdateGame()` call.
This is a source-derived test scenario; it has not been executed as a regression test here.

#### Possible fix

Establish an explicit boundary between the tasks eligible for the current update and tasks
deferred to the next one, for example with separate queues or a safely captured batch.
Keep the intentional immediate execution of `addTask()` on the update thread distinct.

Audit movement and GUI callers before changing the behaviour, because some may have come
to rely on same-update execution. Preserve ordering, thread safety and complete world-state
transitions. A time budget alone would not guarantee the documented deferral semantics.

Regression coverage should include A scheduling B, finite self-requeueing, concurrent
producers, the immediate `addTask()` path and task exceptions. Measure frame times and queue
age separately before introducing any additional limits for large pre-existing backlogs.
