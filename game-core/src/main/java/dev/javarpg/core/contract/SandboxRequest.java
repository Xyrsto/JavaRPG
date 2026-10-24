package dev.javarpg.core.contract;

import java.util.Map;

/**
 * runId     : unique id (UUID string), also used in the container name "jr-" + runId.
 * sources   : file name -> content, e.g. {"Main.java": "..."} ; names are plain "X.java", no paths.
 * mainClass : class whose main(String[]) is run, e.g. "Main" or "JrHarness".
 */
public record SandboxRequest(String runId, Profile profile, Map<String, String> sources, String mainClass) { }
