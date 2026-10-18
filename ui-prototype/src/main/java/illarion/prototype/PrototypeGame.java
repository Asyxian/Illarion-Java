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
package illarion.prototype;

import illarion.prototype.model.LabModel;
import org.illarion.engine.GameContainer;
import org.illarion.engine.GameListener;
import org.illarion.engine.graphic.Color;
import org.illarion.engine.input.Key;

import java.util.function.Supplier;

/** Runs the experiment through the same engine lifecycle as the real client. */
public final class PrototypeGame implements GameListener {
    private final LabModel model;
    private final Supplier<PrototypeView> createView;
    private PrototypeView view;

    public PrototypeGame(LabModel model, Supplier<PrototypeView> createView) {
        this.model = model;
        this.createView = createView;
    }

    @Override
    public void create(GameContainer container) {
        container.getEngine().getAssets().getTextureManager().addTextureDirectory("gui");
        view = createView.get();
    }

    @Override
    public void update(GameContainer container, int delta) {
        view.update(delta / 1000f);
        var input = container.getEngine().getInput();
        int direction = (input.isKeyDown(Key.D) ? 1 : 0) - (input.isKeyDown(Key.A) ? 1 : 0);
        model.movePlayer(direction * delta * 0.15f, view.capturesKeyboard());
    }

    @Override
    public void render(GameContainer container) {
        var graphics = container.getEngine().getGraphics();
        graphics.drawRectangle(0, 0, container.getWidth(), container.getHeight(), Color.midnightBlue);
        int playerY = Math.round(120 * model.settings().uiScale());
        graphics.drawRectangle((int) model.playerX(), playerY, 30, 30, Color.emerald);
        if (model.settings().quests()) {
            graphics.drawRectangle(220, playerY + 40, 10, 10, Color.sunflower);
        }

        // Scene2D draws last, with a separate batch. Never interleave active batches.
        graphics.flush();
        view.render();
    }

    @Override
    public void resize(GameContainer container, int width, int height) {
        if (view != null && width > 0 && height > 0) {
            view.resize(width, height);
        }
    }

    @Override
    public boolean isClosingGame() {
        return true;
    }

    @Override
    public void dispose() {
        if (view != null) {
            view.close();
        }
    }
}
