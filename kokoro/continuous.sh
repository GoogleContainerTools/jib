#!/bin/bash

set -e
set -x

cd github/jib

(cd jib-core; ./gradlew clean build --info)
