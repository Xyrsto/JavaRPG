package dev.javarpg.server.service;

import dev.javarpg.core.contract.Evaluator;
import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestScope;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Runs the Evaluator only while the player holds a RunGate slot (F-14); the slot is always released. */
@Component
public class GatedEvaluator {

    private final Evaluator evaluator;
    private final RunGate runGate;

    public GatedEvaluator(Evaluator evaluator, RunGate runGate) {
        this.evaluator = evaluator;
        this.runGate = runGate;
    }

    public GradeReport evaluate(String playerId, Quest quest, TestScope scope, String source) {
        runGate.acquire(playerId);
        try {
            return evaluator.evaluate(quest, scope, source, UUID.randomUUID().toString());
        } finally {
            runGate.release(playerId);
        }
    }
}
