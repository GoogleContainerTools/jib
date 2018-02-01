#!/bin/bash

set -e
set -x

cd github/jib

(cd jib-core; ./gradlew clean build --info)
(cd jib-maven-plugin; ./mvnw clean install cobertura:cobertura -B -U -X)