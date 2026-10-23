package dev.javarpg.server.service;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.core.rules.XpRules;
import org.springframework.stereotype.Component;

/** Stores a graded attempt through ProgressService, applying the "server fault is not a failed attempt" rule. */
@Component
public class AttemptRecorder {

    private final ProgressService progress;

    public AttemptRecorder(ProgressService progress) {
        this.progress = progress;
    }

    /**
     * Stores the attempt. A SANDBOX_ERROR is the server's fault, so it is not a failed attempt and nothing
     * is stored (the editor keeps the player's text on the client). Returns null in that case.
     */
    public RecordedAttempt record(String playerId, Quest quest, String source, GradeReport report,
                                  boolean complete) {
        if (report.status() == SandboxStatus.SANDBOX_ERROR) {
            return null;
        }
        return progress.recordAttempt(playerId, quest.id(), source, report.passed(), complete,
                XpRules.xpFor(quest.id()));
    }
}
