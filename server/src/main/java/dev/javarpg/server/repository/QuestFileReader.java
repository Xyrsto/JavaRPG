package dev.javarpg.server.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.server.dto.QuestFileDto;
import dev.javarpg.server.exception.ContentLoadException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** File system access for quest content: lists the *.json files of a folder and parses one into its DTO. */
final class QuestFileReader {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /** Quest files of the directory in file-name order. */
    List<Path> listJsonFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new ContentLoadException("Quest folder not found: " + directory.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new ContentLoadException("Cannot list the quest folder " + directory.toAbsolutePath(), e);
        }
    }

    QuestFileDto read(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), QuestFileDto.class);
        } catch (IOException | RuntimeException e) {
            throw new ContentLoadException(file.getFileName() + ": invalid JSON or unknown field: "
                    + e.getMessage(), e);
        }
    }
}
