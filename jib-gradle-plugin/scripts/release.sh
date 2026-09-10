#!/bin/sh

set -o errexit

readonly PUBLISH_KEY=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key")
readonly PUBLISH_SECRET=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret")

set -o xtrace

# From default hostname, get id of container to exclude
CONTAINER_ID=$(hostname)
echo "$CONTAINER_ID"

# Stops any left-over containers.
docker stop $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true
docker kill $(docker ps --all --quiet | grep -v "$CONTAINER_ID") || true
cd github/jib

echo "gradle publish"

# turn of command tracing when dealing with secrets
set +o xtrace

./gradlew :jib-gradle-plugin:publishPlugins \
  -Pgradle.publish.key="${PUBLISH_KEY}" \
  -Pgradle.publish.secret="${PUBLISH_SECRET}" \
  --info --stacktrace
