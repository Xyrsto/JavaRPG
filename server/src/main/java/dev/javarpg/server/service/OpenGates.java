package dev.javarpg.server.service;

import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.WorldEffect;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Gate state is derived, never stored: a gate is open once the quest with an OPEN_GATE effect is completed. */
@Component
public class OpenGates {

    private final QuestRepository repository;

    public OpenGates(QuestRepository repository) {
        this.repository = repository;
    }

    /** Gate ids opened by completed quests whose worldEffect is OPEN_GATE, sorted. */
    public Set<String> forCompleted(Set<String> completed) {
        Set<String> gates = new TreeSet<>();
        for (Quest quest : repository.findAllOrdered()) {
            WorldEffect effect = quest.worldEffect();
            if (effect != null && "OPEN_GATE".equals(effect.type()) && completed.contains(quest.id())) {
                gates.add(effect.target());
            }
        }
        return gates;
    }
}
