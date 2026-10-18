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
package illarion.client.settings;

/** Exclusive settings screen lifecycle, independent of the widget toolkit. */
public interface SettingsView extends AutoCloseable {
    void update(float seconds);

    void render();

    void resize(int width, int height);

    void requestClose();

    /** Closing waits until held keys and mouse buttons are released before returning input ownership. */
    boolean isReadyToClose();

    @Override
    void close();
}
