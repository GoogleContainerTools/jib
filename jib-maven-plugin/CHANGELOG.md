# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

### Fixed
- Using Docker config for finding registry credentials (was not ignoring extra fields and handling `https` protocol) ([#524](https://github.com/GoogleContainerTools/jib/pull/524))

## 0.9.2

### Changed
- Minor improvements and issue fixes

## 0.9.1
### Added
- `<container><ports>` parameter to define container's exposed ports (similar to Dockerfile `EXPOSE`) ([#383](https://github.com/GoogleContainerTools/jib/issues/383))
- Can set `allowInsecureRegistries` parameter to `true` to use registries that only support HTTP ([#388](https://github.com/GoogleContainerTools/jib/issues/388)) 

### Changed
- Fetches credentials from inferred credential helper before Docker config ([#401](https://github.com/GoogleContainerTools/jib/issues/401))
- Container creation date set to timestamp 0 ([#341](https://github.com/GoogleContainerTools/jib/issues/341))
- Does not authenticate base image pull unless necessary - reduces build time by about 500ms ([#414](https://github.com/GoogleContainerTools/jib/pull/414))
- `jvmFlags`, `mainClass`, `args`, and `format` are now grouped under `container` configuration object ([#384](https://github.com/GoogleContainerTools/jib/issues/384))

### Fixed
- Using Azure Container Registry now works - define credentials in Maven settings ([#415](https://github.com/GoogleContainerTools/jib/issues/415))
- Supports `access_token` as alias to `token` in registry authentication ([#420](https://github.com/GoogleContainerTools/jib/pull/420))

## 0.9.0
### Added
- Better feedback for build failures ([#197](https://github.com/google/jib/pull/197))
- Warns if specified `mainClass` is not a valid Java class ([#206](https://github.com/google/jib/issues/206))
- Warns if build may not be reproducible ([#245](https://github.com/GoogleContainerTools/jib/pull/245))
- `jib:dockerBuild` maven goal to build straight to Docker daemon ([#266](https://github.com/GoogleContainerTools/jib/pull/266))
- `mainClass` is inferred by searching through class files if configuration is missing ([#278](https://github.com/GoogleContainerTools/jib/pull/278))
- Can now specify target image with `-Dimage` ([#328](https://github.com/GoogleContainerTools/jib/issues/328))
- `args` parameter to define default main args ([#346](https://github.com/GoogleContainerTools/jib/issues/346))

### Changed
- Removed `enableReproducibleBuilds` parameter - application layers will always be reproducible ([#245](https://github.com/GoogleContainerTools/jib/pull/245))
- Changed configuration schema to be more like configuration for `jib-gradle-plugin` - NOT compatible with prior versions of `jib-maven-plugin` ([#212](https://github.com/GoogleContainerTools/jib/issues/212))
- `jib:dockercontext` has been changed to `jib:exportDockerContext` ([#350](https://github.com/GoogleContainerTools/jib/issues/350))

### Fixed
- Directories in resources are added to classes layer ([#318](https://github.com/GoogleContainerTools/jib/issues/318))

## 0.1.7
### Fixed
- Using base images that lack entrypoints ([#284](https://github.com/GoogleContainerTools/jib/pull/284)

## 0.1.6
### Changed
- Base image layers are now cached on a user-level rather than a project level - disable with `useOnlyProjectCache` configuration ([#29](https://github.com/google/jib/issues/29))

### Fixed
- `jib:dockercontext` not building a `Dockerfile` ([#171](https://github.com/google/jib/pull/171))
- Failure to parse Docker config with `HttpHeaders` field ([#175](https://github.com/google/jib/pull/175))

## 0.1.5
### Added
- Export a Docker context (including a Dockerfile) with `jib:dockercontext` ([#49](https://github.com/google/jib/issues/49))

## 0.1.4
### Fixed
- Null tag validation generating NullPointerException ([#125](https://github.com/google/jib/issues/125))
- Build failure on project with no dependencies ([#126](https://github.com/google/jib/issues/126))

## 0.1.3
### Added
- Build and push OCI container image ([#96](https://github.com/google/jib/issues/96))

## 0.1.2
### Added
- Use credentials from Docker config if none can be found otherwise ([#101](https://github.com/google/jib/issues/101))
- Reproducible image building ([#7](https://github.com/google/jib/issues/7))

## 0.1.1
### Added
- Simple example `helloworld` project under `examples/` ([#62](https://github.com/google/jib/pull/62))
- Better error messages when pushing an image manifest ([#63](https://github.com/google/jib/pull/63))
- Validates target image configuration ([#63](https://github.com/google/jib/pull/63))
- Configure multiple credential helpers with `credHelpers` ([#68](https://github.com/google/jib/pull/68))
- Configure registry credentials with Maven settings ([#81](https://github.com/google/jib/pull/81))

### Changed
- Removed configuration `credentialHelperName` ([#68](https://github.com/google/jib/pull/68))

### Fixed
- Build failure on Windows ([#74](https://github.com/google/jib/issues/74))
- Infers common credential helper names (for GCR and ECR) ([#64](https://github.com/google/jib/pull/64))
- Cannot use private base image ([#68](https://github.com/google/jib/pull/68))
- Building applications with no resources ([#73](https://github.com/google/jib/pull/73))
- Pushing to registries like Docker Hub and ACR ([#75](https://github.com/google/jib/issues/75))
- Cannot build with files having long file names (> 100 chars) ([#91](https://github.com/google/jib/issues/91)) 
