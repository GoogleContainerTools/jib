#!/bin/bash

set -e
set -x

cd github/jib

(cd jib-core; ./gradlew clean build integrationTest publishToMavenLocal --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -B -U -X)