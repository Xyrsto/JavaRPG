package dev.javarpg.core.contract;

import java.util.List;
import java.util.Optional;

public interface QuestRepository {
    Optional<Quest> findById(String questId);

    /** All loaded quests in curriculum order (region order V,F,G,L,D,C,A, then number). */
    List<Quest> findAllOrdered();
}
