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

import static org.testng.Assert.assertTrue;

/** Opt-in integration test: requires a desktop OpenGL driver, opens no visible window. */
public class NativeRuntimeTest {
    @Test(groups = "nativeRuntime", timeOut = 30000)
    public void startsLibGdxOnTheSelectedJvm() {
        ByteBuffer memory = MemoryUtil.memAlloc(16);
        try {
            memory.putInt(0, 42);
            assertTrue(memory.getInt(0) == 42);
        } finally {
            MemoryUtil.memFree(memory);
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 64);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL20, 3, 2);
        AtomicBoolean rendered = new AtomicBoolean();
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void render() {
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
