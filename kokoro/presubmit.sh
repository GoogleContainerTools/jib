#!/bin/bash

set -e
set -x

gcloud components install docker-credential-gcr
export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest --info --stacktrace)
(cd jib-maven-plugin; ./mvnw clean install -B -U -X)
(cd jib-gradle-plugin; ./gradlew clean build --info --stacktrace)
