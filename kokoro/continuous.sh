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
docker-machine ssh default "echo '{ \"insecure-registries\":[\"$DOCKER_IP\"] }' | sudo tee /etc/docker/daemon.json "
docker-machine ssh default "echo 'DOCKER_OPTS=\"--config-file=/etc/docker/daemon.json\"' | sudo tee -a /var/lib/boot2docker/profile "
docker-machine ssh default "sudo /etc/init.d/docker restart"
docker-machine env default
eval "$(docker-machine env default)"

export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-8-latest/Contents/Home"
export PATH=$JAVA_HOME/bin:$PATH
echo $JAVA_HOME
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

./gradlew clean build integrationTest --info --stacktrace
