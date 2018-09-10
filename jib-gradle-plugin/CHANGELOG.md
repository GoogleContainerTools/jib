# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

- `container.environment` configuration parameter to configure environment variables ([#890](https://github.com/GoogleContainerTools/jib/issues/890))

### Changed

### Fixed

## 0.9.10

### Added

- `container.labels` configuration parameter for configuring labels ([#751](https://github.com/GoogleContainerTools/jib/issues/751))
- `container.entrypoint` configuration parameter to set the entrypoint ([#579](https://github.com/GoogleContainerTools/jib/issues/579))
- `history` to layer metadata ([#875](https://github.com/GoogleContainerTools/jib/issues/875))
- Propagates working directory from the base image ([#902](https://github.com/GoogleContainerTools/jib/pull/902))

### Fixed

- Corrects permissions for directories in the container filesystem ([#772](https://github.com/GoogleContainerTools/jib/pull/772))

## 0.9.9

### Added

- Passthrough labels from base image ([#750](https://github.com/GoogleContainerTools/jib/pull/750/files))

### Changed

- Reordered classpath in entrypoint to use _resources_, _classes_, and then _dependencies_, to allow dependency patching
  ([#777](https://github.com/GoogleContainerTools/jib/issues/777)).  Note that this classpath ordering differs from that used by Gradle's `run` task.
- Changed logging level of missing build output directory message ([#677](https://github.com/GoogleContainerTools/jib/issues/677))

### Fixed

- Gradle project dependencies have their `assemble` task run before running a jib task ([#815](https://github.com/GoogleContainerTools/jib/issues/815))

## 0.9.8

### Added

- Docker context generation now includes snapshot dependencies and extra files ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Disable parallel operation by setting the `jibSerialize` system property to `true` ([#682](https://github.com/GoogleContainerTools/jib/pull/682))

### Changed

- Propagates environment variables from the base image ([#716](https://github.com/GoogleContainerTools/jib/pull/716))
- `allowInsecureRegistries` allows connecting to insecure HTTPS registries (for example, registries using self-signed certificates) ([#733](https://github.com/GoogleContainerTools/jib/pull/733))

### Fixed

- Slow image reference parsing ([#680](https://github.com/GoogleContainerTools/jib/pull/680))
- Building empty layers ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Duplicate layer entries causing unbounded cache growth ([#721](https://github.com/GoogleContainerTools/jib/issues/721))
- Incorrect authentication error message when target and base registry are the same ([#758](https://github.com/GoogleContainerTools/jib/issues/758))

## 0.9.7

### Added

- Snapshot dependencies are added as their own layer ([#584](https://github.com/GoogleContainerTools/jib/pull/584))
- `jibBuildTar` task to build an image tarball at `build/jib-image.tar`, which can be loaded into docker using `docker load` ([#514](https://github.com/GoogleContainerTools/jib/issues/514))
- `container.useCurrentTimestamp` parameter to set the image creation time to the build time ([#413](https://github.com/GoogleContainerTools/jib/issues/413))
- Authentication over HTTP using the `sendCredentialsOverHttp` system property ([#599](https://github.com/GoogleContainerTools/jib/issues/599))
- HTTP connection and read timeouts for registry interactions configurable with the `jib.httpTimeout` system property ([#656](https://github.com/GoogleContainerTools/jib/pull/656))
- Docker context export command-line option `--targetDir` to `--jibTargetDir` ([#662](https://github.com/GoogleContainerTools/jib/issues/662))

### Changed

- Docker context export command-line option `--targetDir` to `--jibTargetDir` ([#662](https://github.com/GoogleContainerTools/jib/issues/662))

### Fixed

- Using multi-byte characters in container configuration ([#626](https://github.com/GoogleContainerTools/jib/issues/626))
- For Docker Hub, also tries registry aliases when getting a credential from the Docker config ([#605](https://github.com/GoogleContainerTools/jib/pull/605))

## 0.9.6

### Fixed

- Using a private registry that does token authentication with `allowInsecureRegistries` set to `true` ([#572](https://github.com/GoogleContainerTools/jib/pull/572))

## 0.9.5

### Added

- Incubating feature to build `src/main/jib` as extra layer in image ([#562](https://github.com/GoogleContainerTools/jib/pull/562))

## 0.9.4

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
