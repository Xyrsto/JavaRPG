package dev.javarpg.runner.grading;

/** One parsed harness result line: either a returned value or a thrown exception's message. */
record HarnessResult(boolean exception, String value) {
}
