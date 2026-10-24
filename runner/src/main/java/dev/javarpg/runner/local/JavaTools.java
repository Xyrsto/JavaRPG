package dev.javarpg.runner.local;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command lines for the JDK tools that sit next to the running JVM. */
final class JavaTools {

    private JavaTools() {
    }

    static String tool(String name) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String exe = os.contains("win") ? name + ".exe" : name;
        return Paths.get(System.getProperty("java.home"), "bin", exe).toString();
    }

    /** classpath may be null. */
    static List<String> javac(Path classes, Path classpath, List<String> files) {
        List<String> command = new ArrayList<>();
        command.add(tool("javac"));
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-proc:none");
        if (classpath != null) {
            command.add("-cp");
            command.add(classpath.toString());
        }
        command.add("-d");
        command.add(classes.toString());
        command.addAll(files);
        return command;
    }

    static List<String> java(Path classes, String mainClass) {
        return List.of(tool("java"), "-Xmx96m", "-Xss512k",
                "-XX:+UseSerialGC", "-XX:-UsePerfData", "-Dfile.encoding=UTF-8",
                "-cp", classes.toString(), mainClass);
    }
}
