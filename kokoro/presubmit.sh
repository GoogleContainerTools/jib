#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
docker-machine create --driver virtualbox default
docker-machine env default
eval "$(docker-machine env default)"

echo $JAVA_HOME
ls /Library/Java/JavaVirtualMachines/
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-1.8.0_221.jdk/Contents/Home"
export PATH=$JAVA_HOME/bin:$PATH
fi

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

cd github/jib

# we only run integration tests on jib-core for presubmit
./gradlew clean build :jib-core:integrationTest --info --stacktrace
