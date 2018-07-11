# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

### Fixed

- Fixed handling case-insensitive `Basic` authentication method ([#546](https://github.com/GoogleContainerTools/jib/pull/546))
- Fixed regression that broke pulling base images from registries that required token authentication ([#549](https://github.com/GoogleContainerTools/jib/pull/549))

## 0.9.3

### Fixed

- Using Docker config for finding registry credentials (was not ignoring extra fields and handling `https` protocol) ([#524](https://github.com/GoogleContainerTools/jib/pull/524))

## 0.9.2

### Added

- Can configure `jibExportDockerContext` output directory with `jibExportDockerContext.targetDir` ([#492](https://github.com/GoogleContainerTools/jib/pull/492))

### Changed

### Fixed

- Set `jibExportDockerContext` output directory with command line option `--targetDir` ([#499](https://github.com/GoogleContainerTools/jib/pull/499))

## 0.9.1

### Added

- `container.ports` parameter to define container's exposed ports (similar to Dockerfile `EXPOSE`) ([#383](https://github.com/GoogleContainerTools/jib/issues/383))
- Can set `allowInsecureRegistries` parameter to `true` to use registries that only support HTTP ([#388](https://github.com/GoogleContainerTools/jib/issues/388)) 

### Changed

- Fetches credentials from inferred credential helper before Docker config ([#401](https://github.com/GoogleContainerTools/jib/issues/401))
- Container creation date set to timestamp 0 ([#341](https://github.com/GoogleContainerTools/jib/issues/341))
- Does not authenticate base image pull unless necessary - reduces build time by about 500ms ([#414](https://github.com/GoogleContainerTools/jib/pull/414))
- `jvmFlags`, `mainClass`, `args`, and `format` are now grouped under `container` configuration object ([#384](https://github.com/GoogleContainerTools/jib/issues/384))
- Warns instead of errors when classes not found ([#462](https://github.com/GoogleContainerTools/jib/pull/462))

### Fixed 

- Using Azure Container Registry now works - define credentials in `jib.to.auth`/`jib.from.auth` ([#415](https://github.com/GoogleContainerTools/jib/issues/415))
- Supports `access_token` as alias to `token` in registry authentication ([#420](https://github.com/GoogleContainerTools/jib/pull/420))
- Docker context export for Groovy project ([#459](https://github.com/GoogleContainerTools/jib/pull/459))
- Visibility of `jib.to.image` ([#460](https://github.com/GoogleContainerTools/jib/pull/460))

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
