#!/bin/bash -
# Usage: ./jib-core/scripts/prepare_release.sh <release version>

set -o errexit

EchoRed() {
  echo "$(tput setaf 1; tput bold)$1$(tput sgr0)"
}
EchoGreen() {
  echo "$(tput setaf 2; tput bold)$1$(tput sgr0)"
}

Die() {
  EchoRed "$1"
  exit 1
}

DieUsage() {
  Die "Usage: ./jib-core/scripts/prepare_release.sh <release version> [<post-release-version>]"
}

# Usage: CheckVersion <version>
CheckVersion() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

[ $# -ne 1 ] && [ $# -ne 2 ] && DieUsage

EchoGreen '===== RELEASE SETUP SCRIPT ====='

VERSION=$1
CheckVersion ${VERSION}
if [ -n "$2" ]; then
  POST_RELEASE_VERSION=$2
  CheckVersion ${POST_RELEASE_VERSION}
fi

if [[ $(git status -uno --porcelain) ]]; then
  Die 'There are uncommitted changes.'
fi

# Runs checks and integration tests.
./gradlew :jib-core:check :jib-core:integrationTest --info --stacktrace

# Checks out a new branch for this version release (eg. 1.5.7).
BRANCH=core_release_v${VERSION}
git checkout -b ${BRANCH}

# Changes the version for release and creates the commits/tags.
./gradlew :jib-core:release \
  -Prelease.useAutomaticVersion=true \
  -Prelease.releaseVersion=${VERSION} \
  ${POST_RELEASE_VERSION:+"-Prelease.newVersion=${POST_RELEASE_VERSION}"}

# File a PR on Github for the new branch. Have someone LGTM it, which gives you permission to continue.
EchoGreen 'File a PR for the new release branch:'
echo https://github.com/GoogleContainerTools/jib/pull/new/${BRANCH}

EchoGreen "Merge the PR after the library is released."
