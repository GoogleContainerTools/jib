#!/bin/sh

set -o errexit

readonly PUBLISH_KEY=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key")
readonly PUBLISH_SECRET=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret")

set -o xtrace

gcloud components install docker-credential-gcr

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export readonly GOOGLE_APPLICATION_CREDENTIALS="${KOKORO_KEYSTORE_DIR}/72743_jib_integration_testing_key"
docker-credential-gcr configure-docker

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

# Sets the integration testing project.
export readonly JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

cd github/jib

echo "gradle publish"

# turn of command tracing when dealing with secrets
set +o xtrace

./gradlew :jib-gradle-plugin:publishPlugins \
  -Pgradle.publish.key="${PUBLISH_KEY}" \
  -Pgradle.publish.secret="${PUBLISH_SECRET}" \
  --info --stacktrace
