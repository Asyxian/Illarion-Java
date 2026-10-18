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
package illarion.prototype.scene2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import illarion.prototype.PrototypeGame;
import illarion.prototype.model.LabModel;
import illarion.prototype.model.Settings;
import illarion.prototype.model.SettingsStore;
import org.illarion.engine.*;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/** Uses real Scene2D hit testing and the existing engine in a hidden OpenGL window. */
public class Scene2dNativeTest {
    private Scene2dView view;
    private Stage stage;
    private LabModel model;
    private int frames;
    private float originalScroll;
    private long dragStarted;
    private Vector2 dragTarget;
    private String readingMessage;

    @Test(timeOut = 45_000)
    public void exercisesSettingsChatFocusDragAndResizeOnTheEngine() throws Exception {
        model = new LabModel(new SettingsStore() {
            private Settings settings = Settings.defaults();

            @Override
            public Settings load() {
                return settings;
            }

            @Override
            public void save(Settings proposed) {
                settings = proposed;
            }
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        var game = new PrototypeGame(model, () -> {
            view = new Scene2dView(model);
            return view;
        });
        GameListener driver = new GameListener() {
            @Override
            public void create(GameContainer container) {
                game.create(container);
                try {
                    Field field = Scene2dView.class.getDeclaredField("stage");
                    field.setAccessible(true);
                    stage = (Stage) field.get(view);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }

            @Override
            public void update(GameContainer container, int delta) {
                game.update(container, delta);
            }

            @Override
            public void render(GameContainer container) {
                try {
                    game.render(container);
                    assertEquals(Gdx.gl.glGetError(), GL20.GL_NO_ERROR);
                    exercise();
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
                var owned = Gdx.input.getInputProcessor();
                game.dispose();
                assertNotSame(Gdx.input.getInputProcessor(), owned);
                game.dispose();
            }
        };
        var container = EngineManager.createDesktopGame(Backend.libGDX, driver, 1100, 800, false, false, true);
        Field configField = container.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        var config = (Lwjgl3ApplicationConfiguration) configField.get(container);
        config.setInitialVisible(false);
        config.disableAudio(true);
        container.startGame();
        if (failure.get() != null) {
            throw new AssertionError("Native UI test failed", failure.get());
        }

        assertTrue(frames >= 6, "The interaction sequence did not complete");
    }

    private void exercise() {
        switch (frames) {
            case 0 -> {
                click("options");
                assertTrue(view.capturesKeyboard());
                CheckBox own = actor("ownName");
                assertFalse(own.isChecked());
                click("ownName");
                click("cancelSettings");
                assertFalse(model.settings().ownName());
                click("options");
                assertFalse(((CheckBox) actor("ownName")).isChecked());
                click("ownName");
                click("saveSettings");
                assertTrue(model.settings().ownName());
                click("chatInput");
                Gdx.input.getInputProcessor().keyTyped('a');
                Gdx.input.getInputProcessor().keyTyped('d');
                assertEquals(((TextField) actor("chatInput")).getText(), "ad");
                assertTrue(view.capturesKeyboard());
                float before = model.playerX();
                model.movePlayer(40, view.capturesKeyboard());
                assertEquals(model.playerX(), before);
                click("send");
                assertEquals(model.messages().getLast().text(), "ad");
                ScrollPane scroll = actor("chatScroll");
                scroll.setScrollY(100);
                scroll.updateVisualScroll();
                originalScroll = scroll.getScrollY();
                click("incoming");
                assertEquals(scroll.getScrollY(), originalScroll, 1f);
                readingMessage = firstVisibleMessage();
                Vector2 source = centre("bag");
                dragTarget = centre("ground");
                Gdx.input.getInputProcessor().touchDown((int) source.x, (int) source.y, 0, Input.Buttons.LEFT);
                Gdx.input.getInputProcessor().touchDragged((int) dragTarget.x, (int) dragTarget.y, 0);
                dragStarted = System.nanoTime();
                frames++;
            }
            case 1 -> {
                if (System.nanoTime() - dragStarted > 350_000_000L) {
                    Gdx.input.getInputProcessor().touchUp(
                            (int) dragTarget.x, (int) dragTarget.y, 0, Input.Buttons.LEFT);
                    assertFalse(model.itemInBag());
                    assertFalse(view.capturesKeyboard());
                    frames++;
                    Gdx.app.postRunnable(() -> Gdx.graphics.setWindowedMode(900, 700));
                }
            }
            case 2 -> {
                assertEquals(firstVisibleMessage(), readingMessage, "Resize changed the message being read");
                capture("prototype.png");
                click("options");
                ((Slider) actor("scale")).setValue(1.5f);
                click("saveSettings");
                assertEquals(model.settings().uiScale(), 1.5f);
                frames++;
            }
            case 3 -> {
                assertEquals(firstVisibleMessage(), readingMessage, "Scaling changed the message being read");
                click("options");
                frames++;
            }
            case 4 -> {
                Window window = actor("settings");
                assertTrue(window.getX() >= 0 && window.getY() >= 0);
                assertEquals(stage.getViewport().getWorldWidth(), Gdx.graphics.getWidth() / 1.5f, 1f);
                capture("settings-150-percent.png");
                Gdx.input.getInputProcessor().keyDown(Input.Keys.ESCAPE);
                assertFalse(view.capturesKeyboard());
                frames++;
            }
            case 5 -> {
                frames++;
                Gdx.app.exit();
            }
            default -> {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Actor> T actor(String name) {
        Actor result = stage.getRoot().findActor(name);
        assertNotNull(result, "Missing actor " + name);
        return (T) result;
    }

    private String firstVisibleMessage() {
        Table content = actor("chatContent");
        ScrollPane scroll = actor("chatScroll");
        for (Actor row : content.getChildren()) {
            if (content.getHeight() - row.getY() > scroll.getScrollY()) {
                return row.getName();
            }
        }

        throw new AssertionError("No visible chat message");
    }

    private Vector2 centre(String name) {
        for (Actor child : stage.getActors()) {
            if (child instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout layout) {
                layout.validate();
            }
        }
        Actor target = actor(name);
        if (target instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout layout) {
            layout.validate();
        }

        return stage.stageToScreenCoordinates(target.localToStageCoordinates(
                new Vector2(target.getWidth() / 2, target.getHeight() / 2)));
    }

    private void click(String name) {
        Vector2 point = centre(name);
        var input = Gdx.input.getInputProcessor();
        input.touchDown((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        input.touchUp((int) point.x, (int) point.y, 0, Input.Buttons.LEFT);
        stage.act(0);
        for (Actor actor : stage.getActors()) {
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout layout) {
                layout.validate();
            }
        }
    }

    private void capture(String filename) {
        System.out.println("Capture " + filename + ": frame=" + frames + ", settings=" + model.settings());
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight());
        try {
            if ("prototype.png".equals(filename)) {
                int x = Math.round((model.playerX() + 15) * pixels.getWidth() / Gdx.graphics.getWidth());
                float playerY = 120 * model.settings().uiScale() + 15;
                int y = pixels.getHeight() - Math.round(playerY * pixels.getHeight() / Gdx.graphics.getHeight());
                int pixel = pixels.getPixel(x, y);
                int red = (pixel >>> 24) & 255;
                int green = (pixel >>> 16) & 255;
                assertTrue(green > red + 40, "The engine's world drawing must remain visible below Scene2D");
            }

            PixmapIO.writePNG(Gdx.files.local("build/ui-preview/" + filename), pixels, -1, true);
        } finally {
            pixels.dispose();
        }
    }
}
