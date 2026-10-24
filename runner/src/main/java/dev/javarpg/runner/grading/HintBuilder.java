package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.runner.guard.SourceScanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collects translated errors and the quest's common-mistake messages into at most {@value #MAX_HINTS} hints. */
public final class HintBuilder {

    static final int MAX_HINTS = 3;

    List<String> build(Quest quest, String compilerOutput, String stderr, String stdoutNorm, String playerSource) {
        Set<String> hints = new LinkedHashSet<>(ErrorTranslator.translate(compilerOutput, stderr));
        String source = SourceScanner.stripComments(playerSource);
        for (CommonMistake mistake : quest.commonMistakes()) {
            String text = switch (mistake.source()) {
                case COMPILER -> compilerOutput;
                case STDOUT -> stdoutNorm;
                case SOURCE -> source;
            };
            if (BoundedRegex.finds(mistake.pattern(), text)) {
                hints.add(mistake.message());
            }
        }
        List<String> capped = new ArrayList<>();
        for (String hint : hints) {
            if (capped.size() >= MAX_HINTS) {
                break;
            }
            capped.add(hint);
        }
        return capped;
    }
}
