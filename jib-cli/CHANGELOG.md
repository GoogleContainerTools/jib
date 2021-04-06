# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

- Added `--image-metadata-out` option to specify JSON output file that should contain image metadata (image ID, digest, and tags) after build is complete. ([#3187](https://github.com/GoogleContainerTools/jib/pull/3187))

### Changed

### Fixed

## 0.5.0

### Fixed

- Fixed an issue where critical error messages (for example, unauthorized access from a registry) were erased by progress reporting and not shown. ([#3148](https://github.com/GoogleContainerTools/jib/issues/3148))

## 0.4.0

### Added

- Added support for [configuring registry mirrors](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#i-am-hitting-docker-hub-rate-limits-how-can-i-configure-registry-mirrors) for base images. This is useful when hitting [Docker Hub rate limits](https://www.docker.com/increase-rate-limits). Only public mirrors (such as `mirror.gcr.io`) are supported. ([#3134](https://github.com/GoogleContainerTools/jib/pull/3134))

## 0.3.0

### Changed

- Changed the default base image of the Jib CLI jar command from the `openjdk` images to the `adoptopenjdk` images on Docker Hub. ([#3108](https://github.com/GoogleContainerTools/jib/pull/3108))

## 0.2.0

### Added

- Added the `jar` command which can be used to containerize a JAR with `$ jib jar --target ... my-app.jar`. By default, the command will add the contents of the JAR into optimized layers on the container. ([#11](https://github.com/GoogleContainerTools/jib/projects/11))
