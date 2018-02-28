#!/bin/bash

set -e

echo ${JIB_INTEGRATION_TESTING_KEY} > ./keyfile.json

set -x

gcloud components install docker-credential-gcr

# docker-credential-gcr uses GOOGLE_APPLICATION_CREDENTIALS as the credentials key file
export GOOGLE_APPLICATION_CREDENTIALS=./keyfile.json
docker-credential-gcr configure-docker

which docker-credential-gcr
echo gcr.io | docker-credential-gcr get
echo -n gcr.io | docker-credential-gcr get

# export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)

which docker-credential-gcr
echo gcr.io | docker-credential-gcr get
echo -n gcr.io | docker-credential-gcr get

(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -P integration-tests -B -U -X)
