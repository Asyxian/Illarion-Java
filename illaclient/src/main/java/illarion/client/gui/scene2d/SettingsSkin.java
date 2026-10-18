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

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** Temporary skin for the optional settings screen; owns its fonts and textures. */
final class SettingsSkin {
    private SettingsSkin() {
    }

    static Skin create() {
        Skin skin = new Skin();
        Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixels.setColor(Color.WHITE);
            pixels.fill();
            skin.add("pixel", new Texture(pixels));
        } finally {
            pixels.dispose();
        }

        BitmapFont font = new BitmapFont();
        skin.add("default-font", font);
        Drawable panel = skin.newDrawable("pixel", new Color(0.09f, 0.13f, 0.18f, 1));
        Drawable normal = skin.newDrawable("pixel", new Color(0.19f, 0.27f, 0.34f, 1));
        Drawable active = skin.newDrawable("pixel", new Color(0.1f, 0.5f, 0.65f, 1));
        skin.add("panel", panel, Drawable.class);
        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle(normal, active, active, font);
        button.over = active;
        skin.add("default", button);
        Drawable off = skin.newDrawable("pixel", Color.GRAY);
        Drawable on = skin.newDrawable("pixel", Color.CYAN);
        off.setMinWidth(18);
        off.setMinHeight(18);
        on.setMinWidth(18);
        on.setMinHeight(18);
        skin.add("default", new CheckBox.CheckBoxStyle(off, on, font, Color.WHITE));
        Drawable cursor = skin.newDrawable("pixel", Color.WHITE);
        cursor.setMinWidth(2);
        skin.add("default", new TextField.TextFieldStyle(font, Color.WHITE, cursor, active, normal));
        Drawable knob = skin.newDrawable("pixel", Color.LIGHT_GRAY);
        knob.setMinWidth(12);
        knob.setMinHeight(24);
        skin.add("default", new ScrollPane.ScrollPaneStyle(panel, null, null, normal, knob));
        Drawable sliderTrack = skin.newDrawable("pixel", Color.GRAY);
        sliderTrack.setMinHeight(6);
        skin.add("default-horizontal", new Slider.SliderStyle(sliderTrack, knob));
        List.ListStyle list = new List.ListStyle(font, Color.WHITE, Color.WHITE, active);
        skin.add("default", list);
        skin.add("default", new SelectBox.SelectBoxStyle(font, Color.WHITE, normal,
                skin.get(ScrollPane.ScrollPaneStyle.class), list));
        return skin;
    }
}
