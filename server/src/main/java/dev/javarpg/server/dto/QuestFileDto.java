package dev.javarpg.server.dto;

import java.util.List;

/** Mirrors one quest JSON file (plan 5.9). Code fields are arrays of lines. */
public record QuestFileDto(
        String id,
        String region,
        String title,
        String concept,
        String story,
        String goal,
        String kind,
        List<String> starterCode,
        String profile,
        List<TestFileDto> visibleTests,
        List<TestFileDto> hiddenTests,
        List<String> hints,
        List<String> referenceSolution,
        String explanation,
        List<MistakeFileDto> commonMistakes,
        WorldEffectFileDto worldEffect) {

    /** call is null for STDOUT tests. */
    public record TestFileDto(String name, String call, String expected, String match) { }

    public record MistakeFileDto(String source, String pattern, String message) { }

    public record WorldEffectFileDto(String type, String target, String message) { }
}
