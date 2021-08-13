# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

- Increased robustness in registry communications by retrying HTTP requests (to the effect of retrying image pushes or pulls) on I/O exceptions with exponential backoffs. ([#3351](https://github.com/GoogleContainerTools/jib/pull/3351))
- Now also supports `username` and `password` properties for the `auths` section in a Docker config (`~/.docker/config.json`). (Previously, only supported was a base64-encoded username and password string of the `auth` property.) ([#3365](https://github.com/GoogleContainerTools/jib/pull/3365))

### Changed

- Downgraded Google HTTP libraries to 1.34.0 to resolve network issues. ([#3058](https://github.com/GoogleContainerTools/jib/issue/3058) and [#3409](https://github.com/GoogleContainerTools/jib/issue/3409])) ([#3415](https://github.com/GoogleContainerTools/jib/pull/3415))

### Fixed

## 0.7.0

### Added

- Added the `war` command which can be used to containerize a standard WAR with `$ jib war --target ... my-app.war`. The command will explode out the contents of the WAR into optimized layers on the container. ([#3285](https://github.com/GoogleContainerTools/jib/pull/3285))

## 0.6.0

### Added

- Added automatic update check. Jib CLI will now display a message if a new version is available. See the [privacy page](https://github.com/GoogleContainerTools/jib/blob/master/docs/privacy.md) for more details. ([#3165](https://github.com/GoogleContainerTools/jib/pull/3165))
- Added `--image-metadata-out` option to specify JSON output file that should contain image metadata (image ID, digest, and tags) after build is complete. ([#3187](https://github.com/GoogleContainerTools/jib/pull/3187))

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
