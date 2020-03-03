#!/bin/sh

set -o errexit
set -o xtrace

readonly PUBLISH_KEY=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key")
readonly PUBLISH_SECRET=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret")

export readonly JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

cd github/jib

./gradlew :jib-gradle-plugin:publishPlugins \
  -Pgradle.publish.key="${PUBLISH_KEY}" \
  -Pgradle.publish.secret="${PUBLISH_SECRET}" \
  --info --stacktrace
