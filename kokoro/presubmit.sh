#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
docker-machine create --driver virtualbox default
docker-machine env default
eval "$(docker-machine env default)"
fi

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

# Restarting Docker for Mac to get around the certificate expiration issue:
# b/112707824
# https://github.com/GoogleContainerTools/jib/issues/730#issuecomment-413603874
# https://github.com/moby/moby/issues/11534
# TODO: remove this temporary fix once b/112707824 is permanently fixed.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
  osascript -e 'quit app "Docker"'
  open -a Docker
  while ! docker info > /dev/null 2>&1; do sleep 1; done
fi

cd github/jib

# we only run integration tests on jib-core for presubmit
./gradlew clean build :jib-core:integrationTest --info --stacktrace
