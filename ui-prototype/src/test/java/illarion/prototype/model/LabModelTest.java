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
package illarion.prototype.model;

import illarion.prototype.storage.FileSettingsStore;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.testng.Assert.*;

public class LabModelTest {
    @Test
    public void savesToIsolatedFileAndReloads() throws Exception {
        var directory = Files.createTempDirectory("ui-settings-test");
        var file = directory.resolve("settings.properties");
        try {
            var store = new FileSettingsStore(file);
            var model = new LabModel(store);
            assertEquals(model.settings(), Settings.defaults());
            var changed = new Settings(false, false, true, true, 1.25f);
            model.save(changed);
            assertEquals(new LabModel(store).settings(), changed);
            Files.writeString(file, "uiScale=NaN");
            assertThrows(IOException.class, store::load);
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    @Test
    public void failedSaveDoesNotApplySettings() throws Exception {
        var model = new LabModel(new SettingsStore() {
            @Override
            public Settings load() {
                return Settings.defaults();
            }

            @Override
            public void save(Settings settings) throws IOException {
                throw new IOException("Read-only test store");
            }
        });
        assertThrows(IOException.class, () -> model.save(new Settings(false, false, true, true, 1.5f)));
        assertEquals(model.settings(), Settings.defaults());
    }

    @Test
    public void historyIsBoundedAndKeyboardCaptureBlocksMovement() throws Exception {
        var model = new LabModel(new SettingsStore() {
            @Override
            public Settings load() {
                return Settings.defaults();
            }

            @Override
            public void save(Settings settings) {
            }
        });
        for (int index = 0; index < 250; index++) {
            model.append("Message " + index);
        }

        assertEquals(model.messages().size(), 200);
        assertEquals(model.messages().getFirst().id(), 50L);
        model.movePlayer(20, true);
        assertEquals(model.playerX(), 100f);
        model.movePlayer(20, false);
        assertEquals(model.playerX(), 120f);
        assertTrue(model.itemInBag());
        model.moveItem(false);
        assertFalse(model.itemInBag());
    }
}
