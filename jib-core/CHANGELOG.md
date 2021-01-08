# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

### Fixed

- Updated jackson dependency version causing compatibility issues. ([#2931](https://github.com/GoogleContainerTools/jib/issues/2931))
- Fixed `NullPointerException` when pulling an OCI base image whose manifest does not have `mediaType` information. ([#2819](https://github.com/GoogleContainerTools/jib/issues/2819))
- Fixed build failure when using a Docker daemon base image (`docker://...`) that has duplicate layers. ([#2829](https://github.com/GoogleContainerTools/jib/issues/2829))

## 0.16.0

### Added

- Allow setting platform when building image from scratch. ([#2765](https://github.com/GoogleContainerTools/jib/issues/2765))
- New system property `jib.skipExistingImages` (false by default) to skip pushing images (manifests) if the image already exists in the registry. ([#2360](https://github.com/GoogleContainerTools/jib/issues/2360))
- _Incubating feature_: can now configure multiple platforms (such as architectures) to build multiple images as a bundle and push as a manifest list (also known as a fat manifest). As an incubating feature, there are certain limitations. For example, OCI image indices are not supported, and building a manifest list is supported only for registry pushing (using `RegistryImage`). ([#2523](https://github.com/GoogleContainerTools/jib/issues/2523), [#1567](https://github.com/GoogleContainerTools/jib/issues/1567))

### Changed

- Upgraded jib-build-plan to 0.4.0. ([#2660](https://github.com/GoogleContainerTools/jib/pull/2660))
- Previous locally cached base image manifests will be ignored, as the caching mechanism changed to enable multi-platform image building. ([#2730](https://github.com/GoogleContainerTools/jib/pull/2730), [#2711](https://github.com/GoogleContainerTools/jib/pull/2711))
- Upgraded the ASM library to 9.0 to resolve an issue when auto-inferring main class in Java 15+. ([#2776](https://github.com/GoogleContainerTools/jib/pull/2776))

### Fixed

- Fixed `NullPointerException` when the `"auths":` section in `~/.docker/config.json` has an entry with no `"auth":` field. ([#2535](https://github.com/GoogleContainerTools/jib/issues/2535))
- Fixed `NullPointerException` to return a helpful message when a server does not provide any message in certain error cases (400 Bad Request, 404 Not Found, and 405 Method Not Allowed). ([#2532](https://github.com/GoogleContainerTools/jib/issues/2532))
- Now supports sending client certificate (for example, via the `javax.net.ssl.keyStore` and `javax.net.ssl.keyStorePassword` system properties) and thus enabling mutual TLS authentication. ([#2585](https://github.com/GoogleContainerTools/jib/issues/2585), [#2226](https://github.com/GoogleContainerTools/jib/issues/2226))
- Fixed `NullPointerException` during input validation (in Java 9+) when configuring Jib parameters using certain immutable collections (such as `List.of()`). ([#2702](https://github.com/GoogleContainerTools/jib/issues/2702))
- Fixed authentication failure with Azure Container Registry when using ["tokens"](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-repository-scoped-permissions). ([#2784](https://github.com/GoogleContainerTools/jib/issues/2784))
- Improved authentication flow for base image registry. ([#2134](https://github.com/GoogleContainerTools/jib/issues/2134))

## 0.15.0

### Added

- Now sets configured file ownership when creating layer tars. ([#2499](https://github.com/GoogleContainerTools/jib/pull/2499))

### Changed

- `Ports.parse(List<String> ports)` now returns a `Set`(as a `HashSet`) instead of `ImmutableSet` ([#2513](https://github.com/GoogleContainerTools/jib/pull/2513))
- Previous locally cached application layers will be ignored because of changes to the caching selectors. ([#2499](https://github.com/GoogleContainerTools/jib/pull/2499))

### Fixed

- Fixed authentication failure with Azure Container Registry when using an identity token defined in the `auths` section of Docker config (`~/.docker/config.json`). ([#2488](https://github.com/GoogleContainerTools/jib/pull/2488))
- Now adding the Jib Core dependency transitively exposes the Build Plan API. ([#2507](https://github.com/GoogleContainerTools/jib/issues/2507))

## 0.14.0

### Added

- Multiple additions to `ImageReference` to separate `tag` and `digest`. ([#1481](https://github.com/GoogleContainerTools/jib/issues/1481))
    - `of(registry, repository, tag, digest)` to create an image from a tag and digest.
    - `isValidDigest(digest)` to check if a string is a valid digest.
    - `getDigest()` to get the digest.
    - `parse()` now supports image reference strings containing both a tag and a digest.
    - `getQualifier()` to return the digest, or the tag if no digest is set.
    - `withQualifier()` to change the image's tag or digest (old behavior of `withTag()`)
- New public API package hierarchy `com.google.cloud.tools.jib.api.buildplan` for [Container Build Plan Specification](https://github.com/GoogleContainerTools/jib/blob/2b2fe82ad0552ba3ce1de308a0fda24aab684365/proposals/container-build-plan-spec.md) and API.
- `ContainerBuildPlan`, `FileEntriesLayer`, `FileEntry`, and `LayerObject` in `com.google.cloud.tools.jib.api.buildplan` as part of the Container Build Plan API. ([#2338](https://github.com/GoogleContainerTools/jib/pull/2338), [#2328](https://github.com/GoogleContainerTools/jib/pull/2328))

### Changed

- `ImageReference#toStringWithTag` has been renamed to `toStringWithQualifier`.
- `ImageReference#isValidTag` no longer returns `true` for digests.
- `ImageReference#isTagDigest` has been removed; use `#getDigest` with `Optional#isPresent()` to check if an `ImageReference` uses a digest.
- `ImageReference#withTag` has been removed; use `withQualifier()` instead.
- `ImageReference#isDefaultTag` and `usesDefaultTag` no longer return `true` for `null` or empty tags.
- The following classes in `com.google.cloud.tools.jib.api` has been moved to `com.google.cloud.tools.jib.api.buildplan`: `AbsoluteUnixPath`, `RelativeUnixPath`, `Port`, `FilePermissions`, and `ImageFormat`. ([#2328](https://github.com/GoogleContainerTools/jib/pull/2328))
- `LayerConfiguration` and `LayerEntry` are deprecated. Use `FileEntriesLayer` and `FileEntry` in `com.google.cloud.tools.jib.api.buildplan`. ([#2334](https://github.com/GoogleContainerTools/jib/pull/2334))

### Fixed

- Fixed the problem not inheriting `USER` container configuration from a base image. ([#2421](https://github.com/GoogleContainerTools/jib/pull/2421))
- Fixed wrong capitalization of JSON properties in a loadable Docker manifest when building a tar image. ([#2430](https://github.com/GoogleContainerTools/jib/issues/2430))
- Fixed an issue when using a base image whose image creation timestamp contains timezone offset. ([#2428](https://github.com/GoogleContainerTools/jib/issues/2428))

## 0.13.1

### Fixed

- Fixed authentication failure with error `server did not return 'WWW-Authenticate: Bearer' header` in certain cases (for example, on OpenShift). ([#2258](https://github.com/GoogleContainerTools/jib/issues/2258))
- Fixed an issue where using local Docker images (by `docker://...`) on Windows caused an error. ([#2270](https://github.com/GoogleContainerTools/jib/issues/2270))

## 0.13.0

### Added

- New method: `JibContainerBuilder#describeContainer` which returns new class: `JibContainerDescription`, containing a selection of information used for the Jib build. ([#2115](https://github.com/GoogleContainerTools/jib/issues/2115))

### Changed

- Each local base image layer is pushed immediately after being compressed, rather than waiting for all layers to finish compressing before starting to push. ([#1913](https://github.com/GoogleContainerTools/jib/issues/1913))
- HTTP redirection URLs are no longer sanitized in order to work around an issue with certain registries that do not conform to HTTP standards. This resolves an issue with using Red Hat OpenShift and Quay registries. ([#2106](https://github.com/GoogleContainerTools/jib/issues/2106), [#1986](https://github.com/GoogleContainerTools/jib/issues/1986#issuecomment-547610104))
- `Containerizer.DEFAULT_BASE_CACHE_DIRECTORY` has been changed on MacOS and Windows. ([#2216](https://github.com/GoogleContainerTools/jib/issues/2216))
    - MacOS (`$XDG_CACHE_HOME` defined): from `$XDG_CACHE_HOME/google-cloud-tools-java/jib/` to `$XDG_CACHE_HOME/Google/Jib/`
    - MacOS (`$XDG_CACHE_HOME` not defined): from `$HOME/Library/Application Support/google-cloud-tools-java/jib/` to `$HOME/Library/Caches/Google/Jib/`
    - Windows (`$XDG_CACHE_HOME` defined): from `$XDG_CACHE_HOME\google-cloud-tools-java\jib\` to `$XDG_CACHE_HOME\Google\Jib\Cache\`
    - Windows (`$XDG_CACHE_HOME` not defined): from `%LOCALAPPDATA%\google-cloud-tools-java\jib\` to `%LOCALAPPDATA%\Google\Jib\Cache\`

### Fixed

- `Containerizer#setAllowInsecureRegistries(boolean)` and the `sendCredentialsOverHttp` system property are now effective for authentication service server connections. ([#2074](https://github.com/GoogleContainerTools/jib/pull/2074))
- Fixed inefficient communications when interacting with insecure registries and servers (when `Containerizer#setAllowInsecureRegistries(boolean)` is set). ([#946](https://github.com/GoogleContainerTools/jib/issues/946))
- Building a tarball with `OCI` format now builds a correctly formatted OCI archive. ([#2124](https://github.com/GoogleContainerTools/jib/issues/2124))
- Now automatically refreshes Docker registry authentication tokens when expired, fixing the issue that long-running builds may fail with "401 unauthorized." ([#691](https://github.com/GoogleContainerTools/jib/issues/691))

## 0.12.0

### Added

- Main class inference support for Java 13/14. ([#2015](https://github.com/GoogleContainerTools/jib/issues/2015))
- `Containerizer#setAlwaysCacheBaseImage(boolean)` controls the optimization to skip downloading base image layers that exist in a target registry. ([#1870](https://github.com/GoogleContainerTools/jib/pull/1870))

### Changed

- Local base image layers are now processed in parallel, speeding up builds using large local base images. ([#1913](https://github.com/GoogleContainerTools/jib/issues/1913))
- The base image manifest is no longer pulled from the registry if a digest is provided and the manifest is already cached. ([#1881](https://github.com/GoogleContainerTools/jib/issues/1881))
- Docker daemon base images are now cached more effectively, speeding up builds using `DockerDaemonImage` base images. ([#1912](https://github.com/GoogleContainerTools/jib/issues/1912))
- Now ignores `jib.alwaysCacheBaseImage` system property. Use `Containerizer#setAlwaysCacheBaseImage(boolean)` instead. ([#1870](https://github.com/GoogleContainerTools/jib/pull/1870))

### Fixed

- Fixed temporary directory cleanup during builds using local base images. ([#2016](https://github.com/GoogleContainerTools/jib/issues/2016))
- Fixed additional tags being ignored when building to a tarball. ([#2043](https://github.com/GoogleContainerTools/jib/issues/2043))
- Fixed `TarImage` base image failing if tar does not contain explicit directory entries. ([#2067](https://github.com/GoogleContainerTools/jib/issues/2067))

## 0.11.0

### Added

- `Jib#from` and `JavaContainerBuilder#from` overloads to allow using a `DockerDaemonImage` or a `TarImage` as the base image. ([#1468](https://github.com/GoogleContainerTools/jib/issues/1468), [#1905](https://github.com/GoogleContainerTools/jib/issues/1905))
- `Jib#from(String)` accepts strings prefixed with `docker://`, `tar://`, or `registry://` to specify image type.

### Changed

- To disable parallel execution, the property `jib.serialize` should be used instead of `jibSerialize`. ([#1968](https://github.com/GoogleContainerTools/jib/issues/1968))
- `TarImage` is constructed using `TarImage.at(...).named(...)` instead of `TarImage.named(...).saveTo(...)`. ([#1918](https://github.com/GoogleContainerTools/jib/issues/1918))
- For retrieving credentials from Docker config (`~/.docker/config.json`), `credHelpers` now takes precedence over `credsStore`, followed by `auths`. ([#1958](https://github.com/GoogleContainerTools/jib/pull/1958))
- The legacy `credsStore` no longer requires defining empty registry entries in `auths` to be used. This now means that if `credsStore` is defined, `auths` will be completely ignored. ([#1958](https://github.com/GoogleContainerTools/jib/pull/1958))

### Fixed

- Fixed an issue interacting with certain registries due to changes to URL handling in the underlying Apache HttpClient library. ([#1924](https://github.com/GoogleContainerTools/jib/issues/1924))
- Fixed the regression of slow network operations introduced at 0.10.1. ([#1980](https://github.com/GoogleContainerTools/jib/pull/1980))
- Fixed an issue where connection timeout sometimes fell back to attempting plain HTTP (non-HTTPS) requests when the `Containerizer` is set to allow insecure registries. ([#1949](https://github.com/GoogleContainerTools/jib/pull/1949))

## 0.10.1

### Added

- `JavaContainerBuilder#setLastModifiedTimeProvider` to set file timestamps. ([#1818](https://github.com/GoogleContainerTools/jib/pull/1818))

### Changed

- `JibContainerBuilder#addDependencies` is now split into three methods: `addDependencies`, `addSnapshotDependencies`, `addProjectDependencies`. ([#1773](https://github.com/GoogleContainerTools/jib/pull/1773))
- For building and pushing to a registry, Jib now skips downloading and caching base image layers if the layers already exist in the target registry. This feature will be particularly useful in CI/CD environments. However, if you want to force caching base image layers locally, set the system property `-Djib.alwaysCacheBaseImage=true`. ([#1840](https://github.com/GoogleContainerTools/jib/pull/1840))

### Fixed

- Manifest lists referenced directly by sha256 are automatically parsed and the first `linux/amd64` manifest is used. ([#1811](https://github.com/GoogleContainerTools/jib/issues/1811))

## 0.10.0

### Added

- `Containerizer#addEventHandler` for adding event handlers.

### Changed

- Multiple classes have been moved to the `com.google.cloud.tools.jib.api` package.
- Event handlers are now added directly to the `Containerizer` rather than adding them to an `EventHandlers` object first.
- Removed multiple classes to simplify the event system (`JibEventType`, `BuildStepType`, `EventDispatcher`, `DefaultEventDispatcher`, `LayerCountEvent`)
- MainClassFinder now uses a static method instead of requiring instantiation.

## 0.9.2

### Added

- Container configurations in the base image are now propagated when registry uses the old V2 image manifest, schema version 1 (such as Quay). ([#1641](https://github.com/GoogleContainerTools/jib/issues/1641))
- `Containerizer#setOfflineMode` to retrieve the base image from Jib's cache rather than a container registry. ([#718](https://github.com/GoogleContainerTools/jib/issues/718))

### Fixed

- Labels in the base image are now propagated. ([#1643](https://github.com/GoogleContainerTools/jib/issues/1643))
- Fixed an issue with using OCI base images. ([#1683](https://github.com/GoogleContainerTools/jib/issues/1683))

## 0.9.1

### Added

- Overloads for `LayerConfiguration#addEntryRecursive` that take providers to set file permissions and file modification time on a per-file basis. ([#1607](https://github.com/GoogleContainerTools/jib/issues/1607))

### Changed

- `LayerConfiguration` takes file modification time as an `Instant` instead of a `long`.

### Fixed

- Fixed an issue where automatically generated parent directories in a layer did not get their timestamp configured correctly to epoch + 1s. ([#1648](https://github.com/GoogleContainerTools/jib/issues/1648))
- Fixed an issue where the library creates wrong images by adding base image layers in reverse order when registry uses the old V2 image manifest, schema version 1 (such as Quay). ([#1627](https://github.com/GoogleContainerTools/jib/issues/1627))

## 0.9.0

### Added

- `JavaContainerBuilder#setAppRoot()` and `JavaContainerBuilder#fromDistrolessJetty()` for building WAR containers. ([#1464](https://github.com/GoogleContainerTools/jib/issues/1464))
- `Jib#fromScratch()` to start building from an empty base image. ([#1471](https://github.com/GoogleContainerTools/jib/issues/1471))
- Methods in `JavaContainerBuilder` for setting the destination directories for classes, resources, directories, and additional classpath files.

### Changed

- Allow skipping `JavaContainerBuilder#setMainClass()` to skip setting the entrypoint.
- `os` and `architecture` are taken from base image. ([#1564](https://github.com/GoogleContainerTools/jib/pull/1564))

### Fixed

- `ImageReference` assumes `registry-1.docker.io` as the registry if the host part of an image reference is `docker.io`. ([#1549](https://github.com/GoogleContainerTools/jib/issues/1549))

## 0.1.2

### Added

- `ProgressEvent#getBuildStepType` method to get which step in the build process a progress event corresponds to. ([#1449](https://github.com/GoogleContainerTools/jib/pull/1449))
- `LayerCountEvent` that is dispatched at the beginning of certain pull/build/push build steps to indicate the number of layers being processed. ([#1461](https://github.com/GoogleContainerTools/jib/pull/1461))

### Changed

- `JibContainerBuilder#containerize()` throws multiple sub-types of `RegistryException` rather than wrapping them in an `ExecutionException`. ([#1440](https://github.com/GoogleContainerTools/jib/issues/1440))

### Fixed

- `MainClassFinder` failure when main method is defined using varargs (i.e. `public static void main(String... args)`). ([#1456](https://github.com/GoogleContainerTools/jib/issues/1456))

## 0.1.1

### Added

- Adds support for configuring volumes. ([#1121](https://github.com/GoogleContainerTools/jib/issues/1121))
- Adds `JavaContainerBuilder` for building opinionated containers for Java applications. ([#1212](https://github.com/GoogleContainerTools/jib/issues/1212))
