#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
source github/jib/kokoro/docker_setup.sh
fi

# In GCP_UBUNTU_DOCKER, the build script runs in a container and requires additional setup
if [ "${KOKORO_JOB_CLUSTER}" = "GCP_UBUNTU_DOCKER" ]; then
export DOCKER_HOST_IP="$(/sbin/ip route|awk '/default/ { print $3 }')"
echo "DOCKER_HOST_IP: ${DOCKER_HOST_IP}"
echo "${DOCKER_HOST_IP} localhost" >> /etc/hosts
mkdir -p /tmpfs/auth
docker run --entrypoint htpasswd httpd:2 -Bbn testuser testpassword > /tmpfs/auth/htpasswd
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

# temporary: split up tests for troubleshooting
# TODO: revert before merging
./gradlew clean build :jib-gradle-plugin:integrationTest --info --stacktrace
./gradlew clean build :jib-maven-plugin:integrationTest --info --stacktrace

./gradlew clean build integrationTest --info --stacktrace