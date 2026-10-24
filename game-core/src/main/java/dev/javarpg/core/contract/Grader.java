package dev.javarpg.core.contract;

/** Pure and deterministic: no I/O, no clock. Turns one SandboxResult into a GradeReport. */
public interface Grader {
    GradeReport grade(Quest quest, TestScope scope, SandboxResult result, String playerSource);
}
