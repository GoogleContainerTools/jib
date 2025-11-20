#!/bin/bash

# Fail on any error.
set -o errexit
# Display commands to stderr.
set -o xtrace

# Append to JAVA_TOOL_OPTIONS to suppress warnings from kokoro container os.
if [ "${KOKORO_JOB_CLUSTER}" = "GCP_UBUNTU_DOCKER" ]; then
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Xlog:os+container=error"
fi

echo "java version:"
java -version

cd github/jib
./gradlew :jib-maven-plugin:prepareRelease
