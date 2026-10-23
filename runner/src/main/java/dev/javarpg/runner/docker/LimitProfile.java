package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.Profile;

/** Container limits per profile (plan 6.2). Only BASIC is implemented in this feature. */
public record LimitProfile(Profile profile, boolean implemented, int pids, int memoryMb, double cpus,
                           int runTimeoutSeconds, String image, boolean network) {

    public static LimitProfile forProfile(Profile profile) {
        return switch (profile) {
            case BASIC -> new LimitProfile(Profile.BASIC, true, 64, 384, 1.0, 3, "javarpg-runner:1", false);
            case TESTING -> new LimitProfile(Profile.TESTING, false, 128, 512, 1.0, 10, "javarpg-runner-testing:1", false);
            case FILES -> new LimitProfile(Profile.FILES, false, 64, 384, 1.0, 5, "javarpg-runner:1", false);
            case THREADS -> new LimitProfile(Profile.THREADS, false, 256, 512, 2.0, 10, "javarpg-runner:1", false);
            case BUILD -> new LimitProfile(Profile.BUILD, false, 256, 768, 2.0, 60, "javarpg-runner-build:1", false);
            case SERVICE -> new LimitProfile(Profile.SERVICE, false, 256, 768, 2.0, 20, "javarpg-runner-service:1", false);
        };
    }
}
