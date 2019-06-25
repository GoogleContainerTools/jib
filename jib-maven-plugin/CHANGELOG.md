# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

- Can now containerize a JAR artifact instead of putting individual `.class` and resource files with `<containerizingMode>packaged` ([#1746](https://github.com/GoogleContainerTools/jib/pull/1746/files))
- Can now use `<from><image>scratch` to use the scratch (empty) base image for builds. ([#1794](https://github.com/GoogleContainerTools/jib/pull/1794/files))

### Changed

- Dependencies are now split into three layers: dependencies, snapshots dependencies, project dependencies ([#1724](https://github.com/GoogleContainerTools/jib/pull/1724))

### Fixed

- Re-enabled cross-repository blob mounts ([#1793](https://github.com/GoogleContainerTools/jib/pull/1793))

## 1.3.0

### Changed

- Docker credentials (`~/.docker/config.json`) are now given priority over registry-based inferred credential helpers ([#1704](https://github.com/GoogleContainerTools/jib/pulls/1704))

### Fixed

- Fixed an issue where decyrpting Maven settings `settings.xml` wholesale caused the build to fail. We now decrypt only the parts that are required. ([#1709](https://github.com/GoogleContainerTools/jib/issues/1709))

## 1.2.0

### Added

- Container configurations in the base image are now propagated when registry uses the old V2 image manifest, schema version 1 (such as Quay) ([#1641](https://github.com/GoogleContainerTools/jib/issues/1641))
- Can now prepend paths in the container to the computed classpath with `<container><extraClasspath>` ([#1642](https://github.com/GoogleContainerTools/jib/pull/1642))
- Can now build in offline mode using `--offline` ([#718](https://github.com/GoogleContainerTools/jib/issues/718))
- Now supports multiple extra directories with `<extraDirectories>{<paths><path>|<permissions>}` ([#1020](https://github.com/GoogleContainerTools/jib/issues/1020))

### Changed

- `<extraDirectory>(<path>|<permissions>)` are deprecated in favor of the new `<extraDirectories>{<paths><path>|<permissions>}` configurations ([#1626](https://github.com/GoogleContainerTools/jib/pull/1626))

### Fixed

- Labels in the base image are now propagated ([#1643](https://github.com/GoogleContainerTools/jib/issues/1643))
- Fixed an issue with using OCI base images ([#1683](https://github.com/GoogleContainerTools/jib/issues/1683))

## 1.1.2

### Fixed

- Fixed an issue where automatically generated parent directories in a layer did not get their timestamp configured correctly to epoch + 1s ([#1648](https://github.com/GoogleContainerTools/jib/issues/1648))

## 1.1.1

### Fixed

- Fixed an issue where the plugin creates wrong images by adding base image layers in reverse order when registry uses the old V2 image manifest, schema version 1 (such as Quay) ([#1627](https://github.com/GoogleContainerTools/jib/issues/1627))

## 1.1.0

### Added

- Can now decrypt proxy configurations in `settings.xml`. ([#1369](https://github.com/GoogleContainerTools/jib/issues/1369))

### Changed

- `os` and `architecture` are taken from base image ([#1564](https://github.com/GoogleContainerTools/jib/pull/1564))

### Fixed

- Fixed an issue where pushing to Docker Hub fails when the host part of an image reference is `docker.io` ([#1549](https://github.com/GoogleContainerTools/jib/issues/1549))

## 1.0.2

### Added

- Java 9+ WAR projects are now supported and run on the distroless Jetty Java 11 image (https://github.com/GoogleContainerTools/distroless) by default. Java 8 projects remain on the distroless Jetty Java 8 image. ([#1510](https://github.com/GoogleContainerTools/jib/issues/1510))
- Now supports authentication against Azure Container Registry using `docker-credential-acr-*` credential helpers. ([#1490](https://github.com/GoogleContainerTools/jib/issues/1490))
- Batch mode now disables build progress bar. ([#1513](https://github.com/GoogleContainerTools/jib/issues/1513))

### Fixed

- Fixed an issue where setting `<allowInsecureRegistries>` may fail to try HTTP. ([#1517](https://github.com/GoogleContainerTools/jib/issues/1517))
- Crash on talking to servers that do not set the `Content-Length` HTTP header or send an incorrect value. ([#1512](https://github.com/GoogleContainerTools/jib/issues/1512))

## 1.0.1

### Added

- Java 9+ projects are now supported and run on the distroless Java 11 image (https://github.com/GoogleContainerTools/distroless) by default. Java 8 projects remain on the distroless Java 8 image. ([#1279](https://github.com/GoogleContainerTools/jib/issues/1279))

### Fixed

- Failure to infer main class when main method is defined using varargs (i.e. `public static void main(String... args)`) ([#1456](https://github.com/GoogleContainerTools/jib/issues/1456))

## 1.0.0

### Changed

- Shortened progress bar display - make sure console window is at least 50 characters wide or progress bar display can be messy ([#1361](https://github.com/GoogleContainerTools/jib/issues/1361))

## 1.0.0-rc2

### Added

- Setting proxy credentials (via system properties `http(s).proxyUser` and `http(s).proxyPassword`) is now supported.
- Maven proxy settings are now supported.
- Now checks for system properties in pom as well as commandline. ([#1201](https://github.com/GoogleContainerTools/jib/issues/1201))
- `<dockerClient><executable>` and `<dockerClient><environment>` to set Docker client binary path (defaulting to `docker`) and additional environment variables to apply when running the binary ([#468](https://github.com/GoogleContainerTools/jib/issues/468))

### Changed

- Java 9+ projects using the default distroless Java 8 base image will now fail to build. ([#1143](https://github.com/GoogleContainerTools/jib/issues/1143))

## 1.0.0-rc1

### Added

- `jib.baseImageCache` and `jib.applicationCache` system properties for setting cache directories ([#1238](https://github.com/GoogleContainerTools/jib/issues/1238))
- Build progress shown via a progress bar - set `-Djib.console=plain` to show progress as log messages ([#1297](https://github.com/GoogleContainerTools/jib/issues/1297))

### Changed

- `gwt-app` packaging type now builds a WAR container.
- When building to Docker and no `<to><image>` is defined, artifact ID is used as an image reference instead of project name.
- Removed `<useOnlyProjectCache>` parameter in favor of the `jib.useOnlyProjectCache` system property ([#1308](https://github.com/GoogleContainerTools/jib/issues/1308))

### Fixed

- Builds failing due to dependency JARs with the same name ([#810](https://github.com/GoogleContainerTools/jib/issues/810))

## 0.10.1

### Added

- Image ID is now written to `target/jib-image.id` ([#1204](https://github.com/GoogleContainerTools/jib/issues/1204))
- `<container><entrypoint>INHERIT</entrypoint></container>` allows inheriting `ENTRYPOINT` and `CMD` from the base image. While inheriting `ENTRYPOINT`, you can also override `CMD` using `<container><args>`.
- `<container><workingDirectory>` configuration parameter to set the working directory ([#1225](https://github.com/GoogleContainerTools/jib/issues/1225))
- Adds support for configuring volumes ([#1121](https://github.com/GoogleContainerTools/jib/issues/1121))
- Exposed ports are now propagated from the base image ([#595](https://github.com/GoogleContainerTools/jib/issues/595))
- Docker health check is now propagated from the base image ([#595](https://github.com/GoogleContainerTools/jib/issues/595))

### Changed

- Removed `jib:exportDockerContext` goal ([#1219](https://github.com/GoogleContainerTools/jib/issues/1219))

### Fixed

- NullPointerException thrown with incomplete `auth` configuration ([#1177](https://github.com/GoogleContainerTools/jib/issues/1177))

## 0.10.0

### Added

- Properties for each configuration parameter, allowing any parameter to be set via commandline ([#728](https://github.com/GoogleContainerTools/jib/issues/728))
- `<to><credHelper>` and `<from><credHelper>` can be used to specify a credential helper suffix or a full path to a credential helper executable ([#925](https://github.com/GoogleContainerTools/jib/issues/925))
- `<container><user>` configuration parameter to configure the user and group to run the container as ([#1029](https://github.com/GoogleContainerTools/jib/issues/1029))
- Preliminary support for building images for WAR projects ([#431](https://github.com/GoogleContainerTools/jib/issues/431))
- `<extraDirectory>` object with a `<path>` and `<permissions>` field ([#794](https://github.com/GoogleContainerTools/jib/issues/794))
  - `<extraDirectory><path>` configures the extra layer directory (still also configurable via `<extraDirectory>...</extraDirectory>`)
  - `<extraDirectory><permissions>` is a list of `<permission>` objects, each with a `<file>` and `<mode>` field, used to map a file on the container to the file's permission bits (represented as an octal string)
- Image digest is now written to `target/jib-image.digest` ([#1155](https://github.com/GoogleContainerTools/jib/pull/1155))
- Adds the layer type to the layer history as comments ([#1198](https://github.com/GoogleContainerTools/jib/issues/1198))

### Changed

- Removed deprecated `<jvmFlags>`, `<mainClass>`, `<args>`, and `<format>` in favor of the equivalents under `<container>` ([#461](https://github.com/GoogleContainerTools/jib/issues/461))
- `jib:exportDockerContext` generates different directory layout and `Dockerfile` to enable WAR support ([#1007](https://github.com/GoogleContainerTools/jib/pull/1007))
- File timestamps in the built image are set to 1 second since the epoch (hence 1970-01-01T00:00:01Z) to resolve compatibility with applications on Java 6 or below where the epoch means nonexistent or I/O errors; previously they were set to the epoch ([#1079](https://github.com/GoogleContainerTools/jib/issues/1079))

## 0.9.13

### Fixed

- Adds environment variable configuration to Docker context generator ([#890 (comment)](https://github.com/GoogleContainerTools/jib/issues/890#issuecomment-430227555))

## 0.9.11

### Added

- `<skip>` configuration parameter to skip Jib execution in multi-module projects (also settable via `jib.skip` property) ([#865](https://github.com/GoogleContainerTools/jib/issues/865))
- `<container><environment>` configuration parameter to configure environment variables ([#890](https://github.com/GoogleContainerTools/jib/issues/890))
- `container.appRoot` configuration parameter to configure app root in the image ([#984](https://github.com/GoogleContainerTools/jib/pull/984))
- `<to><tags>` (list) defines additional tags to push to ([#1026](https://github.com/GoogleContainerTools/jib/pull/1026))

### Fixed

- Keep duplicate layers to match container history ([#1017](https://github.com/GoogleContainerTools/jib/pull/1017))

## 0.9.10

### Added

- `<container><labels>` configuration parameter for configuring labels ([#751](https://github.com/GoogleContainerTools/jib/issues/751))
- `<container><entrypoint>` configuration parameter to set the entrypoint ([#579](https://github.com/GoogleContainerTools/jib/issues/579))
- `history` to layer metadata ([#875](https://github.com/GoogleContainerTools/jib/issues/875))
- Propagates working directory from the base image ([#902](https://github.com/GoogleContainerTools/jib/pull/902))

### Fixed

- Corrects permissions for directories in the container filesystem ([#772](https://github.com/GoogleContainerTools/jib/pull/772))

## 0.9.9

### Added

- Passthrough labels from base image ([#750](https://github.com/GoogleContainerTools/jib/pull/750/files))

### Changed

- Reordered classpath in entrypoint to allow dependency patching ([#777](https://github.com/GoogleContainerTools/jib/issues/777))

### Fixed

## 0.9.8

### Added

- `<to><auth>` and `<from><auth>` parameters with `<username>` and `<password>` fields for simple authentication, similar to the Gradle plugin ([#693](https://github.com/GoogleContainerTools/jib/issues/693))
- Can set credentials via commandline using `jib.to.auth.username`, `jib.to.auth.password`, `jib.from.auth.username`, and `jib.from.auth.password` system properties ([#693](https://github.com/GoogleContainerTools/jib/issues/693))
- Docker context generation now includes snapshot dependencies and extra files ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Disable parallel operation by setting the `jibSerialize` system property to `true` ([#682](https://github.com/GoogleContainerTools/jib/pull/682))

### Changed

- Propagates environment variables from the base image ([#716](https://github.com/GoogleContainerTools/jib/pull/716))
- Skips execution if packaging is `pom` ([#735](https://github.com/GoogleContainerTools/jib/pull/735))
- `allowInsecureRegistries` allows connecting to insecure HTTPS registries (for example, registries using self-signed certificates) ([#733](https://github.com/GoogleContainerTools/jib/pull/733))

### Fixed

- Slow image reference parsing ([#680](https://github.com/GoogleContainerTools/jib/pull/680))
- Building empty layers ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Duplicate layer entries causing unbounded cache growth ([#721](https://github.com/GoogleContainerTools/jib/issues/721))
- Incorrect authentication error message when target and base registry are the same ([#758](https://github.com/GoogleContainerTools/jib/issues/758))

## 0.9.7

### Added

- Snapshot dependencies are added as their own layer ([#584](https://github.com/GoogleContainerTools/jib/pull/584))
- `jib:buildTar` goal to build an image tarball at `target/jib-image.tar`, which can be loaded into docker using `docker load` ([#514](https://github.com/GoogleContainerTools/jib/issues/514))
- `<container><useCurrentTimestamp>` parameter to set the image creation time to the build time ([#413](https://github.com/GoogleContainerTools/jib/issues/413))
- Authentication over HTTP using the `sendCredentialsOverHttp` system property ([#599](https://github.com/GoogleContainerTools/jib/issues/599))
- HTTP connection and read timeouts for registry interactions configurable with the `jib.httpTimeout` system property ([#656](https://github.com/GoogleContainerTools/jib/pull/656))

### Changed

- Docker context export parameter `-Djib.dockerDir` to `-DjibTargetDir` ([#662](https://github.com/GoogleContainerTools/jib/issues/662))

### Fixed

- Using multi-byte characters in container configuration ([#626](https://github.com/GoogleContainerTools/jib/issues/626))
- For Docker Hub, also tries registry aliases when getting a credential from the Docker config ([#605](https://github.com/GoogleContainerTools/jib/pull/605))
- Decrypting credentials from Maven settings ([#592](https://github.com/GoogleContainerTools/jib/issues/592))

## 0.9.6

### Fixed

- Using a private registry that does token authentication with `allowInsecureRegistries` set to `true` ([#572](https://github.com/GoogleContainerTools/jib/pull/572))

## 0.9.5

### Added

- Incubating feature to build `src/main/jib` as extra layer in image ([#565](https://github.com/GoogleContainerTools/jib/pull/565))

## 0.9.4

### Fixed

- Fixed handling case-insensitive `Basic` authentication method ([#546](https://github.com/GoogleContainerTools/jib/pull/546))
- Fixed regression that broke pulling base images from registries that required token authentication ([#549](https://github.com/GoogleContainerTools/jib/pull/549))

## 0.9.3

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
