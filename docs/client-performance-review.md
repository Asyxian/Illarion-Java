# Client performance review

Reviewed on 29 September 2026, on `codex/modernize-build` with libGDX 1.13.1.
This is a static review of the client and its rendering backend, not an in-game
benchmark. The observations below establish code behaviour; their contribution
to frame time, memory consumption and loading time still needs measurement.
No production code was changed during this review.

## Recommended order

| Order | Candidate | Main benefit | Scope |
| --- | --- | --- | --- |
| 1 | Repair avatar label dimension caching | Less repeated text measurement | Small |
| 2 | Release native pixel buffers | Lower native memory consumption | Small, with lifecycle checks |
| 3 | Upload only changed minimap regions | Less texture transfer and lock time | Medium |
| 4 | Avoid blocking texture finalisation | More responsive loading | Medium |
| 5 | Cache recurring text layouts | Less CPU work with labels and GUI text | Medium |
| 6 | Narrow mouse hit testing and static scene updates | Less scene traversal and calculation | Medium to large |
| 7 | Control bursts of queued game updates | Fewer frame-time spikes | Requires ordering analysis |

All candidates are client-side and can be implemented without changing the
server protocol. None inherently requires abandoning Java 8 source compatibility.
The order reflects confidence and implementation scope, not measured speed gains.

## 1. Avatar label dimension caching never becomes clean

In `illaclient/src/main/java/illarion/client/graphics/AvatarTextTag.java`,
`setCharacterName()` and `setHealthState()` set `dimensionsDirty` to true.
`calculateTextLocations()` checks that flag at line 202, but never clears it.
`Avatar.update()` calls the label update while `renderName` is true (line 366).
Consequently, unchanged displayed names and health labels are measured again
on each update after the first text change.

`GdxFont.getWidth()` itself constructs a glyph layout via `setText()` for the
normal font and, when present, the outline font. The layout object is pooled,
but the text measurement still takes place.

**Adjustment:** separate text dimensions from screen positioning. Recalculate
widths, heights and internal offsets only when text or font metrics change,
then clear the dimension flag. Continue updating the screen rectangle when the
avatar moves or its height changes. Simply clearing the existing flag at the
end of the current method would leave moving labels at stale positions.

**Validation:** count width measurements for unchanged labels; test movement,
name changes, health changes, height changes and hiding/revealing labels.
This is the best small, well-defined first optimisation.

## 2. Pixel buffer ownership is incomplete

`illagameengine-libgdx/src/main/java/org/illarion/engine/backend/gdx/GdxTextureManager.java`
loads PNG data into a `Pixmap` (line 44) and constructs
`new Texture(preLoadData, false)` at line 54. Neither this method nor its atlas
finalisation caller releases that pixel buffer.

Inspection of the locally installed libGDX 1.13.1 bytecode confirms that this
texture constructor uses `PixmapTextureData` with `disposePixmap = false` and
`managed = false`. `Texture.dispose()` deletes the GPU resource but does not
dispose this caller-owned pixmap. The pixel data therefore remains allocated
after uploading, even though this path no longer needs a CPU copy.

There is a related omission in `GdxWorldMap.dispose()` (line 230): it disposes
the texture, but not `worldMapPixels`. That pixmap must remain alive while the
map is updated; it should be released at the end of the map's lifecycle.

**Adjustment:** define ownership explicitly and dispose temporary loading
pixmaps after upload, including failure paths. Dispose the world-map pixmap
only after producers have stopped using it. Check any context-recreation
requirements before disposing retained data. Do not make every
`GdxTexture.dispose()` delete the underlying texture: atlas regions share it.

**Validation:** native memory before/after loading; repeated world-map creation
and disposal; loading failures; no double disposal or access after disposal.
Heap and GC statistics alone do not capture these native allocations. This is
a memory-efficiency issue, not evidence of a leak on every rendered frame or
of a particular FPS improvement.

## 3. Minimap changes upload the complete texture

`GdxWorldMap.setTile()` marks the map dirty when a pixel is written (line 137).
`render()` then uploads the entire pixmap while holding its monitor (line 220).
The constants in `WorldMap` are 1024 by 1024, and the format is RGB888:
each full upload contains 3 MiB of pixel data, even for one changed pixel.
This is a calculated transfer size, not a measured transfer rate. Uploads are
already skipped when the map is clean; this is not unconditional work per frame.

`GameMiniMap.update()` already filters unchanged saved tile data. Nevertheless,
every accepted small change still causes the full upload. A full refresh also
requests 1,048,576 tile coordinates in `GdxWorldMap.setMapChanged()`; this runs
through a background executor and should not be described as a per-frame loop.

**Adjustment:** accumulate dirty rectangles or chunks and transfer only those
regions once per frame, with a full-upload fallback for large changes. Preserve
synchronisation between producers and rendering and account for row alignment
when uploading RGB regions. Avoid allocating a temporary image per changed tile.

**Validation:** sparse updates, movement through unexplored areas, full refresh,
map origin/level changes, update/upload overlap, transferred bytes and time spent
holding the pixel-buffer lock. Compare the resulting map pixel-for-pixel.

## 4. Texture loading can block the render thread

`ListenerApplication.render()` calls the texture manager update before rendering
the game (line 74). In `AbstractTextureManager.update()` (line 143), upload tasks
run until approximately 100 ms have elapsed, with the clock checked only after
each task. That is already much larger than the approximately 16.7 ms available
at the configured foreground limit of 60 FPS.

More significantly, `TextureAtlasFinalizeTask.run()` calls `preLoadTask.get()`
(line 94). The XML loader queues finalisation independently of whether the
background PNG decoding has completed. The render thread can therefore wait
for unfinished background work, exceeding even the 100 ms target. A single
large GPU upload can also exceed a budget by itself.

**Adjustment:** make only completed decode results eligible for finalisation;
handle failed futures without waiting; apply a smaller, explicit upload budget
using a monotonic clock. Preserve ordering between finalisation, progress
reporting and resource availability. Loading screens may use a different
budget from active gameplay.

**Validation:** cold/warm startup, slow decoding, failed textures, loading-screen
responsiveness and total load duration. Check the synchronous fallback in
`AbstractTextureManager.getTexture()` as well. The impact during normal play
depends on whether assets still need loading; fully loaded textures bypass this
work. A smaller budget can improve responsiveness while increasing total load time.

## 5. Repeated text drawing repeats glyph layout work

`GdxGraphics.drawText()` (line 395) calls `GlyphLayout.setText()` on every draw,
and again for an outline when present. Callers include `AvatarTextTag`,
`TextTag` and Nifty's `IgeRenderDevice.renderFont()`. Object pooling reduces
allocation but does not cache the computed layout.

**Adjustment:** start with stable avatar labels. Cache layouts or prepared text
geometry by content and relevant font/style state, updating position separately.
Account for scale, colour/alpha and outline differences; avoid an unbounded
global cache of chat messages or retaining pooled objects after returning them.

**Validation:** many visible labels, outlined fonts, chat-heavy GUI, text changes,
colour changes and scaling. Measure layout time and allocations separately.
This complements candidate 1, which only avoids redundant width measurement.

## 6. Scene work is repeated even when much of the scene is unchanged

`MapDisplayManager.update()` publishes a `CurrentMouseLocationEvent` on every
update (line 152). `AbstractScene.updateScene()` copies the ordered element list
into a reusable array, dispatches each event from front to back until handled,
and updates every element. A mouse miss can traverse the whole scene.

`AbstractEntity.update()` recalculates display bounds, fading and lighting;
`Tile.update()` also checks neighbouring light gradients and computes corner
colours. These operations are not all needed for every static entity on every
frame. Rendering already has viewport culling in `performRendering()`, so
adding another generic render visibility test is not the main opportunity.

**Adjustment:** first measure event traversal and entity update time separately.
Consider a spatial candidate set for hit testing, preserving front-to-back
selection. Cache static bounds and use explicit invalidation for movement,
scale, lighting, neighbours and fading. Cache the scene snapshot only when
membership and ordering have not changed.

**Risks:** unchanged mouse coordinates do not mean an unchanged target: the
camera and objects move, and tiles may disappear. Hover handling also drives
mouse movement targets, and highlights are reset during rendering. Skipping
events solely because the mouse has not moved would break behaviour. Offscreen
entities may still need animation, light and movement updates.

**Validation:** stationary and moving mouse, camera movement, overlapping items,
continuous movement, disappearing targets, doors, roof fading and light changes.

## 7. Queued game work has no per-frame limit

`PlayingState.update()` drains `UpdateTaskManager` before the map update.
`UpdateTaskManager.onUpdateGame()` (line 72) polls until the queue is empty.
Tasks can enqueue further work, so a burst can delay both input feedback and
the next rendered frame. Callers include movement, chat, inventory and dialogs;
this queue is not synonymous with all incoming network messages.

**Adjustment:** record task counts, queue age and execution time first. Combine
redundant presentation updates where semantics allow it. Only then introduce
budgets or split expensive work at safe boundaries. Maintain ordering and atomic
world-state transitions; do not drop protocol events or defer time-sensitive
movement indiscriminately. A budget can create backlog and visible latency.

**Validation:** movement with chat/inventory bursts, consistent final world/UI
state, oldest queued task age, frame-time percentiles and recovery after bursts.

## Measurement and implementation plan

1. Record a repeatable baseline on the same runtime, window size, VSync setting
   and game location: idle, many visible names, movement into new map areas,
   chat/inventory activity and fog. Measure cold startup separately.
2. Collect frame-time median, p95 and p99, plus separate update/render CPU times.
   Use JDK 25 Flight Recorder for CPU, allocation, locks and GC; add GPU timings
   or an OpenGL profiler if CPU evidence does not explain slow frames. Measure
   native process memory separately from Java heap usage.
3. Reuse the existing render-call counter in `ListenerApplication` and
   `ApplicationGameContainer.getDiagnosticLines()`, adding focused counters
   for text measurement, minimap upload bytes and queue execution if needed.
   The sprite-batch counter is not a complete count of every OpenGL draw call.
4. Implement label dimension caching and pixel-buffer ownership in separate
   changes with regression tests. Compare each with the baseline before taking
   on minimap uploads, loading scheduling or broader rendering changes.

Avoid claiming gains from raising the 60 FPS cap, blindly removing locks,
parallelising OpenGL calls, or replacing all collections. Texture atlases,
sprite batching, event/layout pools, viewport culling and reusable scene
framebuffers already exist. Measure where those mechanisms fall short.

Verification for this review consisted of tracing the local update/render and
resource-loading paths, inspecting the installed dependency bytecode, and
checking whitespace. No gameplay profile, benchmark or performance percentage
was produced. No server connection, dependency download or build was needed.
