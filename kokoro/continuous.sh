#!/bin/bash

set -e

gcloud components install docker-credential-gcr
docker-credential-gcr configure-docker
docker login -u _json_key -p "${JIB_INTEGRATION_TESTING_KEY}" https://gcr.io

set -x

cat $HOME/.docker/config.json

export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# Stops any left-over containers.
docker stop $(docker container ls --quiet) || true

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -P integration-tests -B -U -X)