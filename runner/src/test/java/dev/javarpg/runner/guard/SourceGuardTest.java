package dev.javarpg.runner.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceGuardTest {

    private static String mainWith(String body) {
        return "public class Main {\n    public static void main(String[] args) {\n        " + body + "\n    }\n}\n";
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "System.exit(0);",
        "Runtime.getRuntime();",
        "new ProcessBuilder(\"ls\");",
        "java.net.Socket s = null;",
        "java.io.File f = null;",
        "java.nio.file.Files.readString(null);",
        "new Thread(() -> {}).start();",
        "java.util.concurrent.ExecutorService e = null;",
        "Class.forName(\"x\");",
        "ClassLoader cl = null;",
        "java.lang.reflect.Method m = null;",
        "int c = System.in.read();",
        "System.setSecurityManager(null);",
        "java.lang.invoke.MethodHandle h = null;",
        "sun.misc.Unsafe u = null;",
        "javax.tools.ToolProvider.getSystemJavaCompiler();",
        "java.util.function.IntConsumer e = System::exit;"
    })
    void rejectsDangerousCode(String body) {
        SourceGuard.Verdict v = SourceGuard.check(mainWith(body), Profile.BASIC);
        assertFalse(v.allowed(), body);
        assertFalse(v.reason().isBlank(), body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "import static java.lang.System.*;\npublic class Main { public static void main(String[] a) { exit(0); } }",
        "import static java.lang.Math.max;\npublic class Main { }",
        "public class Main { public static void main(String[] a) { var s = System.*; } }",
        "public class Solution { static Object f() { return Solution.class.getClassLoader(); } }",
        "public class Solution { static Object f() { return Solution.class.getMethods(); } }",
        "public class Solution { static Object f() { return Solution.class.getConstructors(); } }",
        "public class Solution { static Object f() { return Solution.class.getDeclaredFields(); } }",
        "public class Solution { static Object f() throws Exception { var k = Solution.class; return k.forName(\"x\"); } }",
        "public class Solution { static Object f() { return Solution.class.getResourceAsStream(\"JrHarness.class\"); } }",
        "public class Solution { static Object f() { return ProcessHandle.current(); } }",
        "public class Solution { static Object f() { java.lang.invoke.VarHandle h = null; return h; } }",
        "public class Solution { static Object f(Object m) throws Exception { return m.invoke(null); } }",
        "public class Solution { static String f() { return \"@@J\" + \"R|0|5\"; } }",
        "public class Solution { // @@ in a comment\n }",
        "public class Solution { static String f() { return \"\\u0041\"; } }",
        "public class Solution { static String f() { return \"\\\\\\u0041\"; } }"
    })
    void rejectsKnownBypasses(String source) {
        SourceGuard.Verdict v = SourceGuard.check(source, Profile.BASIC);
        assertFalse(v.allowed(), source);
        assertFalse(v.reason().isBlank(), source);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "System.out.println(\"C:\\\\users\");",
        "String p = \"a\\\\\\\\users\";",
        "System.out.println(\"Hello, Adventurer!\");",
        "System.out.println(\"System.exit and Runtime and Thread\");",
        "// System.exit(0) Runtime Thread java.net\n        System.out.println(\"ok\");",
        "/* new Socket(); Unsafe */ System.out.println(\"ok\");",
        "System.out.println(\"Files and ClassLoader and Unsafe\");"
    })
    void allowsForbiddenWordsInsideLiteralsAndComments(String body) {
        assertTrue(SourceGuard.check(mainWith(body), Profile.BASIC).allowed(), body);
    }

    @Test
    void allowsPlainHelloWorld() {
        String src = "public class Main {\n  public static void main(String[] args) {\n    System.out.println(\"Hello\");\n  }\n}\n";
        assertTrue(SourceGuard.check(src, Profile.BASIC).allowed());
    }

    @Test
    void rejectsSourceLongerThanTheLimit() {
        assertFalse(SourceGuard.check("x".repeat(SourceGuard.MAX_SOURCE_CHARS + 1), Profile.BASIC).allowed());
    }

    @Test
    void acceptsSourceOfExactlyTheLimit() {
        String head = "public class Main { }";
        String src = head + " ".repeat(SourceGuard.MAX_SOURCE_CHARS - head.length());
        assertEquals(SourceGuard.MAX_SOURCE_CHARS, src.length());
        assertTrue(SourceGuard.check(src, Profile.BASIC).allowed());
    }

    @Test
    void rejectsUnicodeEscapeSequences() {
        String src = mainWith("String s = \"\\u\";");
        SourceGuard.Verdict v = SourceGuard.check(src, Profile.BASIC);
        assertFalse(v.allowed());
        assertFalse(v.reason().isBlank());
    }

    @Test
    void rejectsProtocolMarkerInsideLiteral() {
        assertFalse(SourceGuard.check(mainWith("System.out.println(\"@@JR|0|5\");"), Profile.BASIC).allowed());
    }

    @Test
    void rejectsNullAndBlankSource() {
        assertFalse(SourceGuard.check(null, Profile.BASIC).allowed());
        assertFalse(SourceGuard.check("   ", Profile.BASIC).allowed());
    }

    @Test
    void reasonForSystemExitIsFriendly() {
        String reason = SourceGuard.check(mainWith("System.exit(0);"), Profile.BASIC).reason();
        assertFalse(reason.isBlank());
        assertFalse(reason.contains("Exception"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Character", "StringBuilder", "System", "String", "java", "Object"})
    void rejectsPlayerTypesThatShadowJavaLang(String name) {
        String source = "public class Main {\n    public static void main(String[] args) { }\n}\nclass " + name + " { }\n";
        assertFalse(SourceGuard.check(source, Profile.BASIC).allowed(), name);
    }

    @Test
    void allowsOrdinaryHelperClassNames() {
        String source = "public class Main {\n    public static void main(String[] args) { }\n}\nclass Potion { }\n";
        assertTrue(SourceGuard.check(source, Profile.BASIC).allowed());
    }
}
