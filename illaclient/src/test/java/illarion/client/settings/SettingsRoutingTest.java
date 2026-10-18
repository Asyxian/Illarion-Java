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

import de.lessvoid.nifty.Nifty;
import illarion.client.Game;
import illarion.client.IllaClient;
import illarion.client.states.GameState;
import illarion.common.config.Config;
import org.illarion.engine.GameContainer;
import org.illarion.engine.nifty.IgeInputSystem;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class SettingsRoutingTest {
    @Test
    public void niftyRemainsDefaultAndAnExclusiveViewSuspendsLegacyUpdates() throws Exception {
        String previousFlag = System.getProperty("illarion.ui.scene2dSettings");
        System.clearProperty("illarion.ui.scene2dSettings");
        try (var client = mockStatic(IllaClient.class)) {
            client.when(IllaClient::getCfg).thenReturn(mock(Config.class));
            Game game = new Game();
            Nifty nifty = mock(Nifty.class);
            IgeInputSystem input = mock(IgeInputSystem.class);
            GameState login = mock(GameState.class);
            GameContainer container = mock(GameContainer.class, RETURNS_DEEP_STUBS);
            put(game, "nifty", nifty);
            put(game, "niftyInput", input);
            put(game, "activeListener", Game.STATE_LOGIN);
            put(game, "targetListener", Game.STATE_LOGIN);
            ((GameState[]) get(game, "gameStates"))[Game.STATE_LOGIN] = login;
            game.openSettings();
            verify(nifty).gotoScreen("options");

            SettingsView view = mock(SettingsView.class);
            put(game, "settingsView", view);
            game.update(container, 20);
            game.render(container);
            verify(view).update(0.02f);
            verify(view).render();
            verify(nifty, never()).update();
            verify(nifty, never()).render(anyBoolean());
            verifyNoInteractions(login);
            when(view.isReadyToClose()).thenReturn(true);
            game.update(container, 20);
            verify(view).close();
            verify(input).resetInputState();
            assertNull(get(game, "settingsView"));
            game.update(container, 20);
            verify(nifty).update();
            verify(login).update(container, 20);
        } finally {
            if (previousFlag == null) {
                System.clearProperty("illarion.ui.scene2dSettings");
            } else {
                System.setProperty("illarion.ui.scene2dSettings", previousFlag);
            }
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void put(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
