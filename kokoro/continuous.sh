#!/bin/bash

set -e
set -x

cd github/minikube-build-tools-for-java

(cd minikube-gradle-plugin; ./gradlew clean build)
(cd minikube-maven-plugin; ./mvnw clean install)
(cd crepecake; ./gradlew clean build)
