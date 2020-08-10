#!/bin/bash

# Fail on any error.
set -o errexit
# Display commands to stderr.
set -o xtrace

cd github/jib
./gradlew :jib-core:prepareRelease
