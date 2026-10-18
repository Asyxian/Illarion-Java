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

import illarion.client.IllaClient;
import illarion.client.Servers;
import illarion.client.util.translation.Translator;
import illarion.common.bug.CrashReporter;
import illarion.common.config.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/** Adapter for the existing configuration and its audio/graphics change subscribers. */
public final class ClientSettingsService implements SettingsService {
    private final Config config;
    private final boolean customServer;
    private final List<Setting> settings = new ArrayList<>();
    private final ResourceBundle labels;

    public ClientSettingsService(Config config, List<String> resolutions, boolean customServer, Locale locale) {
        this.config = config;
        this.customServer = customServer;
        labels = ResourceBundle.getBundle("options", locale);
        addBooleans("general", "wasdWalk", "zqsdWalk", "useItemWithE", "disableChatAfterSending",
                "showQuestsOnGameMap", "showQuestsOnMiniMap", "showOwnAvatarTag", "logNpcSpeech",
                "hideNpcSpeechFromChatBox", "showPing");
        addIndexed(CrashReporter.CFG_KEY, "report", "general", "report.ask", "report.always", "report.never");
        addIndexed(Translator.CFG_KEY_PROVIDER, "translation.provider", "general",
                "translation.provider.none", "translation.provider.mymemory");
        addIndexed(Translator.CFG_KEY_DIRECTION, "translation.direction", "general",
                "translation.direction.default", "translation.direction.enToDe", "translation.direction.deToEn");
        List<String> available = new ArrayList<>(resolutions);
        String current = config.getString(IllaClient.CFG_RESOLUTION);
        if (current != null && !available.contains(current)) {
            available.add(current);
        }

        add(IllaClient.CFG_RESOLUTION, "resolution", "graphics", Kind.CHOICE,
                available.stream().distinct().map(value -> new Choice(value, value)).toList());
        addBooleans("graphics", IllaClient.CFG_FULLSCREEN, IllaClient.CFG_VSYNC, "showFps", "limitBackgroundFps");
        addBooleans("sound", "soundOn", "musicOn", "RPalertEnabled");
        for (String key : List.of("soundVolume", "musicVolume", "alertVolume")) {
            add(key, key, "sound", Kind.VOLUME, List.of());
        }

        if (customServer) {
            add("serverAddress", "serverAddress", "server", Kind.TEXT, List.of());
            add("serverPort", "serverPort", "server", Kind.INTEGER, List.of());
            add("clientVersion", "clientVersion", "server", Kind.INTEGER, List.of());
            addBooleans("server", "serverAccountLogin");
            add("resetServerSettings", "serverReset", "server", Kind.BOOLEAN, List.of());
        }
    }

    private void addBooleans(String section, String... keys) {
        for (String key : keys) {
            add(key, key, section, Kind.BOOLEAN, List.of());
        }
    }

    private void addIndexed(String key, String label, String section, String... choices) {
        List<Choice> values = new ArrayList<>();
        for (int i = 0; i < choices.length; i++) {
            values.add(new Choice(i, labels.getString(choices[i])));
        }

        add(key, label, section, Kind.CHOICE, values);
    }

    private void add(String key, String label, String section, Kind kind, List<Choice> choices) {
        settings.add(new Setting(key, labels.getString(label), section, kind, choices));
    }

    @Override
    public List<Setting> settings() {
        return List.copyOf(settings);
    }

    @Override
    public Map<String, Object> load() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Setting setting : settings) {
            String key = setting.key();
            if (key.equals("resetServerSettings")) {
                values.put(key, false);
                continue;
            }

            Object value = switch (setting.kind()) {
                case BOOLEAN -> config.getBoolean(key);
                case VOLUME -> config.getFloat(key);
                case INTEGER -> config.getInteger(key);
                case TEXT -> config.getString(key);
                case CHOICE -> setting.choices().getFirst().value() instanceof Integer
                        ? config.getInteger(key) : config.getString(key);
            };
            values.put(key, value == null ? "" : value);
        }

        if (customServer && !config.getBoolean("clientVersionOverwrite")) {
            values.put("clientVersion", Servers.Customserver.getClientVersion());
        }

        return Map.copyOf(values);
    }

    @Override
    public void save(Map<String, Object> draft) {
        Map<String, Object> values = new LinkedHashMap<>();
        boolean resetServer = customServer && Boolean.TRUE.equals(draft.get("resetServerSettings"));
        for (Setting setting : settings) {
            if (resetServer && setting.section().equals("server")) {
                continue;
            }

            Object value = draft.get(setting.key());
            try {
                value = validate(setting, value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(setting.label(), exception);
            }

            values.put(setting.key(), value);
        }

        values.remove("resetServerSettings");
        if (resetServer) {
            values.put("serverAddress", Servers.Customserver.getServerHost());
            values.put("serverPort", Servers.Customserver.getServerPort());
            values.put("clientVersion", Servers.Customserver.getClientVersion());
            values.put("serverAccountLogin", true);
        }

        if (customServer) {
            values.put("clientVersionOverwrite",
                    !values.get("clientVersion").equals(Servers.Customserver.getClientVersion()));
        }

        values.forEach((key, value) -> {
            if (value instanceof Boolean flag) {
                config.set(key, flag);
            } else if (value instanceof Integer number) {
                config.set(key, number);
            } else if (value instanceof Float volume) {
                config.set(key, volume);
            } else {
                config.set(key, (String) value);
            }
        });
        config.save();
    }

    private static Object validate(Setting setting, Object value) {
        switch (setting.kind()) {
            case BOOLEAN -> {
                if (value instanceof Boolean) {
                    return value;
                }
            }
            case VOLUME -> {
                if (value instanceof Number number && Float.isFinite(number.floatValue())
                        && number.floatValue() >= 0 && number.floatValue() <= 100) {
                    return number.floatValue();
                }
            }
            case CHOICE -> {
                if (setting.choices().stream().anyMatch(choice -> choice.value().equals(value))) {
                    return value;
                }
            }
            case TEXT -> {
                if (value instanceof String text && !text.isBlank()) {
                    return text.trim();
                }
            }
            case INTEGER -> {
                int parsed = Integer.parseInt(String.valueOf(value));
                int maximum = setting.key().equals("serverPort") ? 65535 : 255;
                int minimum = setting.key().equals("serverPort") ? 1 : 0;
                if (parsed >= minimum && parsed <= maximum) {
                    return parsed;
                }
            }
        }

        throw new IllegalArgumentException("Invalid option value");
    }
}
