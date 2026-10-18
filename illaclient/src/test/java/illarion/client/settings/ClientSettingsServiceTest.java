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

import illarion.client.Servers;
import illarion.common.config.ConfigSystem;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.testng.Assert.*;

public class ClientSettingsServiceTest {
    @DataProvider
    public Object[][] languages() {
        return new Object[][]{{Locale.ENGLISH}, {Locale.GERMAN}};
    }

    @Test(dataProvider = "languages")
    public void roundTripAllOptionsAndValidateBeforeApplying(Locale locale) throws Exception {
        assertEquals(ResourceBundle.getBundle("scene2d_options", locale).getString("invalid"),
                locale.getLanguage().equals("de") ? "Ung\u00fcltige Einstellung" : "Invalid setting");
        var directory = Files.createTempDirectory("client-settings");
        var file = directory.resolve("client.xcfgz");
        try {
            var config = new ConfigSystem(file);
            config.set("resolution", "900 x 700 x 32 @ 60Hz");
            config.set("serverAddress", "localhost");
            config.set("serverPort", 13000);
            config.set("musicOn", true);
            config.set("musicVolume", 25.f);
            var service = new ClientSettingsService(config, List.of("800 x 600 x 32 @ 60Hz"), true, locale);
            var draft = new LinkedHashMap<>(service.load());
            draft.put("showOwnAvatarTag", true);
            draft.put("musicVolume", 73.f);
            assertFalse(config.getBoolean("showOwnAvatarTag"), "Editing a draft must not change live settings");
            assertEquals(config.getFloat("musicVolume"), 25.f);
            draft.put("serverPort", "invalid");
            assertThrows(IllegalArgumentException.class, () -> service.save(draft));
            assertFalse(config.getBoolean("showOwnAvatarTag"), "No earlier field may be applied on validation failure");
            draft.put("serverPort", "65536");
            assertThrows(IllegalArgumentException.class, () -> service.save(draft));
            draft.put("serverPort", "13001");
            draft.put("musicVolume", Float.NaN);
            assertThrows(IllegalArgumentException.class, () -> service.save(draft));
            draft.put("musicVolume", 73.f);
            service.save(draft);
            var reopened = new ClientSettingsService(new ConfigSystem(file), List.of(), true, locale);
            assertTrue((Boolean) reopened.load().get("showOwnAvatarTag"));
            assertEquals(reopened.load().get("musicVolume"), 73.f);
            assertEquals(reopened.load().get("serverPort"), 13001);
            assertEquals(reopened.load().get("resolution"), "900 x 700 x 32 @ 60Hz");
            assertEquals(reopened.settings().size(), service.settings().size());
            assertTrue(service.settings().stream().noneMatch(setting -> setting.label().isBlank()));

            draft.put("resetServerSettings", true);
            draft.put("serverPort", "invalid");
            service.save(draft);
            assertEquals(config.getInteger("serverPort"), Servers.Customserver.getServerPort());
            assertFalse(config.getBoolean("clientVersionOverwrite"));
            assertFalse((Boolean) service.load().get("resetServerSettings"));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void productionSettingsCannotAlterCustomServerValues() throws Exception {
        var directory = Files.createTempDirectory("client-settings-production");
        var file = directory.resolve("client.xcfgz");
        try {
            var config = new ConfigSystem(file);
            config.set("resolution", "800 x 600 x 32 @ 60Hz");
            config.set("serverAddress", "existing-host");
            var service = new ClientSettingsService(config, List.of(), false, Locale.ENGLISH);
            assertTrue(service.settings().stream().noneMatch(setting -> setting.section().equals("server")));
            var draft = new LinkedHashMap<>(service.load());
            draft.put("serverAddress", "unwanted-host");
            service.save(draft);
            assertEquals(config.getString("serverAddress"), "existing-host");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
