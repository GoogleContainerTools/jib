# Change Log
All notable changes to this project will be documented in this file.
## [unreleased]

### Added

### Changed

### Fixed

## 0.1.1
### Added
- Simple example `helloworld` project under `examples/` ([#62](https://github.com/google/jib/pull/62))
- Better error messages when pushing an image manifest ([#63](https://github.com/google/jib/pull/63))
- Validates target image configuration ([#63](https://github.com/google/jib/pull/63))

### Changed
- Configure multiple credential helpers with `credHelpers` ([#68](https://github.com/google/jib/pull/68))
- Removed configuration `credentialHelperName` ([#68](https://github.com/google/jib/pull/68))

### Fixed
- Infers common credential helper names (for GCR and ECR) ([#64](https://github.com/google/jib/pull/64))
- Cannot use private base image ([#68](https://github.com/google/jib/pull/68))
- Building applications with no resources ([#73](https://github.com/google/jib/pull/73))
