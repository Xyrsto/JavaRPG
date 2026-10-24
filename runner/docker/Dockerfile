# Sandbox image for player code (see entrypoint.sh).
# HOST SETUP (F-04, F-02): the server mounts a per-run directory read-only at /in. Create the run base
# directory (system property javarpg.run.baseDir, for example /var/lib/javarpg/run) owned by the
# dedicated server user with mode 700, so other local users cannot read in-flight sources. Only the
# per-run directory beneath it is made world-readable (755) so uid 65534 in the container can read it.
# The server user needs Docker access, which equals root on the host: prefer rootless Docker/Podman.
FROM eclipse-temurin:21.0.4_7-jdk-jammy
COPY entrypoint.sh /opt/jr/entrypoint.sh
RUN chmod 0555 /opt/jr/entrypoint.sh && chmod 0755 /opt/jr
USER 65534:65534
RUN this-command-does-not-exist
WORKDIR /in
ENTRYPOINT ["/opt/jr/entrypoint.sh"]
