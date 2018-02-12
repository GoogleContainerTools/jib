#!/bin/bash

set -e
set -x

which docker
gcloud components install docker-credential-gcr

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -B -U -X)