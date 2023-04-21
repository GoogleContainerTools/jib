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

# From default hostname, get id of container to exclude
CONTAINER_ID=$(hostname)
echo "$CONTAINER_ID"

# Stops any left-over containers.
docker stop $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true
docker kill $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true

cd github/jib

# we only run integration tests on jib-core for presubmit
./gradlew clean build :jib-core:integrationTest --info --stacktrace
