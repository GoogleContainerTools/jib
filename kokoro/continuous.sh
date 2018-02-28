#!/bin/bash

set -e

# docker login -u _json_key -p "${JIB_INTEGRATION_TESTING_KEY}" https://gcr.io

set -x

gcloud components install docker-credential-gcr

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
echo ${JIB_INTEGRATION_TESTING_KEY} > keyfile.json
export GOOGLE_APPLICATION_CREDENTIALS=./keyfile.json
docker-credential-gcr configure-docker

export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -P integration-tests -B -U -X)
