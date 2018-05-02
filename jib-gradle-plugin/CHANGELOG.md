# Change Log
All notable changes to this project will be documented in this file.
## [unreleased]

### Added

### Changed

### Fixed

## 0.9.0

### Changed

- Removed `reproducible` parameter - application layers will always be reproducible ([#]())

## 0.1.2

### Added

- Export a Docker context (including a Dockerfile) with `jibDockerContext` ([#204](https://github.com/google/jib/issues/204))

## 0.1.1

### Added

- Warns if specified `mainClass` is not a valid Java class ([#206](https://github.com/google/jib/issues/206))
- Can specify registry credentials to use directly with `from.auth` and `to.auth` ([#215](https://github.com/google/jib/issues/215))
w