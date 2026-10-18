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
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import illarion.client.settings.SettingsService;
import illarion.client.settings.SettingsService.Choice;
import illarion.client.settings.SettingsService.Setting;
import illarion.client.settings.SettingsView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/** Owns input and rendering only while the login state's optional settings screen is open. */
public final class Scene2dSettingsView implements SettingsView {
    private final SettingsService service;
    private final Skin skin = SettingsSkin.create();
    private final Stage stage = new Stage(new ScreenViewport());
    private final Map<String, Supplier<Object>> editors = new LinkedHashMap<>();
    private final ResourceBundle labels;
    private final Label error;
    private final InputProcessor previousInput;
    private final InputProcessor input;
    private boolean waitingForRelease = true;
    private boolean closing;
    private boolean saveRequested;
    private boolean closed;

    public Scene2dSettingsView(SettingsService service, Locale locale) {
        try {
            this.service = service;
            labels = ResourceBundle.getBundle("scene2d_options", locale);
            ResourceBundle options = ResourceBundle.getBundle("options", locale);
            error = new Label("", skin);
            error.setWrap(true);
            Table root = new Table(skin);
            root.setFillParent(true);
            root.setBackground("panel");
            root.pad(20);
            root.add(new Label(labels.getString("title"), skin)).padBottom(12).row();
            Table entries = new Table(skin);
            entries.defaults().pad(6);
            Map<String, Object> values = service.load();
            String section = "";
            for (Setting setting : service.settings()) {
                if (!section.equals(setting.section())) {
                    section = setting.section();
                    entries.add(new Label(labels.getString(section), skin)).colspan(2).left().padTop(18).row();
                    if (section.equals("server")) {
                        Label warning = new Label(options.getString("serverWarning"), skin);
                        warning.setWrap(true);
                        entries.add(warning).colspan(2).growX().row();
                    }
                }

                Label label = new Label(setting.label(), skin);
                label.setWrap(true);
                entries.add(label).growX().minWidth(0).left();
                Actor editor = createEditor(setting, values.get(setting.key()));
                editor.setName(setting.key());
                entries.add(editor).width(240).minHeight(28).row();
            }

            ScrollPane scroll = new ScrollPane(entries, skin);
            scroll.setName("settingsScroll");
            scroll.setFadeScrollBars(false);
            scroll.setScrollingDisabled(true, false);
            root.add(scroll).grow().row();
            root.add(error).growX().minHeight(38).row();
            Table buttons = new Table();
            buttons.add(button(options.getString("save"), "saveButton", () -> saveRequested = true))
                    .size(150, 38).pad(6);
            buttons.add(button(options.getString("cancel"), "cancelButton", this::requestClose)).size(150, 38).pad(6);
            root.add(buttons).row();
            stage.addActor(root);
            stage.setScrollFocus(scroll);
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            previousInput = Gdx.input.getInputProcessor();
            input = createInput();
            Gdx.input.setInputProcessor(input);
        } catch (RuntimeException | Error exception) {
            stage.dispose();
            skin.dispose();
            throw exception;
        }
    }

    private InputProcessor createInput() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (acceptsInput()) {
                    if (keycode == Input.Keys.ESCAPE) {
                        requestClose();
                    } else {
                        stage.keyDown(keycode);
                    }
                }

                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                if (acceptsInput()) {
                    stage.keyUp(keycode);
                }

                return true;
            }

            @Override
            public boolean keyTyped(char character) {
                if (acceptsInput()) {
                    stage.keyTyped(character);
                }

                return true;
            }

            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                if (acceptsInput()) {
                    stage.touchDown(x, y, pointer, button);
                }

                return true;
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                if (acceptsInput()) {
                    stage.touchUp(x, y, pointer, button);
                }

                return true;
            }

            @Override
            public boolean touchCancelled(int x, int y, int pointer, int button) {
                stage.touchCancelled(x, y, pointer, button);
                return true;
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                if (acceptsInput()) {
                    stage.touchDragged(x, y, pointer);
                }

                return true;
            }

            @Override
            public boolean mouseMoved(int x, int y) {
                if (acceptsInput()) {
                    stage.mouseMoved(x, y);
                }

                return true;
            }

            @Override
            public boolean scrolled(float x, float y) {
                if (acceptsInput()) {
                    stage.scrolled(x, y);
                }

                return true;
            }
        };
    }

    private boolean acceptsInput() {
        return !waitingForRelease && !closing && !saveRequested;
    }

    private static boolean anyInputDown() {
        if (Gdx.input.isKeyPressed(Input.Keys.ANY_KEY)) {
            return true;
        }

        for (int button = 0; button <= Input.Buttons.FORWARD; button++) {
            if (Gdx.input.isButtonPressed(button)) {
                return true;
            }
        }

        return false;
    }

    private Actor createEditor(Setting setting, Object value) {
        String key = setting.key();
        switch (setting.kind()) {
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox("", skin);
                checkBox.setChecked(Boolean.TRUE.equals(value));
                editors.put(key, checkBox::isChecked);
                return checkBox;
            }
            case VOLUME -> {
                Slider slider = new Slider(0, 100, 1, false, skin);
                slider.setValue(((Number) value).floatValue());
                editors.put(key, slider::getValue);
                return slider;
            }
            case CHOICE -> {
                SelectBox<Choice> select = new SelectBox<>(skin);
                select.setItems(new Array<>(setting.choices().toArray(Choice[]::new)));
                setting.choices().stream().filter(choice -> choice.value().equals(value))
                        .findFirst().ifPresent(select::setSelected);
                editors.put(key, () -> select.getSelected().value());
                return select;
            }
            case TEXT, INTEGER -> {
                TextField field = new TextField(String.valueOf(value), skin);
                editors.put(key, field::getText);
                return field;
            }
        }

        throw new IllegalArgumentException("Unsupported setting: " + key);
    }

    private TextButton button(String label, String name, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.setName(name);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    @Override
    public void update(float seconds) {
        if (waitingForRelease && !anyInputDown()) {
            waitingForRelease = false;
        }

        // Apply outside native input dispatch: display changes may re-enter the resize callback.
        if (saveRequested && !closing) {
            saveRequested = false;
            Map<String, Object> draft = new LinkedHashMap<>();
            editors.forEach((key, editor) -> draft.put(key, editor.get()));
            try {
                service.save(draft);
                requestClose();
            } catch (IllegalArgumentException exception) {
                error.setText(labels.getString("invalid") + ": " + exception.getMessage());
            }
        }

        stage.act(seconds);
    }

    @Override
    public void render() {
        stage.getViewport().apply();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void requestClose() {
        closing = true;
        stage.unfocusAll();
    }

    @Override
    public boolean isReadyToClose() {
        return closing && !anyInputDown();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (Gdx.input.getInputProcessor() == input) {
            Gdx.input.setInputProcessor(previousInput);
        }

        stage.dispose();
        skin.dispose();
    }
}
