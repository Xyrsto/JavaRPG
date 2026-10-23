package dev.javarpg.server.repository;

import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.exception.ContentLoadException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads every quest file in a directory at startup (plan 5.9) and serves them in curriculum order. Reading
 * is done by QuestFileReader and validation by QuestFileMapper; this class only guards uniqueness and
 * keeps the index. Fails fast on any problem.
 */
public final class FileQuestRepository implements QuestRepository {

    private final List<Quest> ordered;
    private final Map<String, Quest> byId;

    public FileQuestRepository(Path directory, WorldDef world) {
        this(directory, new QuestFileReader(), new QuestFileMapper(world));
    }

    FileQuestRepository(Path directory, QuestFileReader reader, QuestFileMapper mapper) {
        List<Quest> loaded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path file : reader.listJsonFiles(directory)) {
            Quest quest = mapper.toQuest(file.getFileName().toString(), reader.read(file));
            if (!seen.add(quest.id())) {
                throw new ContentLoadException(file.getFileName() + ": duplicate quest id " + quest.id());
            }
            loaded.add(quest);
        }
        loaded.sort(Comparator.comparingInt((Quest q) -> Curriculum.globalIndex(q.id())));
        this.ordered = List.copyOf(loaded);
        Map<String, Quest> index = new LinkedHashMap<>();
        for (Quest quest : ordered) {
            index.put(quest.id(), quest);
        }
        this.byId = Map.copyOf(index);
    }

    @Override
    public Optional<Quest> findById(String questId) {
        return Optional.ofNullable(byId.get(questId));
    }

    @Override
    public List<Quest> findAllOrdered() {
        return ordered;
    }
}
