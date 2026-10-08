/*
 * This file is part of the Illarion project.
 *
 * Copyright © 2026 - Illarion e.V.
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
package illarion.client.gui.controller;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.builder.EffectBuilder;
import de.lessvoid.nifty.builder.LayerBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.builder.ScreenBuilder;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.input.InputSystem;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.sound.SoundDevice;
import illarion.client.IllaClient;
import illarion.client.gui.controller.game.InformHandler;
import illarion.client.gui.controller.game.UpdatableHandler;
import illarion.client.net.server.InformMsg;
import illarion.client.net.server.ServerReplyResult;
import illarion.client.test.ScopedMocks;
import illarion.client.test.TestObjects;
import illarion.client.util.UpdateTaskManager;
import illarion.client.world.World;
import illarion.common.config.Config;
import org.illarion.engine.GameContainer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class GameScreenControllerTest extends ScopedMocks {
    private MockedStatic<World> world;
    private MockedStatic<IllaClient> client;
    private GameScreenController controller;
    private Collection<ScreenController> children;
    private Collection<UpdatableHandler> updaters;
    private InformHandler informs;
    private UpdateTaskManager updates;
    private GameContainer container;
    private Nifty nifty;
    private Screen game;
    private long time;

    @BeforeMethod
    public void setUp() {
        // Keep the real lifecycle methods, without constructing unrelated world-dependent handlers.
        controller = TestObjects.newInstance(GameScreenController.class);
        children = new ArrayList<>();
        updaters = new ArrayList<>();
        TestObjects.setInternalState(controller, "childControllers", children);
        TestObjects.setInternalState(controller, "childUpdateControllers", updaters);
        informs = new InformHandler();
        children.add(informs);
        TestObjects.setInternalState(controller, "informHandler", informs);
        updates = new UpdateTaskManager();
        container = Mockito.mock(GameContainer.class);
        Config config = Mockito.mock(Config.class);

        world = scoped(Mockito.mockStatic(World.class));
        world.when(World::getGameGui).thenReturn(controller);
        world.when(World::getUpdateTaskManager).thenAnswer(invocation -> updates);
        world.when(World::cleanEnvironment).thenAnswer(invocation -> {
            updates = new UpdateTaskManager();
            return null;
        });
        client = scoped(Mockito.mockStatic(IllaClient.class));
        client.when(IllaClient::getCfg).thenReturn(config);
    }

    @Test
    public void bindingDoesNotMakeTheScreenReady() {
        createScreens();
        controller.bind(nifty, game);
        assertFalse(controller.isReady());
    }

    @Test
    public void childrenStartBeforeTheScreenBecomesReady() {
        ScreenController child = Mockito.mock(ScreenController.class);
        Mockito.doAnswer(invocation -> {
            assertFalse(controller.isReady());
            return null;
        }).when(child).onStartScreen();

        children.add(child);
        controller.onStartScreen();
        assertTrue(controller.isReady());
        Mockito.verify(child).onStartScreen();
        Mockito.verifyNoMoreInteractions(child);
    }

    @Test
    public void leavingRevokesReadinessBeforeChildrenStop() {
        ScreenController child = Mockito.mock(ScreenController.class);
        Mockito.doAnswer(invocation -> {
            assertFalse(controller.isReady());
            return null;
        }).when(child).onEndScreen();

        createScreens();
        controller.bind(nifty, game);
        controller.onStartScreen();
        children.add(child);
        controller.onEndScreen();
        assertFalse(controller.isReady());
        Mockito.verify(child).onEndScreen();
        Mockito.verifyNoMoreInteractions(child);
    }

    @Test
    public void childUpdatesOnlyRunWhileTheScreenIsReady() {
        UpdatableHandler updater = Mockito.mock(UpdatableHandler.class);

        updaters.add(updater);
        controller.onUpdateGame(container, 20);
        controller.onStartScreen();
        controller.onUpdateGame(container, 20);
        controller.onEndScreen();
        controller.onUpdateGame(container, 20);
        Mockito.verify(updater).update(container, 20);
        Mockito.verifyNoMoreInteractions(updater);
    }

    @Test
    public void informReplyWaitsForTheSecondScreenStart() {
        createScreens();
        login();
        nifty.gotoScreen("login");
        frames(300);
        nifty.gotoScreen("gamescreen");
        frames(20);
        InformMsg reply = new InformMsg();
        TestObjects.setInternalState(reply, "informText", "Welcome");
        TestObjects.setInternalState(reply, "informType", 100);
        assertEquals(reply.execute(), ServerReplyResult.Reschedule);
        frames(300);
        assertTrue(controller.isReady());
        assertEquals(reply.execute(), ServerReplyResult.Success);
    }

    @Test
    public void welcomeQueuedDuringEachLoginIsRemoved() {
        createScreens();

        for (int session = 0; session < 2; session++) {
            nifty.gotoScreen("gamescreen");
            frames(20);
            assertEquals(nifty.getCurrentScreen().getScreenId(), "login");
            queueWelcome();
            frames(20);
            assertTrue(messagePanel().getChildren().isEmpty(), "Do not build messages during the login fade");
            frames(300);
            assertNotNull(game.findElementById("welcome"));
            frames(2000);
            assertTrue(messagePanel().getChildren().isEmpty(), "The welcome message must finish hiding");
            nifty.gotoScreen("login");
            frames(300);
        }
    }

    @Test
    public void welcomeAfterScreenStartIsRemoved() {
        createScreens();
        login();
        queueWelcome();
        frames(20);
        assertNotNull(game.findElementById("welcome"));
        frames(2000);
        assertTrue(messagePanel().getChildren().isEmpty());
    }

    private void createScreens() {
        time = 1000;
        RenderDevice render = Mockito.mock(RenderDevice.class);
        Mockito.when(render.getWidth()).thenReturn(800);
        Mockito.when(render.getHeight()).thenReturn(600);
        SoundDevice sound = Mockito.mock(SoundDevice.class);
        InputSystem input = Mockito.mock(InputSystem.class);
        ScreenController loginController = Mockito.mock(ScreenController.class);

        nifty = new Nifty(render, sound, input, () -> time);

        ScreenBuilder login = new ScreenBuilder("login", loginController);
        LayerBuilder loginLayer = new LayerBuilder("loginLayer");
        loginLayer.childLayoutCenter();
        EffectBuilder fade = new EffectBuilder("fade");
        fade.length(200);
        fade.effectParameter("start", "#ff");
        fade.effectParameter("end", "#00");
        loginLayer.onEndScreenEffect(fade);
        login.layer(loginLayer);
        nifty.addScreen("login", login.build(nifty));

        ScreenBuilder gameBuilder = new ScreenBuilder("gamescreen", controller);
        LayerBuilder gameLayer = new LayerBuilder("gameLayer");
        gameLayer.childLayoutVertical();
        PanelBuilder panel = new PanelBuilder("scriptMessagePanel");
        panel.childLayoutVertical();
        panel.width("100%");
        panel.height("100%");
        gameLayer.panel(panel);
        gameBuilder.layer(gameLayer);
        game = gameBuilder.build(nifty);
        nifty.addScreen("gamescreen", game);
        nifty.gotoScreen("login");
        frames(300);
    }

    private void login() {
        nifty.gotoScreen("gamescreen");
        frames(300);
    }

    private void frames(int milliseconds) {
        for (int elapsed = 0; elapsed < milliseconds; elapsed += 20) {
            time += 20;
            nifty.update();

            if (controller.isReady()) {
                updates.onUpdateGame(container, 20);
            }

            nifty.render(false);
        }
    }

    private Element messagePanel() {
        return game.findElementById("scriptMessagePanel");
    }

    private void queueWelcome() {
        PanelBuilder message = new PanelBuilder("welcome");
        message.childLayoutCenter();
        message.width("100px");
        message.height("20px");
        EffectBuilder hide = new EffectBuilder("hide");
        hide.startDelay(500);
        message.onHideEffect(hide);
        informs.showInform(message, messagePanel(), messagePanel());
    }
}
