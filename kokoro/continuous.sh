#!/bin/bash

set -e
set -x

# For MacOS builds, link Docker to run as 'docker'.
export PATH=$PATH:/Applications/Docker.app/Contents/Resources/bin

# Runs docker daemon.
sudo service docker start

docker-machine ls
eval "$(docker-machine env default)"
docker ps

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -B -U -X)