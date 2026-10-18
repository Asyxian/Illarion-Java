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
import illarion.prototype.scene2d.Scene2dView;
import illarion.prototype.storage.FileSettingsStore;
import org.illarion.engine.Backend;
import org.illarion.engine.EngineManager;

import java.nio.file.Path;

/** Composition root: the only non-adapter class selecting a concrete UI implementation. */
public final class PrototypeMain {
    private PrototypeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        Path settings = Path.of(System.getProperty("illarion.prototype.settings",
                Path.of(System.getProperty("user.home"), ".illarion-ui-prototype", "settings.properties").toString()));
        LabModel model = new LabModel(new FileSettingsStore(settings));
        PrototypeGame game = new PrototypeGame(model, () -> new Scene2dView(model));
        var container = EngineManager.createDesktopGame(Backend.libGDX, game, 1100, 800, false, true, true);
        container.setTitle("Illarion - Scene2D UI prototype");
        container.setResizeable(true);
        container.startGame();
    }
}
