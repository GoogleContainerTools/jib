# Change Log
All notable changes to this project will be documented in this file.

## [unreleased]

### Added

### Changed

### Fixed

## 3.4.3

### Fixed
- fix: When building to the local docker daemon with multiple platforms configured, Jib will now automatically select the image that matches the OS type and architecture of the local Docker environment. ([#4249](https://github.com/GoogleContainerTools/jib/pull/4249))

## 3.4.2

### Changed
- deps: bump org.apache.commons:commons-compress from 1.21 to 1.26.0 ([#4204](https://github.com/GoogleContainerTools/jib/pull/4204))

### Fixed
- fix: set PAX headers to address build reproducibility issue ([#4204](https://github.com/GoogleContainerTools/jib/pull/4204))
- fix: (WAR Containerization) modify default entrypoint to `java -jar /usr/local/jetty/start.jar --module=ee10-deploy` for Jetty 12+ compatibility ([#4216](https://github.com/GoogleContainerTools/jib/pull/4216))

## 3.4.1

### Fixed
- fix: support parsing manifest JSON containing `LayerSources:` from latest Docker. ([#4171](https://github.com/GoogleContainerTools/jib/pull/4171))

## 3.4.0

### Changed
- deps: bump org.apache.maven:maven-compat from 3.9.1 to 3.9.2. ([#4017](https://github.com/GoogleContainerTools/jib/pull/4017/))
- deps: bump com.github.luben:zstd-jni from 1.5.5-2 to 1.5.5-4. ([#4049](https://github.com/GoogleContainerTools/jib/pull/4049/))
- deps: bump com.fasterxml.jackson:jackson-bom from 2.15.0 to 2.15.2. ([#4055](https://github.com/GoogleContainerTools/jib/pull/4055))
- deps: bump com.google.guava:guava from 32.0.1-jre to 32.1.2-jre ([#4078](https://github.com/GoogleContainerTools/jib/pull/4078))
- deps: bump org.slf4j:slf4j-simple from 2.0.7 to 2.0.9. ([#4098](https://github.com/GoogleContainerTools/jib/pull/4098))

### Fixed
- fix: fix WWW-Authenticate header parsing for Basic authentication ([#4035](https://github.com/GoogleContainerTools/jib/pull/4035/))

## 3.3.2

### Changed
- Log an info instead of warning when entrypoint makes the image to ignore jvm parameters ([#3904](https://github.com/GoogleContainerTools/jib/pull/3904))

Thanks to our community contributors @rmannibucau!

## 3.3.1

### Changed
- Upgraded Google HTTP libraries to 1.42.2 ([#3745](https://github.com/GoogleContainerTools/jib/pull/3745))

## 3.3.0

### Added

- Included `imagePushed` field to image metadata json output file which provides information on whether an image was pushed by Jib. Note that the output file is `build/jib-image.json` by default or configurable with `jib.outputPaths.imageJson`. ([#3641](https://github.com/GoogleContainerTools/jib/pull/3641))
- Better error messaging when environment map in `container.environment` contains null values ([#3672](https://github.com/GoogleContainerTools/jib/pull/3672)).
- Support for OCI image index manifests ([#3715](https://github.com/GoogleContainerTools/jib/pull/3715)).
- Support for base image layer compressed with zstd ([#3717](https://github.com/GoogleContainerTools/jib/pull/3717)).

### Changed

- Upgraded slf4j-simple and slf4j-api to 2.0.0 ([#3734](https://github.com/GoogleContainerTools/jib/pull/3734), [#3735](https://github.com/GoogleContainerTools/jib/pull/3735)).
- Upgraded nullaway to 0.9.9. ([#3720](https://github.com/GoogleContainerTools/jib/pull/3720))
- Jib now only checks for file existence instead of running the executable passed into `dockerClient.executable` for the purpose of verifying if docker is installed correctly. Users are responsible for ensuring that the docker executable specified through this property is valid and has the correct permissions ([#3744](https://github.com/GoogleContainerTools/jib/pull/3744)).
- Jib now throws an exception when the base image doesn't support target platforms during multi-platform build ([#3707](https://github.com/GoogleContainerTools/jib/pull/3707)).

Thanks to our community contributors @wwadge, @oliver-brm, @rquinio and @gsquared94!

## 3.2.1

### Added

- Environment variables can now be used in configuring credential helpers. ([#2814](https://github.com/GoogleContainerTools/jib/issues/2814))
  ```xml
  <to>
      <image>myimage</image>
      <credHelper>
          <helper>ecr-login</helper>
          <environment>
              <AWS_PROFILE>profile</AWS_PROFILE>
          </environment>
      </credHelper>
  </to>
  ```

### Changed

- Upgraded jackson-databind to 2.13.2.2 ([#3612](https://github.com/GoogleContainerTools/jib/pull/3612)).

## 3.2.0

### Added

- [`<from><platforms>`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#from-object) parameter for multi-architecture image building can now be configured through Maven and system properties (for example, `-Djib.from.platforms=linux/amd64,linux/arm64` on the command-line). ([#2742](https://github.com/GoogleContainerTools/jib/pull/2742))
- For retrieving credentials, Jib additionally looks for `$XDG_RUNTIME_DIR/containers/auth.json`, `$XDG_CONFIG_HOME/containers/auth.json`, and `$HOME/.config/containers/auth.json`. ([#3524](https://github.com/GoogleContainerTools/jib/issues/3524))


### Changed

- Changed the default base image of the Jib CLI `jar` command from the `adoptopenjdk` images to the [`eclipse-temurin`](https://hub.docker.com/_/eclipse-temurin) on Docker Hub. Note that Temurin (by Adoptium) is the new name of AdoptOpenJDK. ([#3483](https://github.com/GoogleContainerTools/jib/issues/3483))
- Build will fail if `<extraDirectories><paths>` contain `from` directory that doesn't exist locally ([#3542](https://github.com/GoogleContainerTools/jib/issues/3542))

### Fixed

- Fixed incorrect parsing with comma escaping when providing Jib list or map property values on the command-line. ([#2224](https://github.com/GoogleContainerTools/jib/issues/2224))

## 3.1.4

### Changed

- Downgraded Google HTTP libraries to 1.34.0 to resolve network issues. ([#3415](https://github.com/GoogleContainerTools/jib/pull/3415), [#3058](https://github.com/GoogleContainerTools/jib/issues/3058), [#3409](https://github.com/GoogleContainerTools/jib/issues/3409))
- If `allowInsecureRegistries=true`, HTTP requests are retried on I/O errors only after insecure failover is finalized for each server. ([#3422](https://github.com/GoogleContainerTools/jib/issues/3422))

## 3.1.3

### Added

- Increased robustness in registry communications by retrying HTTP requests (to the effect of retrying image pushes or pulls) on I/O exceptions with exponential backoffs. ([#3351](https://github.com/GoogleContainerTools/jib/pull/3351))
- Now also supports `username` and `password` properties for the `auths` section in a Docker config (`~/.docker/config.json`). (Previously, only supported was a base64-encoded username and password string of the `auth` property.) ([#3365](https://github.com/GoogleContainerTools/jib/pull/3365))

### Changed

- Upgraded Google HTTP libraries to 1.39.2. ([#3387](https://github.com/GoogleContainerTools/jib/pull/3387))

## 3.1.2

### Fixed

- Fixed the bug introduced in 3.1 that constructs a wrong Java runtime classpath when two dependencies have the same artifact ID and version but different group IDs. The bug occurs only when using Java 9+ or setting `<container><expandClasspathDependencies>`. ([#3331](https://github.com/GoogleContainerTools/jib/pull/3331))

## 3.1.1

### Fixed

- Fixed the regression introduced in 3.1.0 where a build may fail due to an error from main class inference even if `<container><entrypoint>` is configured. ([#3295](https://github.com/GoogleContainerTools/jib/pull/3295))

## 3.1.0

### Added

- For Google Artifact Registry (`*-docker.pkg.dev`), Jib now tries [Google Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials) last like it has been doing for `gcr.io`. ([#3241](https://github.com/GoogleContainerTools/jib/pull/3241))

### Changed

- Jib now creates an additional layer that contains two small text files: [`/app/jib-classpath-file` and `/app/jib-main-class-file`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin/README.md#custom-container-entrypoint). They hold, respectively, the final Java runtime classpath and the main class computed by Jib that are suitable for app execution on JVM. For example, with Java 9+, setting the container entrypoint to `java --class-path @/app/jib-classpath-file @/app/jib-main-class-file` will work to start the app. (This is basically the default entrypoint set by Jib when the entrypoint is not explicitly configured by the user.) The files are always generated whether Java 8 or 9+, or whether `jib.container.entrypoint` is explicitly configured. The files can be helpful especially when setting a custom entrypoint for a shell script that needs to get the classpath and the main class computed by Jib, or for [AppCDS](https://github.com/GoogleContainerTools/jib/issues/2471). ([#3280](https://github.com/GoogleContainerTools/jib/pull/3280))
- For Java 9+ apps, the default Java runtime classpath explicitly lists all the app dependencies, preserving the dependency loading order declared by Maven. This is done by changing the default entrypoint to use the new classpath JVM argument file (basically `java -cp @/app/jib-classpath-file`). As such, `<container><expandClasspathDependencies>` takes no effect for Java 9+. ([#3280](https://github.com/GoogleContainerTools/jib/pull/3280))
- Timestamps of file entries in a tarball built with `jib:buildTar` are set to the epoch, making the tarball reproducible. ([#3158](https://github.com/GoogleContainerTools/jib/issues/3158))

## 3.0.0

### Added

- New `<includes>` and `<excludes>` options for `<extraDirectories>`. This enables copying a subset of files from the source directory using glob patterns. ([#2564](https://github.com/GoogleContainerTools/jib/issues/2564))
- [Jib extensions](https://github.com/GoogleContainerTools/jib-extensions) can be loaded via the [Maven dependency injection mechanism](https://maven.apache.org/maven-jsr330.html). This also enables injecting arbitrary dependencies (for example, Maven components) into an extension. ([#3036](https://github.com/GoogleContainerTools/jib/issues/3036))

### Changed

- [Switched the default base images](https://github.com/GoogleContainerTools/jib/blob/master/docs/default_base_image.md) from Distroless to [`adoptopenjdk:{8,11}-jre`](https://hub.docker.com/_/adoptopenjdk) and [`jetty`](https://hub.docker.com/_/jetty) (for WAR). ([#3124](https://github.com/GoogleContainerTools/jib/pull/3124))

### Fixed

- Fixed an issue where some log messages used color in the "plain" console output. ([#2764](https://github.com/GoogleContainerTools/jib/pull/2764))

## 2.8.0

### Added

- Added support for [configuring registry mirrors](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#i-am-hitting-docker-hub-rate-limits-how-can-i-configure-registry-mirrors) for base images. This is useful when hitting [Docker Hub rate limits](https://www.docker.com/increase-rate-limits). Only public mirrors (such as `mirror.gcr.io`) are supported. ([#3011](https://github.com/GoogleContainerTools/jib/issues/3011))

### Changed

- Build will fail if Jib cannot create or read the [global Jib configuration file](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#global-jib-configuration). ([#2996](https://github.com/GoogleContainerTools/jib/pull/2996))

## 2.7.1

### Fixed

- Updated jackson dependency version causing compatibility issues. ([#2931](https://github.com/GoogleContainerTools/jib/issues/2931))

## 2.7.0

### Changed

- Added an option `<container><expandClasspathDependencies>` to preserve the order of loading dependencies as configured in a project. The option enumerates dependency JARs instead of using a wildcard (`/app/libs/*`) in the Java runtime classpath for an image entrypoint. ([#1871](https://github.com/GoogleContainerTools/jib/issues/1871), [#1907](https://github.com/GoogleContainerTools/jib/issues/1907), [#2228](https://github.com/GoogleContainerTools/jib/issues/2228), [#2733](https://github.com/GoogleContainerTools/jib/issues/2733))
    - The option is also useful for AppCDS. ([#2471](https://github.com/GoogleContainerTools/jib/issues/2471))
    - Turning on the option may result in a very long classpath string, and the OS may not support passing such a long string to JVM.

### Fixed

- Fixed `NullPointerException` when pulling an OCI base image whose manifest does not have `mediaType` information. ([#2819](https://github.com/GoogleContainerTools/jib/issues/2819))
- Fixed build failure when using a Docker daemon base image (`docker://...`) that has duplicate layers. ([#2829](https://github.com/GoogleContainerTools/jib/issues/2829))

## 2.6.0

### Added

### Changed

- Previous locally cached base image manifests will be ignored, as the caching mechanism changed to enable multi-platform image building. ([#2730](https://github.com/GoogleContainerTools/jib/pull/2730), [#2711](https://github.com/GoogleContainerTools/jib/pull/2711))
- Upgraded the ASM library to 9.0 to resolve an issue when auto-inferring main class in Java 15+. ([#2776](https://github.com/GoogleContainerTools/jib/pull/2776))
- _Incubating feature_: can now configure multiple platforms (such as architectures) to build multiple images as a bundle and push as a manifest list (also known as a fat manifest). As an incubating feature, there are certain limitations. For example, OCI image indices are not supported, and building a manifest list is supported only for registry pushing (the `jib:build` goal). ([#2523](https://github.com/GoogleContainerTools/jib/issues/2523))
    ```xml
      <from>
        <image>... image reference to a manifest list ...</image>
        <platforms>
          <platform>
            <architecture>arm64</architecture>
            <os>linux</os>
          </platform>
        </platforms>
      </from>
   ```

### Fixed

- Fixed authentication failure with Azure Container Registry when using ["tokens"](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-repository-scoped-permissions). ([#2784](https://github.com/GoogleContainerTools/jib/issues/2784))
- Improved authentication flow for base image registry. ([#2134](https://github.com/GoogleContainerTools/jib/issues/2134))

## 2.5.2

### Fixed

- Fixed the regression introduced in 2.5.1 that caused Jib to containerize a Spring Boot fat JAR instead of a normal thin JAR when `<containerizingMode>packaged` is set and the Spring Boot Maven plugin does not have a `<configuration>` block. ([#2693](https://github.com/GoogleContainerTools/jib/pull/2693))

## 2.5.1

### Fixed

- Fixed `NullPointerException` when `<containerizingMode>packaged` is set the Spring Boot Maven plugin does not have a `<configuration>` block. ([#2687](https://github.com/GoogleContainerTools/jib/issues/2687))

## 2.5.0

### Added

- Also tries `.exe` file extension for credential helpers on Windows. ([#2527](https://github.com/GoogleContainerTools/jib/issues/2527))
- New system property `jib.skipExistingImages` (false by default) to skip pushing images (manifests) if the image already exists in the registry. ([#2360](https://github.com/GoogleContainerTools/jib/issues/2360))
- _Incubating feature_: can now configure desired platform (architecture and OS) to select the matching manifest from a Docker manifest list for a base image. Currently supports building only one image. OCI image indices are not supported. ([#1567](https://github.com/GoogleContainerTools/jib/issues/1567))
    ```xml
      <from>
        <image>... image reference to a manifest list ...</image>
        <platforms>
          <platform>
            <architecture>arm64</architecture>
            <os>linux</os>
          </platform>
        </platforms>
      </from>
    ```

### Fixed

- Fixed reporting a wrong credential helper name when the helper does not exist on Windows. ([#2527](https://github.com/GoogleContainerTools/jib/issues/2527))
- Fixed `NullPointerException` when the `"auths":` section in `~/.docker/config.json` has an entry with no `"auth":` field. ([#2535](https://github.com/GoogleContainerTools/jib/issues/2535))
- Fixed `NullPointerException` to return a helpful message when a server does not provide any message in certain error cases (400 Bad Request, 404 Not Found, and 405 Method Not Allowed). ([#2532](https://github.com/GoogleContainerTools/jib/issues/2532))
- Now supports sending client certificate (for example, via the `javax.net.ssl.keyStore` and `javax.net.ssl.keyStorePassword` system properties) and thus enabling mutual TLS authentication. ([#2585](https://github.com/GoogleContainerTools/jib/issues/2585), [#2226](https://github.com/GoogleContainerTools/jib/issues/2226))
- Fixed build failure with `<containerizingMode>packaged` in Spring Boot projects where Jib assumed a wrong JAR path when `<finalName>` or `<classifier>` is configured in Spring Boot. ([#2565](https://github.com/GoogleContainerTools/jib/issues/2565))
- Fixed an issue where Jib cannot infer Kotlin main class that takes no arguments. ([#2666](https://github.com/GoogleContainerTools/jib/pull/2666))

## 2.4.0

### Added

- Jib Extension Framework! The framework enables anyone to easily extend and tailor the Jib Maven plugin behavior to their liking. Check out the new [Jib Extensions](https://github.com/GoogleContainerTools/jib-extensions) GitHub repository to learn more. ([#2401](https://github.com/GoogleContainerTools/jib/issues/2401))
- Project dependencies in a multi-module WAR project are now stored in a separate "project dependencies" layer (as currently done for a non-WAR project). ([#2450](https://github.com/GoogleContainerTools/jib/issues/2450))

### Changed

- Previous locally cached application layers (`<project root>/target/jib-cache`) will be ignored because of changes to the caching selectors. ([#2499](https://github.com/GoogleContainerTools/jib/pull/2499))

### Fixed

- Fixed authentication failure with Azure Container Registry when using an identity token defined in the `auths` section of Docker config (`~/.docker/config.json`). ([#2488](https://github.com/GoogleContainerTools/jib/pull/2488))

## 2.3.0

### Added

- `<from>` and `<into>` fields to `<extraDirectories><paths><path>` for configuring the source and target of an extra directory. ([#1581](https://github.com/GoogleContainerTools/jib/issues/1581))

### Fixed

- Fixed the problem not inheriting `USER` container configuration from a base image. ([#2421](https://github.com/GoogleContainerTools/jib/pull/2421))
- Fixed wrong capitalization of JSON properties in a loadable Docker manifest when building a tar image. ([#2430](https://github.com/GoogleContainerTools/jib/issues/2430))
- Fixed an issue when using a base image whose image creation timestamp contains timezone offset. ([#2428](https://github.com/GoogleContainerTools/jib/issues/2428))
- Fixed an issue inferring a wrong main class or using an invalid main class (for example, Spring Boot project containing multiple main classes). ([#2456](https://github.com/GoogleContainerTools/jib/issues/2456))

## 2.2.0

### Added

- Glob pattern support for `<extraDirectories><permissions>`. ([#1200](https://github.com/GoogleContainerTools/jib/issues/1200))
- Support for image references with both a tag and a digest. ([#1481](https://github.com/GoogleContainerTools/jib/issues/1481))
- The `DOCKER_CONFIG` environment variable specifying the directory containing docker configs is now checked during credential retrieval. ([#1618](https://github.com/GoogleContainerTools/jib/issues/1618))
- Also tries `.cmd` file extension for credential helpers on Windows. ([#2399](https://github.com/GoogleContainerTools/jib/issues/2399))

### Changed

- `<container><creationTime>` now accepts more timezone formats:`+HHmm`. This allows for easier configuration of creationTime by external systems. ([#2320](https://github.com/GoogleContainerTools/jib/issues/2320))

## 2.1.0

### Added

- Additionally reads credentials from `~/.docker/.dockerconfigjson` and legacy Docker config (`~/.docker/.dockercfg`). Also searches for `$HOME/.docker/*` (in addition to current `System.get("user.home")/.docker/*`). This may help retrieve credentials, for example, on Kubernetes. ([#2260](https://github.com/GoogleContainerTools/jib/issues/2260))
- New skaffold configuration options that modify how jib's build config is presented to skaffold ([#2292](https://github.com/GoogleContainerTools/jib/pull/2292)):
    - `<watch><buildIncludes>`: a list of build files to watch
    - `<watch><includes>`: a list of project files to watch
    - `<watch><excludes>`: a list of files to exclude from watching
    - `<sync><excludes>`: a list of files to exclude from sync'ing

### Fixed

- Fixed a `skaffold init` issue with projects containing submodules specifying different parent poms. ([#2262](https://github.com/GoogleContainerTools/jib/issues/2262))
- Fixed authentication failure with error `server did not return 'WWW-Authenticate: Bearer' header` in certain cases (for example, on OpenShift). ([#2258](https://github.com/GoogleContainerTools/jib/issues/2258))
- Fixed an issue where using local Docker images (by `docker://...`) on Windows caused an error. ([#2270](https://github.com/GoogleContainerTools/jib/issues/2270))

## 2.0.0

### Added

- Added json output file for image metadata after a build is complete. Writes to `target/jib-image.json` by default, configurable with `<outputPaths><imageJson>`. ([#2227](https://github.com/GoogleContainerTools/jib/pull/2227))
- Added automatic update checks. Jib will now display a message if there is a new version of Jib available. See the [privacy page](../docs/privacy.md) for more details. ([#2193](https://github.com/GoogleContainerTools/jib/issues/2193))

### Changed

- Removed deprecated `<extraDirectory>` configuration in favor of `<extraDirectories>`. ([#1691](https://github.com/GoogleContainerTools/jib/issues/1691))
- Removed deprecated `<container><useCurrentTimestamp>` configuration in favor of `<container><creationTime>` with `USE_CURRENT_TIMESTAMP`. ([#1897](https://github.com/GoogleContainerTools/jib/issues/1897))
- HTTP redirection URLs are no longer sanitized in order to work around an issue with certain registries that do not conform to HTTP standards. This resolves an issue with using Red Hat OpenShift and Quay registries. ([#2106](https://github.com/GoogleContainerTools/jib/issues/2106), [#1986](https://github.com/GoogleContainerTools/jib/issues/1986#issuecomment-547610104))
- The default base image cache location has been changed on MacOS and Windows. ([#2216](https://github.com/GoogleContainerTools/jib/issues/2216))
    - MacOS (`$XDG_CACHE_HOME` defined): from `$XDG_CACHE_HOME/google-cloud-tools-java/jib/` to `$XDG_CACHE_HOME/Google/Jib/`
    - MacOS (`$XDG_CACHE_HOME` not defined): from `$HOME/Library/Application Support/google-cloud-tools-java/jib/` to `$HOME/Library/Caches/Google/Jib/`
    - Windows (`$XDG_CACHE_HOME` defined): from `$XDG_CACHE_HOME\google-cloud-tools-java\jib\` to `$XDG_CACHE_HOME\Google\Jib\Cache\`
    - Windows (`$XDG_CACHE_HOME` not defined): from `%LOCALAPPDATA%\google-cloud-tools-java\jib\` to `%LOCALAPPDATA%\Google\Jib\Cache\`
    - Initial builds will be slower until the cache is repopulated, unless you manually move the cache from the old location to the new location
- When giving registry credentials in `settings.xml`, specifying port in `<server><id>` is no longer required. ([#2135](https://github.com/GoogleContainerTools/jib/issues/2135))

### Fixed

- Fixed `<extraDirectories><permissions>` being ignored if `<paths>` are not explicitly defined. ([#2106](https://github.com/GoogleContainerTools/jib/issues/2160))
- Now `<containerizingMode>packaged` works as intended with Spring Boot projects that generate a fat JAR. ([#2170](https://github.com/GoogleContainerTools/jib/issues/2170))
- Now `<containerizingMode>packaged` correctly identifies the packaged JAR generated at a non-default location when configured with the Maven Jar Plugin's `<classifier>` and `<outputDirectory>`. ([#2170](https://github.com/GoogleContainerTools/jib/issues/2170))
- `jib:buildTar` with `<container><format>OCI` now builds a correctly formatted OCI archive. ([#2124](https://github.com/GoogleContainerTools/jib/issues/2124))
- Fixed an issue where configuring the `<warName>` property of the Maven WAR plugin fails the build. ([#2206](https://github.com/GoogleContainerTools/jib/issues/2206))
- Now automatically refreshes Docker registry authentication tokens when expired, fixing the issue that long-running builds may fail with "401 unauthorized." ([#691](https://github.com/GoogleContainerTools/jib/issues/691))

## 1.8.0

### Changed

- Optimized building to a registry with local base images. ([#1913](https://github.com/GoogleContainerTools/jib/issues/1913))

### Fixed

- Fixed reporting wrong module name when `skaffold init` is run on multi-module projects. ([#2088](https://github.com/GoogleContainerTools/jib/issues/2088))
- `<allowInsecureRegistries>` and the `sendCredentialsOverHttp` system property are now effective for authentication service server connections. ([#2074](https://github.com/GoogleContainerTools/jib/pull/2074))
- Fixed inefficient communications when interacting with insecure registries and servers (when `<allowInsecureRegistries>` is set). ([#946](https://github.com/GoogleContainerTools/jib/issues/946))

## 1.7.0

### Added

- `<outputPaths>` object for configuration output file locations ([#1561](https://github.com/GoogleContainerTools/jib/issues/1561))
  - `<outputPaths><tar>` configures output path of `jib:buildTar` (`target/jib-image.tar` by default)
  - `<outputPaths><digest>` configures the output path of the image digest (`target/jib-image.digest` by default)
  - `<outputPaths><imageId>` configures output path of the image id  (`target/jib-image.id` by default)
- Main class inference support for Java 13/14. ([#2015](https://github.com/GoogleContainerTools/jib/issues/2015))

### Changed

- Local base image layers are now processed in parallel, speeding up builds using large local base images. ([#1913](https://github.com/GoogleContainerTools/jib/issues/1913))
- The base image manifest is no longer pulled from the registry if a digest is provided and the manifest is already cached. ([#1881](https://github.com/GoogleContainerTools/jib/issues/1881))
- Docker daemon base images are now cached more effectively, speeding up builds using `docker://` base images. ([#1912](https://github.com/GoogleContainerTools/jib/issues/1912))

### Fixed

- Fixed temporary directory cleanup during builds using local base images. ([#2016](https://github.com/GoogleContainerTools/jib/issues/2016))
- Fixed additional tags being ignored when building to a tarball. ([#2043](https://github.com/GoogleContainerTools/jib/issues/2043))
- Fixed `tar://` base image failing if tar does not contain explicit directory entries. ([#2067](https://github.com/GoogleContainerTools/jib/issues/2067))
- Fixed an issue for WAR projects where Jib used an intermediate exploded WAR directory instead of exploding the final WAR file. ([#1091](https://github.com/GoogleContainerTools/jib/issues/1091))

## 1.6.1

### Fixed

- Fixed an issue with using custom base images in Java 12+ projects. ([#1995](https://github.com/GoogleContainerTools/jib/issues/1995))

## 1.6.0

### Added

- Support for local base images by prefixing `<from><image>` with `docker://` to build from a docker daemon image, or `tar://` to build from a tarball image. ([#1468](https://github.com/GoogleContainerTools/jib/issues/1468), [#1905](https://github.com/GoogleContainerTools/jib/issues/1905))

### Changed

- To disable parallel execution, the property `jib.serialize` should be used instead of `jibSerialize`. ([#1968](https://github.com/GoogleContainerTools/jib/issues/1968))
- For retrieving credentials from Docker config (`~/.docker/config.json`), `credHelpers` now takes precedence over `credsStore`, followed by `auths`. ([#1958](https://github.com/GoogleContainerTools/jib/pull/1958))
- The legacy `credsStore` no longer requires defining empty registry entries in `auths` to be used. This now means that if `credsStore` is defined, `auths` will be completely ignored. ([#1958](https://github.com/GoogleContainerTools/jib/pull/1958))
- `<dockerClient>` is now configurable on all goals, not just `jib:dockerBuild`. ([#1932](https://github.com/GoogleContainerTools/jib/issues/1932))

### Fixed

- Fixed the regression of slow network operations introduced at 1.5.0. ([#1980](https://github.com/GoogleContainerTools/jib/pull/1980))
- Fixed an issue where connection timeout sometimes fell back to attempting plain HTTP (non-HTTPS) requests when `<allowInsecureRegistries>` is set. ([#1949](https://github.com/GoogleContainerTools/jib/pull/1949))

## 1.5.1

### Fixed

- Fixed an issue interacting with certain registries due to changes to URL handling in the underlying Apache HttpClient library. ([#1924](https://github.com/GoogleContainerTools/jib/issues/1924))

## 1.5.0

### Added

- Can now set file timestamps (last modified time) in the image with `<container><filesModificationTime>`. The value should either be `EPOCH_PLUS_SECOND` to set the timestamps to Epoch + 1 second (default behavior), or an ISO 8601 date time parsable with [`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html) such as `2019-07-15T10:15:30+09:00` or `2011-12-03T22:42:05Z`. ([#1818](https://github.com/GoogleContainerTools/jib/pull/1818))
- Can now set container creation timestamp with `<container><creationTime>`. The value should be `EPOCH`, `USE_CURRENT_TIMESTAMP`, or an ISO 8601 date time. ([#1609](https://github.com/GoogleContainerTools/jib/issues/1609))
- For Google Container Registry (gcr.io), Jib now tries [Google Application Default Credentials](https://developers.google.com/identity/protocols/application-default-credentials) (ADC) last when no credentials can be retrieved. ADC are available on many Google Cloud Platform (GCP) environments (such as Google Cloud Build, Google Compute Engine, Google Kubernetes Engine, and Google App Engine). Application Default Credentials can also be configured with `gcloud auth application-default login` locally or through the `GOOGLE_APPLICATION_CREDENTIALS` environment variable. ([#1902](https://github.com/GoogleContainerTools/jib/pull/1902))

### Changed

- When building to a registry, Jib now skips downloading and caching base image layers that already exist in the target registry. This feature will be particularly useful in CI/CD environments. However, if you want to force caching base image layers locally, set the system property `-Djib.alwaysCacheBaseImage=true`. ([#1840](https://github.com/GoogleContainerTools/jib/pull/1840))
- `<container><useCurrentTimestamp>` has been deprecated in favor of `<container><creationTime>` with `USE_CURRENT_TIMESTAMP`. ([#1609](https://github.com/GoogleContainerTools/jib/issues/1609))

## 1.4.0

### Added

- Can now containerize a JAR artifact instead of putting individual `.class` and resource files with `<containerizingMode>packaged`. ([#1746](https://github.com/GoogleContainerTools/jib/pull/1746/files))
- Can now use `<from><image>scratch` to use the scratch (empty) base image for builds. ([#1794](https://github.com/GoogleContainerTools/jib/pull/1794/files))

### Changed

- Dependencies are now split into three layers: dependencies, snapshots dependencies, project dependencies. ([#1724](https://github.com/GoogleContainerTools/jib/pull/1724))

### Fixed

- Re-enabled cross-repository blob mounts. ([#1793](https://github.com/GoogleContainerTools/jib/pull/1793))
- Manifest lists referenced directly by sha256 are automatically parsed and the first `linux/amd64` manifest is used. ([#1811](https://github.com/GoogleContainerTools/jib/issues/1811))

## 1.3.0

### Changed

- Docker credentials (`~/.docker/config.json`) are now given priority over registry-based inferred credential helpers. ([#1704](https://github.com/GoogleContainerTools/jib/pulls/1704))

### Fixed

- Fixed an issue where decyrpting Maven settings `settings.xml` wholesale caused the build to fail. We now decrypt only the parts that are required. ([#1709](https://github.com/GoogleContainerTools/jib/issues/1709))

## 1.2.0

### Added

- Container configurations in the base image are now propagated when registry uses the old V2 image manifest, schema version 1 (such as Quay). ([#1641](https://github.com/GoogleContainerTools/jib/issues/1641))
- Can now prepend paths in the container to the computed classpath with `<container><extraClasspath>`. ([#1642](https://github.com/GoogleContainerTools/jib/pull/1642))
- Can now build in offline mode using `--offline`. ([#718](https://github.com/GoogleContainerTools/jib/issues/718))
- Now supports multiple extra directories with `<extraDirectories>{<paths><path>|<permissions>}`. ([#1020](https://github.com/GoogleContainerTools/jib/issues/1020))

### Changed

- `<extraDirectory>(<path>|<permissions>)` are deprecated in favor of the new `<extraDirectories>{<paths><path>|<permissions>}` configurations. ([#1626](https://github.com/GoogleContainerTools/jib/pull/1626))

### Fixed

- Labels in the base image are now propagated. ([#1643](https://github.com/GoogleContainerTools/jib/issues/1643))
- Fixed an issue with using OCI base images. ([#1683](https://github.com/GoogleContainerTools/jib/issues/1683))

## 1.1.2

### Fixed

- Fixed an issue where automatically generated parent directories in a layer did not get their timestamp configured correctly to epoch + 1s. ([#1648](https://github.com/GoogleContainerTools/jib/issues/1648))

## 1.1.1

### Fixed

- Fixed an issue where the plugin creates wrong images by adding base image layers in reverse order when registry uses the old V2 image manifest, schema version 1 (such as Quay). ([#1627](https://github.com/GoogleContainerTools/jib/issues/1627))

## 1.1.0

### Added

- Can now decrypt proxy configurations in `settings.xml`. ([#1369](https://github.com/GoogleContainerTools/jib/issues/1369))

### Changed

- `os` and `architecture` are taken from base image. ([#1564](https://github.com/GoogleContainerTools/jib/pull/1564))

### Fixed

- Fixed an issue where pushing to Docker Hub fails when the host part of an image reference is `docker.io`. ([#1549](https://github.com/GoogleContainerTools/jib/issues/1549))

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

- Failure to infer main class when main method is defined using varargs (i.e. `public static void main(String... args)`). ([#1456](https://github.com/GoogleContainerTools/jib/issues/1456))

## 1.0.0

### Changed

- Shortened progress bar display - make sure console window is at least 50 characters wide or progress bar display can be messy. ([#1361](https://github.com/GoogleContainerTools/jib/issues/1361))

## 1.0.0-rc2

### Added

- Setting proxy credentials (via system properties `http(s).proxyUser` and `http(s).proxyPassword`) is now supported.
- Maven proxy settings are now supported.
- Now checks for system properties in pom as well as commandline. ([#1201](https://github.com/GoogleContainerTools/jib/issues/1201))
- `<dockerClient><executable>` and `<dockerClient><environment>` to set Docker client binary path (defaulting to `docker`) and additional environment variables to apply when running the binary. ([#468](https://github.com/GoogleContainerTools/jib/issues/468))

### Changed

- Java 9+ projects using the default distroless Java 8 base image will now fail to build. ([#1143](https://github.com/GoogleContainerTools/jib/issues/1143))

## 1.0.0-rc1

### Added

- `jib.baseImageCache` and `jib.applicationCache` system properties for setting cache directories. ([#1238](https://github.com/GoogleContainerTools/jib/issues/1238))
- Build progress shown via a progress bar - set `-Djib.console=plain` to show progress as log messages. ([#1297](https://github.com/GoogleContainerTools/jib/issues/1297))

### Changed

- `gwt-app` packaging type now builds a WAR container.
- When building to Docker and no `<to><image>` is defined, artifact ID is used as an image reference instead of project name.
- Removed `<useOnlyProjectCache>` parameter in favor of the `jib.useOnlyProjectCache` system property. ([#1308](https://github.com/GoogleContainerTools/jib/issues/1308))

### Fixed

- Builds failing due to dependency JARs with the same name. ([#810](https://github.com/GoogleContainerTools/jib/issues/810))

## 0.10.1

### Added

- Image ID is now written to `target/jib-image.id`. ([#1204](https://github.com/GoogleContainerTools/jib/issues/1204))
- `<container><entrypoint>INHERIT</entrypoint></container>` allows inheriting `ENTRYPOINT` and `CMD` from the base image. While inheriting `ENTRYPOINT`, you can also override `CMD` using `<container><args>`.
- `<container><workingDirectory>` configuration parameter to set the working directory. ([#1225](https://github.com/GoogleContainerTools/jib/issues/1225))
- Adds support for configuring volumes. ([#1121](https://github.com/GoogleContainerTools/jib/issues/1121))
- Exposed ports are now propagated from the base image. ([#595](https://github.com/GoogleContainerTools/jib/issues/595))
- Docker health check is now propagated from the base image. ([#595](https://github.com/GoogleContainerTools/jib/issues/595))

### Changed

- Removed `jib:exportDockerContext` goal. ([#1219](https://github.com/GoogleContainerTools/jib/issues/1219))

### Fixed

- NullPointerException thrown with incomplete `auth` configuration. ([#1177](https://github.com/GoogleContainerTools/jib/issues/1177))

## 0.10.0

### Added

- Properties for each configuration parameter, allowing any parameter to be set via commandline. ([#728](https://github.com/GoogleContainerTools/jib/issues/728))
- `<to><credHelper>` and `<from><credHelper>` can be used to specify a credential helper suffix or a full path to a credential helper executable. ([#925](https://github.com/GoogleContainerTools/jib/issues/925))
- `<container><user>` configuration parameter to configure the user and group to run the container as. ([#1029](https://github.com/GoogleContainerTools/jib/issues/1029))
- Preliminary support for building images for WAR projects. ([#431](https://github.com/GoogleContainerTools/jib/issues/431))
- `<extraDirectory>` object with a `<path>` and `<permissions>` field. ([#794](https://github.com/GoogleContainerTools/jib/issues/794))
  - `<extraDirectory><path>` configures the extra layer directory (still also configurable via `<extraDirectory>...</extraDirectory>`)
  - `<extraDirectory><permissions>` is a list of `<permission>` objects, each with a `<file>` and `<mode>` field, used to map a file on the container to the file's permission bits (represented as an octal string)
- Image digest is now written to `target/jib-image.digest`. ([#1155](https://github.com/GoogleContainerTools/jib/pull/1155))
- Adds the layer type to the layer history as comments. ([#1198](https://github.com/GoogleContainerTools/jib/issues/1198))

### Changed

- Removed deprecated `<jvmFlags>`, `<mainClass>`, `<args>`, and `<format>` in favor of the equivalents under `<container>`. ([#461](https://github.com/GoogleContainerTools/jib/issues/461))
- `jib:exportDockerContext` generates different directory layout and `Dockerfile` to enable WAR support. ([#1007](https://github.com/GoogleContainerTools/jib/pull/1007))
- File timestamps in the built image are set to 1 second since the epoch (hence 1970-01-01T00:00:01Z) to resolve compatibility with applications on Java 6 or below where the epoch means nonexistent or I/O errors; previously they were set to the epoch. ([#1079](https://github.com/GoogleContainerTools/jib/issues/1079))

## 0.9.13

### Fixed

- Adds environment variable configuration to Docker context generator. ([#890 (comment)](https://github.com/GoogleContainerTools/jib/issues/890#issuecomment-430227555))

## 0.9.11

### Added

- `<skip>` configuration parameter to skip Jib execution in multi-module projects (also settable via `jib.skip` property). ([#865](https://github.com/GoogleContainerTools/jib/issues/865))
- `<container><environment>` configuration parameter to configure environment variables. ([#890](https://github.com/GoogleContainerTools/jib/issues/890))
- `container.appRoot` configuration parameter to configure app root in the image. ([#984](https://github.com/GoogleContainerTools/jib/pull/984))
- `<to><tags>` (list) defines additional tags to push to. ([#1026](https://github.com/GoogleContainerTools/jib/pull/1026))

### Fixed

- Keep duplicate layers to match container history. ([#1017](https://github.com/GoogleContainerTools/jib/pull/1017))

## 0.9.10

### Added

- `<container><labels>` configuration parameter for configuring labels. ([#751](https://github.com/GoogleContainerTools/jib/issues/751))
- `<container><entrypoint>` configuration parameter to set the entrypoint. ([#579](https://github.com/GoogleContainerTools/jib/issues/579))
- `history` to layer metadata. ([#875](https://github.com/GoogleContainerTools/jib/issues/875))
- Propagates working directory from the base image. ([#902](https://github.com/GoogleContainerTools/jib/pull/902))

### Fixed

- Corrects permissions for directories in the container filesystem. ([#772](https://github.com/GoogleContainerTools/jib/pull/772))

## 0.9.9

### Added

- Passthrough labels from base image. ([#750](https://github.com/GoogleContainerTools/jib/pull/750/files))

### Changed

- Reordered classpath in entrypoint to allow dependency patching. ([#777](https://github.com/GoogleContainerTools/jib/issues/777))

### Fixed

## 0.9.8

### Added

- `<to><auth>` and `<from><auth>` parameters with `<username>` and `<password>` fields for simple authentication, similar to the Gradle plugin. ([#693](https://github.com/GoogleContainerTools/jib/issues/693))
- Can set credentials via commandline using `jib.to.auth.username`, `jib.to.auth.password`, `jib.from.auth.username`, and `jib.from.auth.password` system properties. ([#693](https://github.com/GoogleContainerTools/jib/issues/693))
- Docker context generation now includes snapshot dependencies and extra files. ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Disable parallel operation by setting the `jibSerialize` system property to `true`. ([#682](https://github.com/GoogleContainerTools/jib/pull/682))

### Changed

- Propagates environment variables from the base image. ([#716](https://github.com/GoogleContainerTools/jib/pull/716))
- Skips execution if packaging is `pom`. ([#735](https://github.com/GoogleContainerTools/jib/pull/735))
- `allowInsecureRegistries` allows connecting to insecure HTTPS registries (for example, registries using self-signed certificates). ([#733](https://github.com/GoogleContainerTools/jib/pull/733))

### Fixed

- Slow image reference parsing. ([#680](https://github.com/GoogleContainerTools/jib/pull/680))
- Building empty layers. ([#516](https://github.com/GoogleContainerTools/jib/pull/516/files))
- Duplicate layer entries causing unbounded cache growth. ([#721](https://github.com/GoogleContainerTools/jib/issues/721))
- Incorrect authentication error message when target and base registry are the same. ([#758](https://github.com/GoogleContainerTools/jib/issues/758))

## 0.9.7

### Added

- Snapshot dependencies are added as their own layer. ([#584](https://github.com/GoogleContainerTools/jib/pull/584))
- `jib:buildTar` goal to build an image tarball at `target/jib-image.tar`, which can be loaded into docker using `docker load`. ([#514](https://github.com/GoogleContainerTools/jib/issues/514))
- `<container><useCurrentTimestamp>` parameter to set the image creation time to the build time. ([#413](https://github.com/GoogleContainerTools/jib/issues/413))
- Authentication over HTTP using the `sendCredentialsOverHttp` system property. ([#599](https://github.com/GoogleContainerTools/jib/issues/599))
- HTTP connection and read timeouts for registry interactions configurable with the `jib.httpTimeout` system property. ([#656](https://github.com/GoogleContainerTools/jib/pull/656))

### Changed

- Docker context export parameter `-Djib.dockerDir` to `-DjibTargetDir`. ([#662](https://github.com/GoogleContainerTools/jib/issues/662))

### Fixed

- Using multi-byte characters in container configuration. ([#626](https://github.com/GoogleContainerTools/jib/issues/626))
- For Docker Hub, also tries registry aliases when getting a credential from the Docker config. ([#605](https://github.com/GoogleContainerTools/jib/pull/605))
- Decrypting credentials from Maven settings. ([#592](https://github.com/GoogleContainerTools/jib/issues/592))

## 0.9.6

### Fixed

- Using a private registry that does token authentication with `allowInsecureRegistries` set to `true`. ([#572](https://github.com/GoogleContainerTools/jib/pull/572))

## 0.9.5

### Added

- Incubating feature to build `src/main/jib` as extra layer in image. ([#565](https://github.com/GoogleContainerTools/jib/pull/565))

## 0.9.4

### Fixed

- Fixed handling case-insensitive `Basic` authentication method. ([#546](https://github.com/GoogleContainerTools/jib/pull/546))
- Fixed regression that broke pulling base images from registries that required token authentication. ([#549](https://github.com/GoogleContainerTools/jib/pull/549))

## 0.9.3

### Fixed

- Using Docker config for finding registry credentials (was not ignoring extra fields and handling `https` protocol). ([#524](https://github.com/GoogleContainerTools/jib/pull/524))

## 0.9.2

### Changed

- Minor improvements and issue fixes.

## 0.9.1

### Added

- `<container><ports>` parameter to define container's exposed ports (similar to Dockerfile `EXPOSE`). ([#383](https://github.com/GoogleContainerTools/jib/issues/383))
- Can set `allowInsecureRegistries` parameter to `true` to use registries that only support HTTP. ([#388](https://github.com/GoogleContainerTools/jib/issues/388))

### Changed

- Fetches credentials from inferred credential helper before Docker config. ([#401](https://github.com/GoogleContainerTools/jib/issues/401))
- Container creation date set to timestamp 0. ([#341](https://github.com/GoogleContainerTools/jib/issues/341))
- Does not authenticate base image pull unless necessary - reduces build time by about 500ms. ([#414](https://github.com/GoogleContainerTools/jib/pull/414))
- `jvmFlags`, `mainClass`, `args`, and `format` are now grouped under `container` configuration object. ([#384](https://github.com/GoogleContainerTools/jib/issues/384))

### Fixed

- Using Azure Container Registry now works - define credentials in Maven settings. ([#415](https://github.com/GoogleContainerTools/jib/issues/415))
- Supports `access_token` as alias to `token` in registry authentication. ([#420](https://github.com/GoogleContainerTools/jib/pull/420))

## 0.9.0

### Added

- Better feedback for build failures. ([#197](https://github.com/google/jib/pull/197))
- Warns if specified `mainClass` is not a valid Java class. ([#206](https://github.com/google/jib/issues/206))
- Warns if build may not be reproducible. ([#245](https://github.com/GoogleContainerTools/jib/pull/245))
- `jib:dockerBuild` maven goal to build straight to Docker daemon. ([#266](https://github.com/GoogleContainerTools/jib/pull/266))
- `mainClass` is inferred by searching through class files if configuration is missing. ([#278](https://github.com/GoogleContainerTools/jib/pull/278))
- Can now specify target image with `-Dimage`. ([#328](https://github.com/GoogleContainerTools/jib/issues/328))
- `args` parameter to define default main args. ([#346](https://github.com/GoogleContainerTools/jib/issues/346))

### Changed

- Removed `enableReproducibleBuilds` parameter - application layers will always be reproducible. ([#245](https://github.com/GoogleContainerTools/jib/pull/245))
- Changed configuration schema to be more like configuration for `jib-gradle-plugin` - NOT compatible with prior versions of `jib-maven-plugin`. ([#212](https://github.com/GoogleContainerTools/jib/issues/212))
- `jib:dockercontext` has been changed to `jib:exportDockerContext`. ([#350](https://github.com/GoogleContainerTools/jib/issues/350))

### Fixed

- Directories in resources are added to classes layer. ([#318](https://github.com/GoogleContainerTools/jib/issues/318))

## 0.1.7

### Fixed

- Using base images that lack entrypoints. ([#284](https://github.com/GoogleContainerTools/jib/pull/284))

## 0.1.6

### Changed

- Base image layers are now cached on a user-level rather than a project level - disable with `useOnlyProjectCache` configuration. ([#29](https://github.com/google/jib/issues/29))

### Fixed

- `jib:dockercontext` not building a `Dockerfile`. ([#171](https://github.com/google/jib/pull/171))
- Failure to parse Docker config with `HttpHeaders` field. ([#175](https://github.com/google/jib/pull/175))

## 0.1.5

### Added

- Export a Docker context (including a Dockerfile) with `jib:dockercontext`. ([#49](https://github.com/google/jib/issues/49))

## 0.1.4

### Fixed

- Null tag validation generating NullPointerException. ([#125](https://github.com/google/jib/issues/125))
- Build failure on project with no dependencies. ([#126](https://github.com/google/jib/issues/126))

## 0.1.3

### Added

- Build and push OCI container image. ([#96](https://github.com/google/jib/issues/96))

## 0.1.2

### Added

- Use credentials from Docker config if none can be found otherwise. ([#101](https://github.com/google/jib/issues/101))
- Reproducible image building. ([#7](https://github.com/google/jib/issues/7))

## 0.1.1

### Added

- Simple example `helloworld` project under `examples/`. ([#62](https://github.com/google/jib/pull/62))
- Better error messages when pushing an image manifest. ([#63](https://github.com/google/jib/pull/63))
- Validates target image configuration. ([#63](https://github.com/google/jib/pull/63))
- Configure multiple credential helpers with `credHelpers`. ([#68](https://github.com/google/jib/pull/68))
- Configure registry credentials with Maven settings. ([#81](https://github.com/google/jib/pull/81))

### Changed

- Removed configuration `credentialHelperName`. ([#68](https://github.com/google/jib/pull/68))

### Fixed

- Build failure on Windows. ([#74](https://github.com/google/jib/issues/74))
- Infers common credential helper names (for GCR and ECR). ([#64](https://github.com/google/jib/pull/64))
- Cannot use private base image. ([#68](https://github.com/google/jib/pull/68))
- Building applications with no resources. ([#73](https://github.com/google/jib/pull/73))
- Pushing to registries like Docker Hub and ACR. ([#75](https://github.com/google/jib/issues/75))
- Cannot build with files having long file names (> 100 chars). ([#91](https://github.com/google/jib/issues/91))
