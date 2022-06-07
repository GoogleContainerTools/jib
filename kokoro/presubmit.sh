#!/bin/bash

set -o errexit
set -o xtrace

gcloud components install docker-credential-gcr

# Docker service does not run by default in Big Sur but can be started with the following commands.
if [ "${KOKORO_JOB_CLUSTER}" = "MACOS_EXTERNAL" ]; then
docker-machine ls
docker-machine start default
export DOCKER_IP="$(docker-machine ip default)"
echo $DOCKER_IP
docker-machine ssh default "echo '{ \"insecure-registries\":[\"$DOCKER_IP:5000\", \"$DOCKER_IP:8080]\" }' | sudo tee /etc/docker/daemon.json "
docker-machine ssh default "echo 'DOCKER_OPTS=\"--config-file=/etc/docker/daemon.json\"' | sudo tee -a /var/lib/boot2docker/profile "
docker-machine ssh default "sudo /etc/init.d/docker restart"
docker-machine env default
eval "$(docker-machine env default)"


export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-8-latest/Contents/Home"
export PATH=$JAVA_HOME/bin:$PATH
echo $JAVA_HOME
fi

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

cd github/jib

# we only run integration tests on jib-core for presubmit
./gradlew clean build :jib-core:integrationTest --info --stacktrace
