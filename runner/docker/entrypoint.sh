#!/bin/sh
# Env: JR_MAIN (main class), JR_RUN_TIMEOUT (seconds)
# Exit 100 = compile error (message on stdout). A run-phase exit 100 is remapped to 101 so
# that player code cannot pass itself off as a compile error.
# Exit 102 = a javac step hit its timeout (reported as TIMEOUT, not as a compile error). Run-phase 102 -> 101.
#
# F-10: the player's files are compiled ALONE first, and only that javac output is returned.
# The generated harness (JrHarness.java) is compiled second against the player's classes; if it fails
# a fixed message is printed and javac's output is discarded, because it would quote hidden test calls.
# Keep the two messages below identical to HarnessProtocol.java.
HARNESS_MSG="Your method does not match the signature the tests call."
TIMEOUT_MSG="Your code took too long to compile. Try making it simpler."

mkdir -p /work/out
cd /in || exit 1

players=""
for f in *.java; do
  if [ "$f" != "JrHarness.java" ]; then
    players="$players $f"
  fi
done

# F-05: javac gets its own wall-clock limit inside the container (124 = timeout, 137 = killed).
timeout -k 1 10 javac -J-Xmx160m -J-XX:+UseSerialGC -J-XX:TieredStopAtLevel=1 -encoding UTF-8 -proc:none -Xlint:none -d /work/out $players > /work/compile.txt 2>&1
rc=$?
if [ $rc -eq 124 ] || [ $rc -eq 137 ]; then echo "$TIMEOUT_MSG"; exit 102; fi
if [ $rc -ne 0 ]; then cat /work/compile.txt; exit 100; fi

if [ -f JrHarness.java ]; then
  timeout -k 1 10 javac -J-Xmx160m -J-XX:+UseSerialGC -J-XX:TieredStopAtLevel=1 -encoding UTF-8 -proc:none -Xlint:none -cp /work/out -d /work/out JrHarness.java > /work/harness.txt 2>&1
  rc=$?
  if [ $rc -eq 124 ] || [ $rc -eq 137 ]; then echo "$TIMEOUT_MSG"; exit 102; fi
  if [ $rc -ne 0 ]; then echo "$HARNESS_MSG"; exit 100; fi
fi

timeout -k 1 "$JR_RUN_TIMEOUT" java -Xmx96m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:-UsePerfData -Dfile.encoding=UTF-8 -cp /work/out "$JR_MAIN"
rc=$?
if [ $rc -eq 100 ] || [ $rc -eq 102 ]; then rc=101; fi
exit $rc
