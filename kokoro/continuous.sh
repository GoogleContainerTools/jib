#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
source github/jib/kokoro/docker_setup.sh
fi

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export GOOGLE_APPLICATION_CREDENTIALS=${KOKORO_KEYSTORE_DIR}/72743_jib_integration_testing_key

docker-credential-gcr configure-docker

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

# Sets the integration testing project.
export JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

cd github/jib

#./gradlew clean build integrationTest --info --stacktrace
./gradlew build :jib-gradle-plugin:integrationTest --tests=com.google.cloud.tools.jib.gradle.SingleProjectIntegrationTest
