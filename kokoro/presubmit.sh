#!/bin/bash

set -e
set -x

echo QUIT | openssl s_client -connect gcr.io:443 > cert.crt
openssl x509 -in cert.crt -text -noout | grep -i validity -A 2

gcloud components install docker-credential-gcr
export PATH=$PATH:/usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/

# Stops any left-over containers.
docker stop $(docker ps --all --quiet) || true
docker kill $(docker ps --all --quiet) || true

cd github/jib

date
(cd jib-core; ./gradlew clean build integrationTest --info --stacktrace)
(cd jib-plugins-common; ./gradlew clean build --info --stacktrace)
(cd jib-maven-plugin; ./mvnw clean install -B -U -X)
(cd jib-gradle-plugin; ./gradlew clean build --info --stacktrace)
