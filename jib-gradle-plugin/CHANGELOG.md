# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

- Fetches credentials from inferred credential helper before Docker config ([#401](https://github.com/GoogleContainerTools/jib/issues/401))

### Fixed 

- Registries without TLS now supported ([#388](https://github.com/GoogleContainerTools/jib/issues/388))

## 0.9.0

### Added

- Export a Docker context (including a Dockerfile) with `jibExportDockerContext` ([#204](https://github.com/google/jib/issues/204))
- Warns if build may not be reproducible ([#245](https://github.com/GoogleContainerTools/jib/pull/245))
- `jibDockerBuild` gradle task to build straight to Docker daemon ([#265](https://github.com/GoogleContainerTools/jib/pull/265))
- `mainClass` is inferred by searching through class files if configuration is missing ([#278](https://github.com/GoogleContainerTools/jib/pull/278))
- All tasks depend on `classes` by default ([#335](https://github.com/GoogleContainerTools/jib/issues/335))
- Can now specify target image with `--image` ([#328](https://github.com/GoogleContainerTools/jib/issues/328))
- `args` parameter to define default main arguments ([#346](https://github.com/GoogleContainerTools/jib/issues/346))

### Changed

- Removed `reproducible` parameter - application layers will always be reproducible ([#245](https://github.com/GoogleContainerTools/jib/pull/245)) 

### Fixed

- Using base images that lack entrypoints ([#284](https://github.com/GoogleContainerTools/jib/pull/284)

## 0.1.1

### Added

- Warns if specified `mainClass` is not a valid Java class ([#206](https://github.com/google/jib/issues/206))
- Can specify registry credentials to use directly with `from.auth` and `to.auth` ([#215](https://github.com/google/jib/issues/215))
