#!/bin/bash

# Fail on any error.
set -o errexit
# Display commands to stderr.
set -o xtrace

echo "java version:"
java -version

cd github/jib
./gradlew :jib-core:prepareRelease
