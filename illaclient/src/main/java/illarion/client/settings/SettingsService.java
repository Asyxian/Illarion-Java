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

import java.util.List;
import java.util.Map;

/** The client options boundary. No UI toolkit types or live configuration objects escape it. */
public interface SettingsService {
    enum Kind {
        BOOLEAN, VOLUME, CHOICE, TEXT, INTEGER
    }

    record Choice(Object value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    record Setting(String key, String label, String section, Kind kind, List<Choice> choices) {
        public Setting {
            choices = List.copyOf(choices);
        }
    }

    List<Setting> settings();

    Map<String, Object> load();

    /** Validate the complete draft before applying it through the client's existing configuration events. */
    void save(Map<String, Object> draft);
}
