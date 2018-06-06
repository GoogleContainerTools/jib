#!/bin/bash -
# Usage: ./scripts/prepare_release.sh <release version>

set -e

Colorize() {
	echo "$(tput setff $2)$1$(tput sgr0)"
}

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
    Die "Usage: ./scripts/prepare_release.sh <release version>"
}

# Usage: CheckVersion <version>
CheckVersion() {
    [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || Die "Version not in ###.###.### format."
}

[ $# -ne 2 ] || DieUsage

EchoGreen '===== RELEASE SETUP SCRIPT ====='

VERSION=$1
CheckVersion ${VERSION}

if [[ $(git status -uno --porcelain) ]]; then
    Die 'There are uncommitted changes.'
fi

# Runs integration tests.
./gradlew integrationTest --info --stacktrace

# Checks out a new branch for this version release (eg. 1.5.7).
BRANCH=gradle_release_v${VERSION}
git checkout -b ${BRANCH}

# Changes the version for release and creates the commits/tags.
echo | ./gradlew release -PreleaseVersion=${VERSION}

# Pushes the release branch and tag to Github.
git push origin ${BRANCH}
git push origin v${VERSION}-gradle

# File a PR on Github for the new branch. Have someone LGTM it, which gives you permission to continue.
EchoGreen 'File a PR for the new release branch:'
echo https://github.com/GoogleContainerTools/jib/compare/${BRANCH}

EchoGreen "Once approved and merged, checkout the 'v${VERSION}-gradle' tag and run './gradlew publishPlugins'."
