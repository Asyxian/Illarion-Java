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
package illarion.prototype.storage;

import illarion.prototype.model.Settings;
import illarion.prototype.model.SettingsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Uses an isolated prototype file, never the real client's configuration. */
public final class FileSettingsStore implements SettingsStore {
    private final Path path;

    public FileSettingsStore(Path path) {
        this.path = path.toAbsolutePath();
    }

    @Override
    public Settings load() throws IOException {
        if (!Files.exists(path)) {
            return Settings.defaults();
        }

        Properties values = new Properties();
        try (var input = Files.newInputStream(path)) {
            values.load(input);
        }

        try {
            return new Settings(Boolean.parseBoolean(values.getProperty("wasd", "true")),
                    Boolean.parseBoolean(values.getProperty("quests", "true")),
                    Boolean.parseBoolean(values.getProperty("ownName", "false")),
                    Boolean.parseBoolean(values.getProperty("fps", "false")),
                    Float.parseFloat(values.getProperty("uiScale", "1")));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid prototype settings: " + path, exception);
        }
    }

    @Override
    public void save(Settings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("wasd", Boolean.toString(settings.wasd()));
        values.setProperty("quests", Boolean.toString(settings.quests()));
        values.setProperty("ownName", Boolean.toString(settings.ownName()));
        values.setProperty("fps", Boolean.toString(settings.fps()));
        values.setProperty("uiScale", Float.toString(settings.uiScale()));
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "settings-", ".tmp");

        try {
            try (var output = Files.newOutputStream(temporary)) {
                values.store(output, "Scene2D prototype only");
            }

            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
