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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Synthetic world and bounded chat history; no server or GUI library is required. */
public final class LabModel {
    public record Message(long id, String text) {
    }

    private final SettingsStore store;
    private final List<Message> messages = new ArrayList<>();
    private Settings settings;
    private long nextMessage;
    private boolean itemInBag = true;
    private float playerX = 100;

    public LabModel(SettingsStore store) throws IOException {
        this.store = store;
        settings = store.load();
    }

    public Settings settings() {
        return settings;
    }

    public void save(Settings proposed) throws IOException {
        store.save(proposed);
        settings = proposed;
    }

    public void append(String text) {
        if (!text.isBlank()) {
            messages.add(new Message(nextMessage++, text));
            if (messages.size() > 200) {
                messages.removeFirst();
            }
        }
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public boolean itemInBag() {
        return itemInBag;
    }

    public void moveItem(boolean intoBag) {
        itemInBag = intoBag;
    }

    public float playerX() {
        return playerX;
    }

    public void movePlayer(float distance, boolean keyboardCaptured) {
        if (settings.wasd() && !keyboardCaptured) {
            playerX = Math.max(30, Math.min(450, playerX + distance));
        }
    }
}
