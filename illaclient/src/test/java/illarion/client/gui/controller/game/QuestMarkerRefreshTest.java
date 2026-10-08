/*
 * This file is part of the Illarion project.
 *
 * Copyright 2026 - Illarion e.V.
 *
 * Illarion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Illarion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package illarion.client.gui.controller.game;

import de.lessvoid.nifty.controls.ListBox;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import illarion.client.graphics.QuestMarker;
import illarion.client.gui.GameGui;
import illarion.client.gui.MiniMapGui;
import illarion.client.test.ScopedMocks;
import illarion.client.test.TestObjects;
import illarion.client.util.UpdateTaskManager;
import illarion.client.world.GameMap;
import illarion.client.world.MapTile;
import illarion.client.world.World;
import illarion.common.types.ServerCoordinate;
import org.illarion.engine.GameContainer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.testng.Assert.assertEquals;

/** Tests the real refresh and marker lifecycle without starting a graphics engine or a server. */
public class QuestMarkerRefreshTest extends ScopedMocks {
    private static final ServerCoordinate FIRST_TARGET = new ServerCoordinate(100, 100, 0);
    private static final ServerCoordinate SECOND_TARGET = new ServerCoordinate(500, 500, 0);
    private static final int FRAME_TIME = 16;

    private MockedStatic<World> world;

    private GameMap map;
    private QuestHandler handler;
    private UpdateTaskManager updates;
    private GameContainer container;
    private RecordingMiniMap miniMap;
    private Map<ServerCoordinate, MapTile> tiles;

    @DataProvider
    public Object[][] markerSettings() {
        List<Object[]> cases = new ArrayList<>();
        for (int selection : new int[]{0, 1, -1}) {
            for (boolean showMiniMap : new boolean[]{false, true}) {
                for (boolean showGameMap : new boolean[]{false, true}) {
                    cases.add(new Object[]{selection, showMiniMap, showGameMap});
                }
            }
        }

        return cases.toArray(new Object[cases.size()][]);
    }

    @Test(dataProvider = "markerSettings")
    public void preservesSelectionAcrossMapChanges(int selection, boolean showMiniMap, boolean showGameMap)
            throws Exception {
        List<ServerCoordinate> first = Collections.singletonList(FIRST_TARGET);
        List<ServerCoordinate> second = Collections.singletonList(SECOND_TARGET);
        initialise(first, second, selection, showMiniMap, showGameMap);
        Set<ServerCoordinate> selected = selection < 0 ? Collections.emptySet()
                : new HashSet<>(selection == 0 ? first : second);
        Set<ServerCoordinate> all = new HashSet<>(Arrays.asList(FIRST_TARGET, SECOND_TARGET));

        // Visit a map containing the targets, leave it, and return twice.
        for (int visit = 0; visit < 2; visit++) {
            loadTiles(all);
            refresh();
            assertMarkers(selected, all, showMiniMap, showGameMap);
            refresh();
            assertMarkers(selected, all, showMiniMap, showGameMap);

            map.clear();
            refresh();
            assertMarkers(selected, Collections.emptySet(), showMiniMap, showGameMap);
        }
    }

    @Test
    public void sharedTargetRemainsSelectedWithoutDuplicatePointers() throws Exception {
        initialise(Collections.singletonList(FIRST_TARGET), Arrays.asList(FIRST_TARGET, SECOND_TARGET),
                0, true, true);
        Set<ServerCoordinate> all = new HashSet<>(Arrays.asList(FIRST_TARGET, SECOND_TARGET));
        loadTiles(all);
        refresh();
        assertMarkers(Collections.singleton(FIRST_TARGET), all, true, true);

        map.clear();
        refresh();
        assertMarkers(Collections.singleton(FIRST_TARGET), Collections.emptySet(), true, true);
    }

    @Test
    public void selectedQuestWithoutTargetsDoesNotHighlightAnotherQuest() throws Exception {
        initialise(Collections.emptyList(), Collections.singletonList(SECOND_TARGET), 0, true, true);
        loadTiles(Collections.singleton(SECOND_TARGET));
        refresh();
        assertMarkers(Collections.emptySet(), Collections.singleton(SECOND_TARGET), true, true);
    }

    @Test
    public void lastQuestWithoutTargetsDoesNotRemoveSelectedTargets() throws Exception {
        initialise(Collections.singletonList(FIRST_TARGET), Collections.emptyList(), 0, true, false);
        refresh();
        assertMarkers(Collections.singleton(FIRST_TARGET), Collections.emptySet(), true, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void initialise(List<ServerCoordinate> first, List<ServerCoordinate> second, int selection,
                            boolean showMiniMap, boolean showGameMap) throws Exception {
        // Only bypass construction that requires graphics assets; marker methods remain real.
        map = TestObjects.newInstance(GameMap.class);
        tiles = new HashMap<>();
        TestObjects.setInternalState(map, "tiles", tiles);
        TestObjects.setInternalState(map, "mapLock", new ReentrantReadWriteLock());
        TestObjects.setInternalState(map, "activeQuestStartMarkers", new HashMap<>());
        TestObjects.setInternalState(map, "activeQuestTargetMarkers", new HashMap<>());
        TestObjects.setInternalState(map, "inactiveQuestTargetLocations", new HashMap<>());
        TestObjects.setInternalState(map, "showQuestsOnMiniMap", showMiniMap);
        TestObjects.setInternalState(map, "showQuestsOnGameMap", showGameMap);
        miniMap = new RecordingMiniMap();
        updates = new UpdateTaskManager();
        container = Mockito.mock(GameContainer.class);

        GameGui gui = Mockito.mock(GameGui.class);
        Mockito.when(gui.getMiniMapGui()).thenReturn(miniMap);

        world = scoped(Mockito.mockStatic(World.class));
        world.when(World::getMap).thenReturn(map);
        world.when(World::getGameGui).thenReturn(gui);
        world.when(World::getUpdateTaskManager).thenReturn(updates);

        // Replace only the graphical marker, not its carrier or the map's pointer logic.
        scoped(Mockito.mockConstruction(QuestMarker.class));

        Class<?> entryClass = Class.forName(QuestHandler.class.getName() + "$QuestEntry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(int.class, String.class, String.class,
                boolean.class, List.class);
        constructor.setAccessible(true);
        List<Object> entries = Arrays.asList(constructor.newInstance(1, "First", "", false, first),
                constructor.newInstance(2, "Second", "", false, second));
        ListBox list = Mockito.mock(ListBox.class);
        Mockito.when(list.getItems()).thenReturn(entries);
        Mockito.when(list.getSelection()).thenReturn(selection < 0 ? Collections.emptyList()
                : Collections.singletonList(entries.get(selection)));
        Window window = Mockito.mock(Window.class);
        Element element = Mockito.mock(Element.class);
        Screen screen = Mockito.mock(Screen.class);
        Mockito.when(window.getElement()).thenReturn(element);
        Mockito.when(element.findNiftyControl("#questList", ListBox.class)).thenReturn(list);

        handler = new QuestHandler();
        TestObjects.setInternalState(handler, "screen", screen);
        TestObjects.setInternalState(handler, "questWindow", window);
    }

    private void loadTiles(Set<ServerCoordinate> locations) {
        for (ServerCoordinate location : locations) {
            MapTile tile = Mockito.mock(MapTile.class);

            tiles.put(location, tile);
        }
    }

    private void refresh() {
        // MapCompleteMsg uses this entry point; process its real queued update.
        handler.updateAllQuests();
        updates.onUpdateGame(container, FRAME_TIME);
    }

    private void assertMarkers(Set<ServerCoordinate> selected, Set<ServerCoordinate> loaded,
                               boolean showMiniMap, boolean showGameMap) {
        assertEquals(miniMap.currentTargets(), showMiniMap ? selected : Collections.emptySet());
        Map<ServerCoordinate, ?> worldMarkers = TestObjects.getInternalState(map, "activeQuestTargetMarkers");
        assertEquals(worldMarkers.keySet(), showGameMap ? loaded : Collections.emptySet());

        Set<ServerCoordinate> visible = new HashSet<>();
        if (showMiniMap) {
            visible.addAll(selected);
            if (showGameMap) {
                visible.addAll(loaded);
            }
        }

        assertEquals(miniMap.targets(), visible);
        assertEquals(miniMap.pointers.size(), visible.size(), "Each target must have exactly one pointer");
    }

    private static final class RecordedPointer implements MiniMapGui.Pointer {
        private ServerCoordinate target;
        private boolean current;

        @Override
        public void setTarget(ServerCoordinate target) {
            this.target = target;
        }

        @Override
        public void setCurrentQuest(boolean currentQuest) {
            current = currentQuest;
        }
    }

    private static final class RecordingMiniMap implements MiniMapGui {
        private final List<RecordedPointer> pointers = new ArrayList<>();

        @Override
        public Pointer createTargetPointer() {
            return new RecordedPointer();
        }

        @Override
        public Pointer createStartPointer(boolean available) {
            throw new AssertionError("Refreshing quest targets must not create quest start pointers");
        }

        @Override
        public void releasePointer(Pointer pointer) {
            pointers.remove(pointer);
        }

        @Override
        public void addPointer(Pointer pointer) {
            pointers.add((RecordedPointer) pointer);
        }

        @Override
        public void toggleMiniMap() {
            throw new AssertionError("Refreshing quest targets must not toggle the minimap");
        }

        private Set<ServerCoordinate> currentTargets() {
            Set<ServerCoordinate> targets = new HashSet<>();
            for (RecordedPointer pointer : pointers) {
                if (pointer.current) {
                    targets.add(pointer.target);
                }
            }

            return targets;
        }

        private Set<ServerCoordinate> targets() {
            Set<ServerCoordinate> targets = new HashSet<>();
            for (RecordedPointer pointer : pointers) {
                targets.add(pointer.target);
            }

            return targets;
        }
    }
}
