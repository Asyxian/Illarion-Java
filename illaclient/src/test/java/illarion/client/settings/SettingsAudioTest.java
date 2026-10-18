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

import illarion.client.util.AudioPlayer;
import illarion.common.config.Config;
import illarion.common.config.ConfigChangedEvent;
import org.illarion.engine.sound.Music;
import org.illarion.engine.sound.Sounds;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

public class SettingsAudioTest {
    @Test
    public void enablingMusicWithoutPreviewStartsTheLastTrackOnlyWhenNeeded() throws Exception {
        var constructor = AudioPlayer.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AudioPlayer player = constructor.newInstance();
        Sounds sounds = mock(Sounds.class);
        Music music = mock(Music.class);
        Config config = mock(Config.class);
        var field = AudioPlayer.class.getDeclaredField("sounds");
        field.setAccessible(true);
        field.set(player, sounds);
        player.setLastMusic(music);
        when(config.getBoolean("musicOn")).thenReturn(true);
        when(config.getFloat("musicVolume")).thenReturn(37.f);

        player.onEvent("musicOn", new ConfigChangedEvent(config, "musicOn"));
        verify(sounds).setMusicVolume(0.37f);
        verify(sounds).playMusic(music, 0, 0);

        when(sounds.isMusicPlaying(music)).thenReturn(true);
        player.onEvent("musicOn", new ConfigChangedEvent(config, "musicOn"));
        verify(sounds, times(1)).playMusic(music, 0, 0);

        when(config.getBoolean("musicOn")).thenReturn(false);
        player.onEvent("musicOn", new ConfigChangedEvent(config, "musicOn"));
        verify(sounds).setMusicVolume(0.f);
        verify(sounds, times(1)).playMusic(music, 0, 0);
    }
}
