#!/bin/sh

set -o errexit
set -o xtrace

readonly GRADLE_PUBLISH_KEY=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key")
readonly GRADLE_PUBLISH_SECRET=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret")

cd github/jib

./gradlew :jib-gradle-plugin:publishPlugins \
  -Pgradle.publish.key="${GRADLE_PUBLISH_KEY}" \
  -Pgradle.publish.secret="${GRADLE_PUBLISH_SECRET}" \
  --info --stacktrace
