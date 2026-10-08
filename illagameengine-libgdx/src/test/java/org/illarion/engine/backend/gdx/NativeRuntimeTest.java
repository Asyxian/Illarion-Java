/*
 * This file is part of the Illarion project.
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
package org.illarion.engine.backend.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import org.lwjgl.system.MemoryUtil;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Opt-in integration test: requires a desktop OpenGL driver, opens no visible window. */

public class NativeRuntimeTest {
    @Test(groups = "nativeRuntime", timeOut = 30_000)
    public void startsLibGdxOnTheSelectedJvm() {
        int testValue = 42;
        ByteBuffer memory = MemoryUtil.memAlloc(Integer.BYTES);

        try {
            memory.putInt(0, testValue);
            assertEquals(memory.getInt(0), testValue);
        } finally {
            MemoryUtil.memFree(memory);
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 64);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 3, 2);
        AtomicBoolean rendered = new AtomicBoolean();

        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void render() {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                System.out.println("Java " + System.getProperty("java.version") + "; OpenGL " +
                        Gdx.gl.glGetString(GL20.GL_VERSION));
                rendered.set(true);
                Gdx.app.exit();
            }
        }, config);
        assertTrue(rendered.get(), "libGDX never rendered a frame");
    }
}
