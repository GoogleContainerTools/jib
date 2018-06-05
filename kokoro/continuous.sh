#!/bin/bash

set -e

echo ${JIB_INTEGRATION_TESTING_KEY} > ./keyfile.json

set -x

gcloud components install docker-credential-gcr

# For macOS to find docker-credential-gcr
export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/keyfile.json
docker-credential-gcr configure-docker

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

(cd github/jib/jib-core; ./gradlew clean build integrationTest --info --stacktrace)
(cd github/jib/jib-maven-plugin; ./mvnw clean install -P integration-tests -B -U -X)
(cd github/jib/jib-gradle-plugin; ./gradlew clean build integrationTest --info --stacktrace)
