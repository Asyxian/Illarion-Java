/*
 * This file is part of the Illarion project.
 *
 * Copyright © 2015 - Illarion e.V.
 *
 * Illarion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Illarion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package illarion.download.launcher;

import illarion.common.util.DirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The use of this class is to start a independent JVM that runs the chosen application. This class requires calls
 * that are system dependent.
 *
 * @author Martin Karing
 */
public final class JavaLauncher {
    private static final int MINIMUM_JAVA_VERSION = 25;

    /**
     * This instance of the logger takes care for the logging output of this class.
     */
    @Nonnull
    private static final Logger log = LoggerFactory.getLogger(JavaLauncher.class);

    /**
     * This text contains the error data in case the launch failed.
     */
    @Nullable
    private String errorData;

    private final boolean snapshot;

    /**
     * Construct a new launcher and set the classpath and the class to launch.
     */
    public JavaLauncher(boolean snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Calling this function causes the selected application to launch.
     *
     * @return {@code true} in case launching the application was successful
     */
    public boolean launch(@Nonnull Collection<File> classpath, @Nonnull String startupClass) {
        String classPathString = buildClassPathString(classpath);

        Iterable<Path> executablePaths;
        executablePaths = OSDetection.isMacOSX() ? new MacOsXJavaExecutableIterable() : new JavaExecutableIterable();

        for (Path executable : executablePaths) {
            int javaVersion = getJavaVersion(executable);

            if (isSupportedJavaVersion(javaVersion)) {
                List<String> callList = new ArrayList<>();
                callList.add(escapePath(executable.toString()));
                callList.addAll(runtimeOptions());
                callList.add("-classpath");
                callList.add(classPathString);
                if (snapshot) {
                    callList.add("-Dillarion.server=devserver");
                }
                callList.add(startupClass);
                printCallList(callList);
                if (launchCallList(callList)) {
                    return true;
                } else {
                    log.error("Error while launching application: {}", errorData);
                }
            }
        }

        if (errorData == null) {
            errorData = "Java " + MINIMUM_JAVA_VERSION + " or newer is required to launch this application.";
        }

        return false;
    }

    /**
     * This function is used to check if the java executable has the proper version.
     *
     * @param executable the path to the executable
     * @return the detected Java feature version, or zero if detection failed
     */
    private static int getJavaVersion(@Nonnull Path executable) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(executable.toString(), "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {

                return reader.lines().mapToInt(JavaLauncher::parseJavaVersion)
                        .filter(version -> version > 0).findFirst().orElse(0);

            } finally {
                process.destroy();
            }
        } catch (IOException e) {
            log.error("Launching {} failed.", executable);
        }

        return 0;
    }

    // Both legacy 1.8.0_... and modern OpenJDK version strings are supported.
    static int parseJavaVersion(String line) {
        Pattern versionPattern = Pattern.compile("^(?:java|openjdk) version \"(\\d+)(?:\\.(\\d+))?[^\"]*\"");
        Matcher matcher = versionPattern.matcher(line.trim());

        if (!matcher.find()) {
            return 0;
        }

        try {
            int feature = Integer.parseInt(matcher.group(1));

            if (feature == 1) {
                return matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            }

            return feature;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean isSupportedJavaVersion(int javaVersion) {
        return javaVersion >= MINIMUM_JAVA_VERSION;
    }

    static List<String> runtimeOptions() {
        return List.of("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=deny");
    }

    /**
     * Build the class path string that contain a list of files pointing to each file needed to include to this
     * application.
     *
     * @return the string that represents the class path
     */
    @Nonnull
    private static String buildClassPathString(@Nonnull Collection<File> classpath) {
        if (classpath.isEmpty()) {
            return "";
        }

        String cp = classpath.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        return (cp == null) ? "" : escapePath(cp);
    }

    /**
     * This small utility function takes care for escaping a path. This operation is platform dependent so the result
     * will differ on different platforms.
     *
     * @param orgPath the original plain path
     * @return the escaped path
     */
    @Nonnull
    private static String escapePath(@Nonnull String orgPath) {
        if (OSDetection.isWindows()) {
            if (orgPath.contains(" ")) {
                return '"' + orgPath + '"';
            }
            return orgPath;
        }
        // ProcessBuilder does not invoke a shell: Escaping is not needed on Mac/Linux
        return orgPath;
    }

    /**
     * Print the call list to the logger.
     *
     * @param callList the call list to print
     */
    private static void printCallList(@Nonnull Collection<String> callList) {
        if (log.isDebugEnabled()) {
            String prefix = "Calling: " + System.getProperty("line.separator");
            log.debug(callList.stream().collect(Collectors.joining(" ", prefix, "")));
        }
    }

    /**
     * Launch the specified call list.
     *
     * @param callList launch the call list
     * @return {@code true} in case the launch was successful
     */
    private boolean launchCallList(@Nonnull List<String> callList) {
        try {
            ProcessBuilder pBuilder = new ProcessBuilder(callList);

            Path workingDirectory = DirectoryManager.getInstance().getWorkingDirectory();
            pBuilder.directory(workingDirectory.toFile());
            pBuilder.redirectErrorStream(true);
            Process proc = pBuilder.start();

            //noinspection EmptyTryBlock
            try (OutputStream ignored = proc.getOutputStream()) {
            }

            StringBuilder outputBuffer = new StringBuilder();
            try (final BufferedReader outputReader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()))) {

                TimerTask timeoutTask = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            outputReader.close();
                        } catch (IOException ignored) {
                            // nothing to do
                        }
                    }
                };
                new Timer("Startup Timeout Timer", true).schedule(timeoutTask, 10000);

                while (true) {
                    String line = outputReader.readLine();
                    if (line == null) {
                        errorData = outputBuffer.toString().trim();
                        return false;
                    }
                    if (line.endsWith("Startup done.")) {
                        timeoutTask.cancel();
                        outputReader.close();
                        return true;
                    }
                    outputBuffer.append(line);
                    outputBuffer.append('\n');
                }
            }
        } catch (@Nonnull Exception e) {
            StringWriter sWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(sWriter);
            e.printStackTrace(writer);
            writer.flush();
            errorData = sWriter.toString();
            return false;
        }
    }

    /**
     * Get the information about the launch error.
     *
     * @return the string containing the data about the crash
     */
    @Nullable
    public String getErrorData() {
        return errorData;
    }
}
