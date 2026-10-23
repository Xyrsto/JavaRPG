package dev.javarpg.runner.grading;

import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.TestScope;
import java.util.ArrayList;
import java.util.List;

/** Which of a quest's tests take part in a run, and in which order. */
final class TestSelection {

    private TestSelection() {
    }

    /** Tests in scope, visible first, then hidden when the scope is ALL. */
    static List<TestCase> inScope(Quest quest, TestScope scope) {
        List<TestCase> list = new ArrayList<>(quest.visibleTests());
        if (scope == TestScope.ALL) {
            list.addAll(quest.hiddenTests());
        }
        return List.copyOf(list);
    }

    /** The in-scope tests that become harness calls, in harness index order. */
    static List<TestCase> callTestsInScope(Quest quest, TestScope scope) {
        List<TestCase> list = new ArrayList<>();
        for (TestCase t : inScope(quest, scope)) {
            if (t.call() != null) {
                list.add(t);
            }
        }
        return List.copyOf(list);
    }

    /** The in-scope tests wrapped with their hidden flag and hidden-test number. */
    static List<GradedTest> plan(Quest quest, TestScope scope) {
        List<GradedTest> list = new ArrayList<>();
        for (TestCase t : quest.visibleTests()) {
            list.add(new GradedTest(t, false, 0));
        }
        if (scope == TestScope.ALL) {
            int n = 0;
            for (TestCase t : quest.hiddenTests()) {
                n++;
                list.add(new GradedTest(t, true, n));
            }
        }
        return list;
    }
}
