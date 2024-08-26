#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
  source github/jib/kokoro/docker_setup_macos.sh
fi

# In GCP_UBUNTU_DOCKER, the build script runs in a container and requires additional setup
if [ "${KOKORO_JOB_CLUSTER}" = "GCP_UBUNTU_DOCKER" ]; then
  source github/jib/kokoro/docker_setup_ubuntu.sh
fi

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export GOOGLE_APPLICATION_CREDENTIALS=${KOKORO_KEYSTORE_DIR}/72743_jib_integration_testing_key

# Overrides default search order to check credentials in GOOGLE_APPLICATION_CREDENTIALS
docker-credential-gcr config --token-source="env"
docker-credential-gcr configure-docker

# From default hostname, get id of container to exclude
CONTAINER_ID=$(hostname)
echo "$CONTAINER_ID"

# Stops any left-over containers.
docker stop $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true
docker kill $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true

# Sets the integration testing project.
export JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

cd github/jib

./gradlew clean build integrationTest --info --stacktrace --debug
