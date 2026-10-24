package dev.javarpg.server.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-006, NFR-001: the committed description is exactly what the running server serves. After an intended API
 * change, regenerate it with: mvn -pl server test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSnapshotTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theCommittedDescriptionEqualsTheServedOne() throws Exception {
        String served = OpenApiFixture.yaml(mvc);
        Path file = OpenApiFixture.snapshot();
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, served, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(file), file + " is missing. Generate it with -Dopenapi.update=true");
        String committed = OpenApiFixture.normalize(Files.readString(file, StandardCharsets.UTF_8));
        if (!committed.equals(served)) {
            String[] a = committed.split("\n", -1);
            String[] b = served.split("\n", -1);
            int i = 0;
            while (i < Math.min(a.length, b.length) && a[i].equals(b[i])) {
                i++;
            }
            throw new AssertionError("docs/api/openapi.yaml differs from the served description at line " + (i + 1)
                    + ".\n  committed: " + (i < a.length ? a[i] : "<end>")
                    + "\n  served:    " + (i < b.length ? b[i] : "<end>")
                    + "\nIf the API change is intended, regenerate with -Dopenapi.update=true and commit the file.");
        }
        assertEquals(committed, served);
    }
}
