package illarion.download.launcher;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.testng.Assert.assertEquals;

public class JavaLauncherTest {
    @DataProvider public Object[][] versions() {
        return new Object[][] {
            {"java version \"1.7.0_80\"", false},
            {"java version \"1.8.0_504\"", true},
            {"openjdk version \"1.8.0_504\"", true},
            {"openjdk version \"17.0.16\" 2025-07-15", true},
            {"openjdk version \"25\" 2025-09-16", true},
            {"java version \"25.0.4.1\" 2026-07-21 LTS", true},
            {"OpenJDK Runtime Environment", false},
            {"java version \"1.6.0_45\"", false},
            {"garbage", false}
        };
    }
    @Test(dataProvider="versions") public void recognizesSupportedRuntime(String line, boolean expected) {
        assertEquals(JavaLauncher.isSupportedJavaVersion(line), expected);
    }

    @Test public void runtimeFlagsFollowTheChildJvmVersion() {
        assertEquals(JavaLauncher.runtimeOptions(8, false), Collections.emptyList());
        assertEquals(JavaLauncher.runtimeOptions(8, true), Arrays.asList("-XX:+AggressiveOpts"));
        assertEquals(JavaLauncher.runtimeOptions(11, true), Collections.emptyList());
        assertEquals(JavaLauncher.runtimeOptions(16, false), Collections.emptyList());
        assertEquals(JavaLauncher.runtimeOptions(17, false), Arrays.asList("--enable-native-access=ALL-UNNAMED"));
        assertEquals(JavaLauncher.runtimeOptions(25, true), Arrays.asList("--enable-native-access=ALL-UNNAMED"));
        assertEquals(JavaLauncher.parseJavaVersion("java version \"1.8.0_504\""), 8);
        assertEquals(JavaLauncher.parseJavaVersion("openjdk version \"25\""), 25);
    }
}
