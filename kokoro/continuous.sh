#!/bin/bash

set -e
set -x

cd github/jib

(cd crepecake; ./gradlew clean build --info)
