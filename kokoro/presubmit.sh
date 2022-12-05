#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
source github/jib/kokoro/docker_setup.sh
fi

# Try using docker host ip instead of localhost for http requests
export DOCKER_IP="$(docker-machine ip default)"

# Stops any left-over containers.
# From default hostname, get id of container to exclude
DEFAULT_HOSTNAME=$(hostname)
echo "$DEFAULT_HOSTNAME"
docker stop $(docker ps --all --quiet | grep -v "$DEFAULT_HOSTNAME") || true
docker kill $(docker ps --all --quiet | grep -v "$DEFAULT_HOSTNAME") || true

cd github/jib

# we only run integration tests on jib-core for presubmit
./gradlew clean build :jib-core:integrationTest --info --stacktrace
