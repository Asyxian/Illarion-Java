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
package illarion.client.gui.scene2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import de.lessvoid.nifty.Nifty;
import illarion.client.Game;
import illarion.client.IllaClient;
import illarion.client.settings.SettingsService.Choice;
import illarion.client.util.Lang;
import illarion.common.config.ConfigSystem;
import org.bushe.swing.event.EventBus;
import org.bushe.swing.event.EventServiceLocator;
import org.bushe.swing.event.ThreadSafeEventService;
import org.illarion.engine.*;
import org.illarion.engine.graphic.GraphicResolution;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/** Opens the real client login and switches to settings without requesting a server login. */
public class SettingsNativeTest {
    private Game game;
    private Nifty nifty;
    private Stage stage;
    private InputProcessor loginInput;
    private int phase;
    private int frames;
    private boolean heldKey;
    private boolean heldButton;
    private boolean checkedClosingBarrier;
    private long started;
    private boolean completed;
    private String savedResolution;
    private String serverAddress;

    @Test(timeOut = 90_000)
    public void realLoginSettingsAndBack() throws Exception {
        EventServiceLocator.setEventService(EventServiceLocator.SERVICE_NAME_EVENT_BUS, new ThreadSafeEventService());
        Files.createDirectories(Path.of(System.getProperty("user.home"), ".illarion"));
        Object client = IllaClient.getInstance();
        var prepare = IllaClient.class.getDeclaredMethod("prepareConfig");
        prepare.setAccessible(true);
        prepare.invoke(client);
        Lang.getInstance().recheckLocale(System.getProperty("illarion.test.settingsLanguage", "en"));
        IllaClient.getCfg().set("resolution", "1100 x 800 x 32 @ 60Hz");
        IllaClient.getCfg().set("showOwnAvatarTag", false);
        IllaClient.getCfg().set("lastLogin", "");
        IllaClient.getCfg().set("musicOn", true);
        IllaClient.getCfg().set("musicVolume", 25.f);
        IllaClient.getCfg().set("alertVolume", 50.f);
        game = new Game();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        GameListener driver = new GameListener() {
            @Override
            public void create(GameContainer container) {
                try {
                    put(client, "gameContainer", container);
                    EventBus.subscribe(IllaClient.CFG_RESOLUTION, IllaClient.getInstance());
                    EventBus.subscribe(IllaClient.CFG_FULLSCREEN, IllaClient.getInstance());
                    EventBus.subscribe(IllaClient.CFG_VSYNC, IllaClient.getInstance());
                    game.create(container);
                    // Load the graphical resources, leaving audio/world catalogues unused in this UI test.
                    container.getEngine().getAssets().getTextureManager().startLoading();
                    game.enterState(Game.STATE_LOGIN);
                    Input nativeInput = Gdx.input;
                    Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                            new Class<?>[]{Input.class}, (proxy, method, arguments) -> {
                                if (method.getName().equals("isKeyPressed") && heldKey
                                        && (Integer) arguments[0] == Input.Keys.ANY_KEY) {
                                    return true;
                                }

                                if (method.getName().equals("isButtonPressed") && heldButton
                                        && (Integer) arguments[0] == Input.Buttons.LEFT) {
                                    return true;
                                }

                                return method.invoke(nativeInput, arguments);
                            });
                    nifty = (Nifty) get(game, "nifty");
                } catch (Throwable problem) {
                    failure.set(problem);
                    Gdx.app.exit();
                }
            }

            @Override
            public void update(GameContainer container, int delta) {
                if (failure.get() == null) {
                    try {
                        game.update(container, delta);
                    } catch (Throwable problem) {
                        failure.set(problem);
                        Gdx.app.exit();
                    }
                }
            }

            @Override
            public void render(GameContainer container) {
                if (failure.get() != null) {
                    return;
                }

                try {
                    game.render(container);
                    // Capture after the engine's final batch, which normally flushes after this callback.
                    container.getEngine().getGraphics().flush();
                    assertEquals(Gdx.gl.glGetError(), GL20.GL_NO_ERROR);
                    exercise(container);
                } catch (Throwable problem) {
                    failure.set(problem);
                    Gdx.app.exit();
                }
            }

            @Override
            public void resize(GameContainer container, int width, int height) {
                game.resize(container, width, height);
            }

            @Override
            public boolean isClosingGame() {
                return true;
            }

            @Override
            public void dispose() {
                game.dispose();
            }
        };
        var container = EngineManager.createDesktopGame(Backend.libGDX, driver, 1100, 800, false, false, true);
        var configuration = (Lwjgl3ApplicationConfiguration) get(container, "config");
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        started = System.nanoTime();
        container.startGame();
        if (failure.get() != null) {
            throw new AssertionError("Native settings sequence failed in phase " + phase, failure.get());
        }

        assertEquals(phase, 7);
        assertTrue(completed);
    }

    private void exercise(GameContainer container) throws Exception {
        if (System.nanoTime() - started > 60_000_000_000L) {
            throw new AssertionError("Timed out in phase " + phase);
        }

        frames++;
        Scene2dSettingsView view = (Scene2dSettingsView) get(game, "settingsView");
        if (view != null) {
            stage = (Stage) get(view, "stage");
        }

        switch (phase) {
            case 0 -> {
                if (frames > 60 && nifty.getCurrentScreen() != null
                        && container.getEngine().getAssets().getTextureManager().isLoadingDone()
                        && nifty.getCurrentScreen().getScreenId().equals("login")
                        && (int) get(game, "activeListener") == Game.STATE_LOGIN) {
                    assertEquals(nifty.getCurrentScreen().getScreenId(), "login");
                    capture("login-before");
                    loginInput = Gdx.input.getInputProcessor();
                    heldKey = true;
                    heldButton = true;
                    clickLoginOptions();
                    phase++;
                }
            }
            case 1 -> {
                if (view != null && heldKey) {
                    assertTrue((Boolean) get(view, "waitingForRelease"));
                    click("showOwnAvatarTag");
                    assertFalse(((CheckBox) actor("showOwnAvatarTag")).isChecked());
                    heldKey = false;
                    heldButton = false;
                    return;
                }

                if (view != null && !(Boolean) get(view, "waitingForRelease")) {
                    capture("settings");
                    click("showOwnAvatarTag");
                    assertTrue(((CheckBox) actor("showOwnAvatarTag")).isChecked());
                    click("cancelButton");
                    phase++;
                }
            }
            case 2 -> {
                if (view == null) {
                    assertFalse(IllaClient.getCfg().getBoolean("showOwnAvatarTag"));
                    assertSame(Gdx.input.getInputProcessor(), loginInput);
                    clickLoginOptions();
                    phase++;
                }
            }
            case 3 -> {
                if (view != null && !(Boolean) get(view, "waitingForRelease")) {
                    assertFalse(((CheckBox) actor("showOwnAvatarTag")).isChecked());
                    ((CheckBox) actor("showOwnAvatarTag")).setChecked(true);
                    ((Slider) actor("musicVolume")).setValue(37);
                    ((Slider) actor("soundVolume")).setValue(42);
                    ((CheckBox) actor("vsync")).setChecked(false);
                    SelectBox<Choice> resolutions = actor("resolution");
                    resolutions.setSelectedIndex(0);
                    savedResolution = (String) resolutions.getSelected().value();
                    heldKey = true;
                    heldButton = true;
                    click("saveButton");
                    phase++;
                }
            }
            case 4 -> {
                if (view != null && heldKey) {
                    assertFalse(view.isReadyToClose(), "Do not return held keys to the login screen");
                    assertNotSame(Gdx.input.getInputProcessor(), loginInput);
                    checkedClosingBarrier = true;
                    heldKey = false;
                    assertFalse(view.isReadyToClose(), "Mouse releases must also stay in the settings screen");
                    heldButton = false;
                    return;
                }

                if (view == null) {
                    assertTrue(checkedClosingBarrier);
                    assertSame(Gdx.input.getInputProcessor(), loginInput);
                    assertTrue(new ConfigSystem(IllaClient.getFile("Illarion.xcfgz"))
                            .getBoolean("showOwnAvatarTag"));
                    assertEquals(container.getEngine().getSounds().getMusicVolume(), 0.37f, 0.001f);
                    assertEquals(container.getEngine().getSounds().getSoundVolume(), 0.42f, 0.001f);
                    assertFalse(IllaClient.getCfg().getBoolean("vsync"));
                    GraphicResolution savedMode = new GraphicResolution(savedResolution);
                    assertEquals(container.getWidth(), savedMode.getWidth());
                    assertEquals(container.getHeight(), savedMode.getHeight());
                    clickLoginOptions();
                    phase++;
                }
            }
            case 5 -> {
                if (view != null && !(Boolean) get(view, "waitingForRelease")) {
                    assertTrue(((CheckBox) actor("showOwnAvatarTag")).isChecked());
                    assertEquals(((Slider) actor("musicVolume")).getValue(), 37.f);
                    Gdx.app.postRunnable(() -> Gdx.graphics.setWindowedMode(800, 600));
                    phase++;
                }
            }
            case 6 -> {
                if (Gdx.graphics.getWidth() == 800 && Gdx.graphics.getHeight() == 600) {
                    assertEquals(stage.getViewport().getWorldWidth(), 800.f);
                    capture("settings-800x600");
                    TextField address = actor("serverAddress");
                    ScrollPane scroll = actor("settingsScroll");
                    scroll.setScrollPercentY(1);
                    scroll.updateVisualScroll();
                    scroll.validate();
                    stage.setKeyboardFocus(address);
                    String before = address.getText();
                    serverAddress = IllaClient.getCfg().getString("serverAddress");
                    Gdx.input.getInputProcessor().keyTyped('a');
                    assertNotEquals(address.getText(), before);
                    assertEquals(nifty.getCurrentScreen().findNiftyControl("nameTxt",
                            de.lessvoid.nifty.controls.TextField.class).getRealText(), "");
                    Gdx.input.getInputProcessor().keyDown(Input.Keys.ESCAPE);
                    Gdx.input.getInputProcessor().keyUp(Input.Keys.ESCAPE);
                    phase++;
                }
            }
            case 7 -> {
                if (view == null) {
                    capture("login-after");
                    assertSame(Gdx.input.getInputProcessor(), loginInput);
                    assertEquals(IllaClient.getCfg().getString("serverAddress"), serverAddress);
                    assertEquals(nifty.getCurrentScreen().getScreenId(), "login");
                    completed = true;
                    Gdx.app.exit();
                }
            }
            default -> throw new AssertionError(phase);
        }
    }

    private void clickLoginOptions() {
        var button = nifty.getCurrentScreen().findElementById("optionBtn");
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + button.getHeight() / 2;
        Gdx.input.getInputProcessor().mouseMoved(x, y);
        Gdx.input.getInputProcessor().touchDown(x, y, 0, Input.Buttons.LEFT);
        Gdx.input.getInputProcessor().touchUp(x, y, 0, Input.Buttons.LEFT);
    }

    private void click(String name) {
        Actor actor = actor(name);
        Vector2 position = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2, actor.getHeight() / 2));
        stage.stageToScreenCoordinates(position);
        var input = Gdx.input.getInputProcessor();
        input.touchDown((int) position.x, (int) position.y, 0, Input.Buttons.LEFT);
        input.touchUp((int) position.x, (int) position.y, 0, Input.Buttons.LEFT);
    }

    private <T extends Actor> T actor(String name) {
        return stage.getRoot().findActor(name);
    }

    private static void capture(String name) {
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        try {
            String language = System.getProperty("illarion.test.settingsLanguage", "en");
            PixmapIO.writePNG(Gdx.files.local("build/settings-preview/" + language + "-" + name + ".png"),
                    pixels, -1, true);
        } finally {
            pixels.dispose();
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
