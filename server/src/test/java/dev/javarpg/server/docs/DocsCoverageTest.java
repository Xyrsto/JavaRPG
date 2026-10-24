package dev.javarpg.server.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-015, FR-016: the written pages in docs/ mention every operation of the description, their relative links
 * resolve, and the public repository never carries local paths or e-mail addresses in them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocsCoverageTest {

    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern PRIVATE_DATA = Pattern.compile(
            "\\b[A-Za-z]:\\\\|/Users/|/home/|\\bC:/|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Autowired
    private MockMvc mvc;

    private static List<Path> files(String... extensions) throws Exception {
        try (Stream<Path> all = Files.walk(OpenApiFixture.docsDir())) {
            return all.filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString();
                for (String ext : extensions) {
                    if (name.endsWith(ext)) {
                        return true;
                    }
                }
                return false;
            }).toList();
        }
    }

    private static String read(Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void everyOperationOfTheDescriptionIsWrittenUpInTheReferencePages() throws Exception {
        JsonNode spec = OpenApiFixture.spec(mvc);
        StringBuilder pages = new StringBuilder();
        for (Path page : files(".md")) {
            if (page.getParent().getFileName().toString().equals("api")) {
                pages.append(read(page)).append('\n');
            }
        }
        assertFalse(pages.isEmpty(), "docs/api/*.md reference pages are missing");
        List<String> missing = new ArrayList<>();
        Iterator<String> paths = spec.get("paths").fieldNames();
        while (paths.hasNext()) {
            String path = paths.next();
            Iterator<String> methods = spec.get("paths").get(path).fieldNames();
            while (methods.hasNext()) {
                String line = methods.next().toUpperCase() + " " + path;
                if (!pages.toString().contains(line)) {
                    missing.add(line);
                }
            }
        }
        assertTrue(missing.isEmpty(), "not written up in docs/api: " + missing);
    }

    @Test
    void theRequiredPagesExistAndRelativeLinksResolve() throws Exception {
        for (String page : new String[] {"index.md", "quick-start.md", "concepts.md", "api/index.md", "api/players.md",
                "api/world.md", "api/quests.md", "api/health.md", "api/errors.md", "_config.yml", "api/openapi.yaml"}) {
            assertTrue(Files.exists(OpenApiFixture.docsDir().resolve(page)), "docs/" + page + " is missing");
        }
        List<String> broken = new ArrayList<>();
        for (Path page : files(".md")) {
            Matcher matcher = LINK.matcher(read(page));
            while (matcher.find()) {
                String target = matcher.group(1);
                if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto:")) {
                    continue;
                }
                String file = target.split("[#?]")[0];
                if (!file.isEmpty() && !Files.exists(page.getParent().resolve(file).normalize())) {
                    broken.add(OpenApiFixture.docsDir().relativize(page) + " -> " + target);
                }
            }
        }
        assertTrue(broken.isEmpty(), "broken links: " + broken);
    }

    @Test
    void theDocumentationCarriesNoLocalPathsOrEmailAddresses() throws Exception {
        List<String> found = new ArrayList<>();
        for (Path file : files(".md", ".yml", ".yaml")) {
            Matcher matcher = PRIVATE_DATA.matcher(read(file));
            if (matcher.find()) {
                found.add(OpenApiFixture.docsDir().relativize(file) + ": " + matcher.group());
            }
        }
        assertTrue(found.isEmpty(), "private-looking data in the public docs: " + found);
    }
}
