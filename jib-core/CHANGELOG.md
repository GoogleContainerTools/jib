# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

- `JibContainerBuilder#addDependencies` is now split into three methods: `addDependencies`, `addSnapshotDependencies`, `addProjectDependencies` ([#1773](https://github.com/GoogleContainerTools/jib/pull/1773))

### Fixed

## 0.10.0

### Added

- `Containerizer#addEventHandler` for adding event handlers

### Changed

- Multiple classes have been moved to the `com.google.cloud.tools.jib.api` package
- Event handlers are now added directly to the `Containerizer` rather than adding them to an `EventHandlers` object first
- Removed multiple classes to simplify the event system (`JibEventType`, `BuildStepType`, `EventDispatcher`, `DefaultEventDispatcher`, `LayerCountEvent`)
- MainClassFinder now uses a static method instead of requiring instantiation

## 0.9.2

### Added

- Container configurations in the base image are now propagated when registry uses the old V2 image manifest, schema version 1 (such as Quay) ([#1641](https://github.com/GoogleContainerTools/jib/issues/1641))
- `Containerizer#setOfflineMode` to retrieve the base image from Jib's cache rather than a container registry ([#718](https://github.com/GoogleContainerTools/jib/issues/718))

### Fixed

- Labels in the base image are now propagated ([#1643](https://github.com/GoogleContainerTools/jib/issues/1643))
- Fixed an issue with using OCI base images ([#1683](https://github.com/GoogleContainerTools/jib/issues/1683))

## 0.9.1

### Added

- Overloads for `LayerConfiguration#addEntryRecursive` that take providers allowing for setting file permissions/file modification timestamps on a per-file basis ([#1607](https://github.com/GoogleContainerTools/jib/issues/1607))

### Changed

- `LayerConfiguration` takes file modification time as an `Instant` instead of a `long`

### Fixed

- Fixed an issue where automatically generated parent directories in a layer did not get their timestamp configured correctly to epoch + 1s ([#1648](https://github.com/GoogleContainerTools/jib/issues/1648))
- Fixed an issue where the library creates wrong images by adding base image layers in reverse order when registry uses the old V2 image manifest, schema version 1 (such as Quay) ([#1627](https://github.com/GoogleContainerTools/jib/issues/1627))

## 0.9.0

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
