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

/** Library-independent sample of the client's display and input options. */
public record Settings(boolean wasd, boolean quests, boolean ownName, boolean fps, float uiScale) {
    public Settings {
        if (!Float.isFinite(uiScale) || uiScale < 1f || uiScale > 1.5f) {
            throw new IllegalArgumentException("UI scale must be between 1 and 1.5");
        }
    }

    public static Settings defaults() {
        return new Settings(true, true, false, false, 1f);
    }
}
