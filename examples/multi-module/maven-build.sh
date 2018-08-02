#!/bin/sh

set -ex

export PROJECT_ID=$(gcloud config list --format 'value(core.project)')
./mvnw compile jib:build
