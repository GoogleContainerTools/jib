# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

- Replaced `BiFunction` usage in `FileEntriesLayer` with `FilePermissionsProvider`, `ModificationTimeProvider` and `OwnershipProvider`. ([#2638](https://github.com/GoogleContainerTools/jib/issues/2638))

### Fixed

## 0.3.1

### Added

- Added `Platform` class representing an image platform. ([#2584](https://github.com/GoogleContainerTools/jib/pull/2584))
- Added `get/setPlatforms()` and `addPlatform()` to `ContainerBuildPlan` for setting and getting image platforms. ([#2584](https://github.com/GoogleContainerTools/jib/pull/2584))

### Changed

- Removed `get/setOsHint()` and `get/setArchitectureHint()` in favor of `get/setPlatforms()` and `addPlatform()`. ([#2584](https://github.com/GoogleContainerTools/jib/pull/2584))

### Fixed

- Fixed the critical bug that the default `Platform` in `ContainerBuildPlan` has OS and architecture values switched with each other. ([#2597](https://github.com/GoogleContainerTools/jib/pull/2597)

## 0.2.0

### Added

- Added file ownership information in `FileEntry` and `FileEntriesLayer`. ([#2494](https://github.com/GoogleContainerTools/jib/pull/2494))
