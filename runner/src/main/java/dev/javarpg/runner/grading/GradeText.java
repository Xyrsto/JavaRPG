package dev.javarpg.runner.grading;

/** Small text helpers shared by the grading classes. */
final class GradeText {

    /** Longest stdout/stderr/actual value copied into a report. */
    static final int CUT = 4000;

    private GradeText() {
    }

    static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > CUT ? s.substring(0, CUT) : s;
    }

    /** Null becomes the empty string. */
    static String nz(String s) {
        return s == null ? "" : s;
    }
}
