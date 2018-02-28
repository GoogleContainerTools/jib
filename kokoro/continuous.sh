#!/bin/bash

set -e

echo ${JIB_INTEGRATION_TESTING_KEY} > ./keyfile.json

set -x

gcloud components install docker-credential-gcr

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export GOOGLE_APPLICATION_CREDENTIALS=./keyfile.json
docker-credential-gcr configure-docker

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

(cd github/jib/jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd github/jib/jib-maven-plugin; ./mvnw clean install cobertura:cobertura -P integration-tests -B -U -X)
