#!/bin/sh

set -ex

export PROJECT_ID=$(gcloud config list --format 'value(core.project)')
# if there are no intermodule dependencies, compile is enough to complete a jib build.
./mvnw compile jib:build -pl hello-service

# multi module builds with dependencies on other modules in the build require that the
# "package" phase is executed as part of the build to correctly include module dependencies.
./mvnw package jib:build -pl name-service -am
