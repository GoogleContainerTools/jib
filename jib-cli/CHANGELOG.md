# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

### Fixed

## 0.11.0

### Added
- Included `imagePushed` field to image metadata json output file which provides information on whether an image was pushed by Jib. ([#3641](https://github.com/GoogleContainerTools/jib/pull/3641))
- Better error messaging when environment map in `container.environment` contains null values ([#3672](https://github.com/GoogleContainerTools/jib/pull/3672)).
- Starting with jib-cli 0.11.0, [SLSA 3 signatures](https://slsa.dev/) will be generated with every release. ([#3762](https://github.com/GoogleContainerTools/jib/pull/3726)).

### Changed
- Upgraded slf4j-api to 2.0.0 ([#3735](https://github.com/GoogleContainerTools/jib/pull/3735)).
- Upgraded nullaway to 0.9.9 ([#3720](https://github.com/GoogleContainerTools/jib/pull/3720)).

Thanks to our community contributors @wwadge @oliver-brm and @laurentsimon!

## 0.10.0

### Changed
- Upgraded jackson-databind to 2.13.2.2 ([#3612](https://github.com/GoogleContainerTools/jib/issues/3612)).

### Fixed

- Incorrect release sha256 file for jib-cli. ([#3584](https://github.com/GoogleContainerTools/jib/issues/3584))

## 0.9.0

### Changed

- For Java 17, changed the default base image of the Jib CLI `jar` command from the `azul/zulu-openjdk` to [`eclipse-temurin`](https://hub.docker.com/_/eclipse-temurin). ([#3483](https://github.com/GoogleContainerTools/jib/issues/3483))

## 0.8.0

### Added

- Increased robustness in registry communications by retrying HTTP requests (to the effect of retrying image pushes or pulls) on I/O exceptions with exponential backoffs. ([#3351](https://github.com/GoogleContainerTools/jib/pull/3351))
- Now also supports `username` and `password` properties for the `auths` section in a Docker config (`~/.docker/config.json`). (Previously, only supported was a base64-encoded username and password string of the `auth` property.) ([#3365](https://github.com/GoogleContainerTools/jib/pull/3365))

### Changed

- Downgraded Google HTTP libraries to 1.34.0 to resolve network issues. ([#3415](https://github.com/GoogleContainerTools/jib/pull/3415), [#3058](https://github.com/GoogleContainerTools/jib/issues/3058), [#3409](https://github.com/GoogleContainerTools/jib/issues/3409))
- Changed the default base image of the Jib CLI `jar` command from the `adoptopenjdk` images to the [`eclipse-temurin`](https://hub.docker.com/_/eclipse-temurin) (for Java 8 and 11) and [`azul/zulu-openjdk`](https://hub.docker.com/r/azul/zulu-openjdk) (for Java 17) images on Docker Hub. Note that Temurin (by Adoptium) is the new name of AdoptOpenJDK. ([#3491](https://github.com/GoogleContainerTools/jib/pull/3491))

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
