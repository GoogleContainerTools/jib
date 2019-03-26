# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

- `JavaContainerBuilder#setAppRoot()` and `JavaContainerBuilder#fromDistrolessJetty()` for building WAR containers ([#1464](https://github.com/GoogleContainerTools/jib/issues/1464))
- `Jib#fromScratch()` to start building from an empty base image ([#1471](https://github.com/GoogleContainerTools/jib/issues/1471))
- Methods in `JavaContainerBuilder` for setting the destination directories for classes, resources, directories, and additional classpath files

### Changed
- Allow skipping `JavaContainerBuilder#setMainClass()` to skip setting the entrypoint
- `os` and `architecture` are taken from base image ([#1564](https://github.com/GoogleContainerTools/jib/pull/1564))

### Fixed
- `ImageReference` assumes `registry-1.docker.io` as the registry if the host part of an image reference is `docker.io` ([#1549](https://github.com/GoogleContainerTools/jib/issues/1549))

## 0.1.2

### Added

- `ProgressEvent#getBuildStepType` method to get which step in the build process a progress event corresponds to ([#1449](https://github.com/GoogleContainerTools/jib/pull/1449))
- `LayerCountEvent` that is dispatched at the beginning of certain pull/build/push build steps to indicate the number of layers being processed ([#1461](https://github.com/GoogleContainerTools/jib/pull/1461))

### Changed

- `JibContainerBuilder#containerize()` throws multiple sub-types of `RegistryException` rather than wrapping them in an `ExecutionException` ([#1440](https://github.com/GoogleContainerTools/jib/issues/1440))

### Fixed

- `MainClassFinder` failure when main method is defined using varargs (i.e. `public static void main(String... args)`) ([#1456](https://github.com/GoogleContainerTools/jib/issues/1456))

## 0.1.1

### Added

- Adds support for configuring volumes ([#1121](https://github.com/GoogleContainerTools/jib/issues/1121))
- Adds `JavaContainerBuilder` for building opinionated containers for Java applications ([#1212](https://github.com/GoogleContainerTools/jib/issues/1212))
