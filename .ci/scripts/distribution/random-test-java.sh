#!/bin/bash -eux

# specialized script to run tests with random elements without flaky test detection
# getconf is a POSIX way to get the number of processors available which works on both Linux and macOS
LIMITS_CPU=${LIMITS_CPU:-$(getconf _NPROCESSORS_ONLN)}
MAVEN_PARALLELISM=${MAVEN_PARALLELISM:-$LIMITS_CPU}
SUREFIRE_FORK_COUNT=${SUREFIRE_FORK_COUNT:-}
JUNIT_THREAD_COUNT=${JUNIT_THREAD_COUNT:-}
MAVEN_PROPERTIES=(
  -DskipITs
  -DskipChecks
  -DtestMavenId=4
  -Dsurefire.rerunFailingTestsCount=0
)
tempFile=$(mktemp)

if [ -n "$SUREFIRE_FORK_COUNT" ]; then
  MAVEN_PROPERTIES+=("-DforkCount=$SUREFIRE_FORK_COUNT")
  # this is a rough heuristic to avoid OOM errors due to high parallelism
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -XX:MaxRAMFraction=${SUREFIRE_FORK_COUNT}"
fi

if [ -n "$JUNIT_THREAD_COUNT" ]; then
  MAVEN_PROPERTIES+=("-DjunitThreadCount=$JUNIT_THREAD_COUNT")
fi

mvn -o -B --fail-never "-T${MAVEN_PARALLELISM}" -s "${MAVEN_SETTINGS_XML}" test -P parallel-tests,include-random-tests "${MAVEN_PROPERTIES[@]}" | tee "${tempFile}"
