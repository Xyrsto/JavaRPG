package dev.javarpg.server.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the javarpg.* settings in application.properties. */
@ConfigurationProperties(prefix = "javarpg")
public class JavaRpgProperties {

    private String contentDir = "content/quests";
    private String worldFile = "content/world/village.json";
    private String dataDir = "./data";
    private final Sandbox sandbox = new Sandbox();
    private final Cors cors = new Cors();
    private final Http http = new Http();
    private final Rate rate = new Rate();
    private final Players players = new Players();

    public String getContentDir() {
        return contentDir;
    }

    public void setContentDir(String contentDir) {
        this.contentDir = contentDir;
    }

    public String getWorldFile() {
        return worldFile;
    }

    public void setWorldFile(String worldFile) {
        this.worldFile = worldFile;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Sandbox getSandbox() {
        return sandbox;
    }

    public Cors getCors() {
        return cors;
    }

    public Http getHttp() {
        return http;
    }

    public Rate getRate() {
        return rate;
    }

    public Players getPlayers() {
        return players;
    }

    /** javarpg.sandbox.* */
    public static class Sandbox {
        private String mode = "docker";
        private String image = "javarpg-runner:1";
        private int maxConcurrent = 2;
        /** Must be true as well before unsafe-local is allowed to start (F-12a). */
        private boolean allowUnsafe = false;
        /** How long a run waits for a free sandbox slot. 0 rejects immediately with SERVER_BUSY (F-14). */
        private long acquireWaitMillis = 0L;

        public boolean isAllowUnsafe() {
            return allowUnsafe;
        }

        public void setAllowUnsafe(boolean allowUnsafe) {
            this.allowUnsafe = allowUnsafe;
        }

        public long getAcquireWaitMillis() {
            return acquireWaitMillis;
        }

        public void setAcquireWaitMillis(long acquireWaitMillis) {
            this.acquireWaitMillis = acquireWaitMillis;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }

    /** javarpg.cors.* */
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** javarpg.http.* */
    public static class Http {
        private long maxBodyBytes = 65536L;

        public long getMaxBodyBytes() {
            return maxBodyBytes;
        }

        public void setMaxBodyBytes(long maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
        }
    }

    /** javarpg.rate.* Per-client-IP limits in requests per minute; 0 or less disables a limit. */
    public static class Rate {
        private int playersPerMinute = 5;
        private int runPerMinute = 10;
        private int submitPerMinute = 10;
        /** Only enable behind a trusted reverse proxy: X-Forwarded-For is client controlled otherwise. */
        private boolean trustForwarded = false;
        private int maxTrackedClients = 10000;

        public int getPlayersPerMinute() {
            return playersPerMinute;
        }

        public void setPlayersPerMinute(int playersPerMinute) {
            this.playersPerMinute = playersPerMinute;
        }

        public int getRunPerMinute() {
            return runPerMinute;
        }

        public void setRunPerMinute(int runPerMinute) {
            this.runPerMinute = runPerMinute;
        }

        public int getSubmitPerMinute() {
            return submitPerMinute;
        }

        public void setSubmitPerMinute(int submitPerMinute) {
            this.submitPerMinute = submitPerMinute;
        }

        public boolean isTrustForwarded() {
            return trustForwarded;
        }

        public void setTrustForwarded(boolean trustForwarded) {
            this.trustForwarded = trustForwarded;
        }

        public int getMaxTrackedClients() {
            return maxTrackedClients;
        }

        public void setMaxTrackedClients(int maxTrackedClients) {
            this.maxTrackedClients = maxTrackedClients;
        }
    }

    /** javarpg.players.* */
    public static class Players {
        private int max = 10000;
        private int idleDays = 30;

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public int getIdleDays() {
            return idleDays;
        }

        public void setIdleDays(int idleDays) {
            this.idleDays = idleDays;
        }
    }
}
