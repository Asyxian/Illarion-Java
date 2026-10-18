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
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import illarion.prototype.PrototypeView;
import illarion.prototype.model.LabModel;
import illarion.prototype.model.Settings;

import java.io.IOException;

/** All Scene2D actors, input events, colours and layout details stay in this adapter. */
public final class Scene2dView implements PrototypeView {
    private record Anchor(String message, float offset, boolean bottom) {
    }

    private final LabModel model;
    private final Skin skin = PrototypeSkin.create();
    private final ScreenViewport viewport = new ScreenViewport();
    private final Stage stage = new Stage(viewport);
    private final Table root = new Table();
    private final Table chat = new Table();
    private final ScrollPane scroll = new ScrollPane(chat, skin);
    private final TextField entry = new TextField("", skin);
    private final Label ownName = new Label("Test character - unharmed", skin);
    private final Label status = new Label("", skin);
    private final TextButton bag = new TextButton("Bag: apple", skin);
    private final TextButton ground = new TextButton("Ground: empty", skin);
    private final DragAndDrop dragging = new DragAndDrop();
    private final InputProcessor previousInput = Gdx.input.getInputProcessor();
    private final InputProcessor input;
    private Window settingsWindow;
    private boolean closed;

    public Scene2dView(LabModel model) {
        this.model = model;
        root.setFillParent(true);
        root.pad(16);
        stage.addActor(root);
        stage.addActor(ownName);
        ownName.setTouchable(Touchable.disabled);
        Table header = new Table();
        Label title = new Label("ILLARION / UI LAB", skin);
        title.setEllipsis(true);
        header.add(title).growX().left();
        header.add(button("Options", "options", this::openSettings)).width(110).height(34);
        root.add(header).growX().row();
        root.add(new Label("A / D: move   |   Click the world: release text focus", skin)).left().padTop(8).row();
        root.add().height(110).row();

        Table body = new Table();
        Table conversation = new Table();
        scroll.setName("chatScroll");
        scroll.setFadeScrollBars(false);
        scroll.setSmoothScrolling(false);
        scroll.setScrollingDisabled(true, false);
        chat.top().left();
        chat.setName("chatContent");
        conversation.add(scroll).grow().colspan(2).row();
        entry.setName("chatInput");
        entry.setMessageText("Type here; A / D must not move the character");
        entry.setMaxLength(300);
        entry.setTextFieldListener((field, character) -> {
            if (character == '\r' || character == '\n') {
                send();
            }
        });
        conversation.add(entry).growX().height(34).padTop(8);
        conversation.add(button("Send", "send", this::send)).width(70).height(34).padTop(8);
        body.add(conversation).grow();
        Table inventory = new Table();
        inventory.defaults().growX().height(36).pad(4);
        inventory.add(new Label("Drag the apple", skin)).row();
        bag.setName("bag");
        ground.setName("ground");
        inventory.add(bag).row();
        inventory.add(ground).row();
        inventory.add(button("Incoming message", "incoming", () -> append("Incoming: a longer test message."))).row();
        inventory.add(button("Add 30 messages", "burst", () -> {
            for (int index = 0; index < 30; index++) {
                append("Message " + model.messages().size() + ": the quick brown fox walks across the map.");
            }
        })).row();
        body.add(inventory).width(190).top().padLeft(12);
        root.add(body).grow().row();
        root.add(status).left().padTop(8);
        connectDrag(bag, true);
        connectDrag(ground, false);
        input = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    cancelSettings();
                    stage.setKeyboardFocus(null);
                    return true;
                }

                boolean captured = capturesKeyboard();
                return stage.keyDown(keycode) || captured;
            }

            @Override
            public boolean keyUp(int keycode) {
                return stage.keyUp(keycode) || capturesKeyboard();
            }

            @Override
            public boolean keyTyped(char character) {
                return stage.keyTyped(character) || capturesKeyboard();
            }

            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                boolean handled = stage.touchDown(x, y, pointer, button);
                if (!handled) {
                    stage.setKeyboardFocus(null);
                }

                return handled;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                return stage.touchUp(x, y, pointer, button);
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                return stage.touchDragged(x, y, pointer);
            }

            @Override
            public boolean mouseMoved(int x, int y) {
                return stage.mouseMoved(x, y);
            }

            @Override
            public boolean scrolled(float x, float y) {
                return stage.scrolled(x, y);
            }
        };
        // Exclusive ownership in this lab. Do not leave events queued in the legacy input processor.
        Gdx.input.setInputProcessor(input);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        for (int index = 0; index < 40; index++) {
            append("History " + index + ": scroll upwards, then add messages or resize the window.");
        }
    }

    private TextButton button(String text, String name, Runnable action) {
        TextButton result = new TextButton(text, skin);
        result.setName(name);
        result.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return result;
    }

    private void send() {
        append(entry.getText());
        entry.setText("");
    }

    private Anchor anchor() {
        if (scroll.getMaxY() - scroll.getScrollY() < 2) {
            return new Anchor(null, 0, true);
        }

        for (Actor row : chat.getChildren()) {
            float top = chat.getHeight() - row.getY() - row.getHeight();
            if (top + row.getHeight() >= scroll.getScrollY()) {
                return new Anchor(row.getName(), scroll.getScrollY() - top, false);
            }
        }

        return new Anchor(null, 0, false);
    }

    private void restore(Anchor anchor) {
        root.validate();
        scroll.validate();
        chat.validate();
        Actor row = anchor.message() == null ? null : chat.findActor(anchor.message());
        float offset = row == null ? 0 : Math.min(anchor.offset(), Math.max(0, row.getHeight() - 1));
        float position = row == null ? 0 : chat.getHeight() - row.getY() - row.getHeight() + offset;
        scroll.setScrollY(anchor.bottom() ? scroll.getMaxY() : position);
        scroll.updateVisualScroll();
    }

    private void append(String text) {
        Anchor before = anchor();
        model.append(text);
        chat.clearChildren();
        for (var message : model.messages()) {
            Label label = new Label(message.text(), skin);
            label.setName("message-" + message.id());
            label.setWrap(true);
            chat.add(label).growX().pad(4).row();
        }

        restore(before);
    }

    private void connectDrag(TextButton slot, boolean isBag) {
        dragging.addSource(new DragAndDrop.Source(slot) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (settingsWindow != null || model.itemInBag() != isBag) {
                    return null;
                }

                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(isBag);
                payload.setDragActor(new Label("APPLE", skin));
                stage.setKeyboardFocus(null);
                return payload;
            }
        });
        dragging.addTarget(new DragAndDrop.Target(slot) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return settingsWindow == null && !payload.getObject().equals(isBag);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                model.moveItem(isBag);
                refreshSlots();
            }
        });
    }

    private void refreshSlots() {
        bag.setText(model.itemInBag() ? "Bag: apple" : "Bag: empty");
        ground.setText(model.itemInBag() ? "Ground: empty" : "Ground: apple");
    }

    private void openSettings() {
        if (settingsWindow != null) {
            return;
        }

        Settings saved = model.settings();
        settingsWindow = new Window("Options - prototype settings only", skin);
        settingsWindow.setName("settings");
        settingsWindow.setModal(true);
        settingsWindow.setMovable(false);
        Table form = new Table();
        form.defaults().left().pad(10);
        CheckBox wasd = option(form, "Use A / D for movement", "wasd", saved.wasd());
        CheckBox quests = option(form, "Show quests on the game map", "quests", saved.quests());
        CheckBox own = option(form, "Show your own name and health", "ownName", saved.ownName());
        CheckBox fps = option(form, "Show frames per second", "fps", saved.fps());
        form.add(new Label("UI scale (100% - 150%)", skin)).row();
        Slider scale = new Slider(1, 1.5f, 0.25f, false, skin);
        scale.setName("scale");
        scale.setValue(saved.uiScale());
        form.add(scale).growX().row();
        Label error = new Label("", skin);
        error.setWrap(true);
        form.add(error).growX().row();
        ScrollPane formScroll = new ScrollPane(form, skin);
        formScroll.setScrollingDisabled(true, false);
        settingsWindow.add(formScroll).grow().colspan(2).row();
        settingsWindow.add(button("Save", "saveSettings", () -> {
            try {
                model.save(new Settings(wasd.isChecked(), quests.isChecked(), own.isChecked(), fps.isChecked(),
                        scale.getValue()));
                cancelSettings();
                resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            } catch (IOException exception) {
                error.setText("Saving failed. Settings were not applied.");
                Gdx.app.error("UI prototype", "Cannot save settings", exception);
            }
        })).growX().height(36).pad(8);
        settingsWindow.add(button("Cancel", "cancelSettings", this::cancelSettings)).growX().height(36).pad(8);
        stage.addActor(settingsWindow);
        placeSettings();
        stage.setKeyboardFocus(settingsWindow);
        stage.setScrollFocus(formScroll);
    }

    private CheckBox option(Table form, String text, String name, boolean enabled) {
        CheckBox option = new CheckBox("  " + text, skin);
        option.setName(name);
        option.setChecked(enabled);
        form.add(option).row();
        return option;
    }

    private void placeSettings() {
        if (settingsWindow != null) {
            settingsWindow.setSize(Math.min(440, viewport.getWorldWidth() - 24),
                    Math.min(460, viewport.getWorldHeight() - 24));
            settingsWindow.setPosition(viewport.getWorldWidth() / 2, viewport.getWorldHeight() / 2, Align.center);
        }
    }

    private void cancelSettings() {
        if (settingsWindow != null) {
            settingsWindow.remove();
            settingsWindow = null;
            stage.setKeyboardFocus(null);
            stage.setScrollFocus(scroll);
        }
    }

    @Override
    public boolean capturesKeyboard() {
        return settingsWindow != null || stage.getKeyboardFocus() instanceof TextField || dragging.isDragging();
    }

    @Override
    public void update(float seconds) {
        stage.act(Math.min(seconds, 0.1f));
        if (settingsWindow == null) {
            stage.setScrollFocus(scroll);
        }

        ownName.setVisible(model.settings().ownName());
        float scale = model.settings().uiScale();
        ownName.setPosition(model.playerX() / scale, viewport.getWorldHeight() - 116);
        status.setText((capturesKeyboard() ? "UI owns keyboard" : "World owns keyboard")
                + (model.settings().fps() ? " | FPS " + Gdx.graphics.getFramesPerSecond() : ""));
    }

    @Override
    public void resize(int width, int height) {
        Anchor before = anchor();
        viewport.setUnitsPerPixel(1 / model.settings().uiScale());
        viewport.update(width, height, true);
        root.invalidateHierarchy();
        restore(before);
        placeSettings();
    }

    @Override
    public void render() {
        viewport.apply();
        stage.draw();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (Gdx.input.getInputProcessor() == input) {
                Gdx.input.setInputProcessor(previousInput);
            }

            dragging.clear();
            stage.dispose();
            skin.dispose();
        }
    }
}
