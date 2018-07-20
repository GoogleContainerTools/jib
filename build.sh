#!/bin/sh
# Build Jib

# doBuild: Run a command in a directory
# $1 = directory
# $2... = build command
doBuild() {
    (directory="$1"; shift; echo ">>> $directory: $*"; cd "$directory" && eval '"$@"')
}

set -e  # exit on error
doBuild jib-core           ./gradlew googleJavaFormat build
doBuild jib-gradle-plugin  ./gradlew googleJavaFormat build install
doBuild jib-maven-plugin   ./mvnw fmt:format install -U
