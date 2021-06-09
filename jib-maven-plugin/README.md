![stable](https://img.shields.io/badge/stability-stable-brightgreen.svg)
[![Maven Central](https://img.shields.io/maven-central/v/com.google.cloud.tools/jib-maven-plugin)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib - Containerize your Maven project

Jib is a [Maven](https://maven.apache.org/) plugin for building Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications.

For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

For information about the project, see the [Jib project README](../README.md).

| ☑️  Jib User Survey |
| :----- |
| What do you like best about Jib? What needs to be improved? Please tell us by taking a [one-minute survey](https://forms.gle/YRFeamGj51xmgnx28). Your responses will help us understand Jib usage and allow us to serve our customers (you!) better. |

## Table of Contents

* [Upcoming Features](#upcoming-features)
* [Quickstart](#quickstart)
  * [Setup](#setup)
  * [Configuration](#configuration)
  * [Build your image](#build-your-image)
    * [Build to Docker Daemon](#build-to-docker-daemon)
    * [Build an image tarball](#build-an-image-tarball)
  * [Bind to a lifecycle](#bind-to-a-lifecycle)
  * [Additional Build Artifacts](#additional-build-artifacts)
* [Multi Module Projects](#multi-module-projects)
* [Extended Usage](#extended-usage)
  * [System Properties](#system-properties)
  * [Global Jib Configuration](#global-jib-configuration)
  * [Example](#example)
  * [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)
  * [Authentication Methods](#authentication-methods)
    * [Using Docker Credential Helpers](#using-docker-credential-helpers)
    * [Using Specific Credentials](#using-specific-credentials)
    * [Using Maven Settings](#using-maven-settings)
  * [Custom Container Entrypoint](#custom-container-entrypoint)
  * [Jib Extensions](#jib-extensions)
  * [WAR Projects](#war-projects)
  * [Skaffold Integration](#skaffold-integration)
* [Frequently Asked Questions (FAQ)](#frequently-asked-questions-faq)
* [Community](#community)

## Quickstart

You can containerize your application easily with one command:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:3.1.0:build -Dimage=<MY IMAGE>
```

This builds and pushes a container image for your application to a container registry. *If you encounter authentication issues, see [Authentication Methods](#authentication-methods).*

To build to a Docker daemon, use:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:3.1.0:dockerBuild
```

If you would like to set up Jib as part of your Maven build, follow the guide below.

### Setup

In your Maven Java project, add the plugin to your `pom.xml`:

```xml
<project>
  ...
  <build>
    <plugins>
      ...
      <plugin>
        <groupId>com.google.cloud.tools</groupId>
        <artifactId>jib-maven-plugin</artifactId>
        <version>3.1.0</version>
        <configuration>
          <to>
            <image>myimage</image>
          </to>
        </configuration>
      </plugin>
      ...
    </plugins>
  </build>
  ...
</project>
```

### Configuration

Configure the plugin by setting the image to push to:

#### Using [Google Container Registry (GCR)](https://cloud.google.com/container-registry/)...

*Make sure you have the [`docker-credential-gcr` command line tool](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper). Jib automatically uses `docker-credential-gcr` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `gcr.io/my-gcp-project/my-app`, the configuration would be:

```xml
<configuration>
  <to>
    <image>gcr.io/my-gcp-project/my-app</image>
  </to>
</configuration>
```

#### Using [Amazon Elastic Container Registry (ECR)](https://aws.amazon.com/ecr/)...

*Make sure you have the [`docker-credential-ecr-login` command line tool](https://github.com/awslabs/amazon-ecr-credential-helper). Jib automatically uses `docker-credential-ecr-login` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `aws_account_id.dkr.ecr.region.amazonaws.com/my-app`, the configuration would be:

```xml
<configuration>
  <to>
    <image>aws_account_id.dkr.ecr.region.amazonaws.com/my-app</image>
  </to>
</configuration>
```

#### Using [Docker Hub Registry](https://hub.docker.com/)...

*Make sure you have a [docker-credential-helper](https://github.com/docker/docker-credential-helpers#available-programs) set up. For example, on macOS, the credential helper would be `docker-credential-osxkeychain`. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `my-docker-id/my-app`, the configuration would be:

```xml
<configuration>
  <to>
    <image>docker.io/my-docker-id/my-app</image>
  </to>
</configuration>
```

#### Using [Azure Container Registry (ACR)](https://azure.microsoft.com/en-us/services/container-registry/)...

*Make sure you have a [`ACR Docker Credential Helper`](https://github.com/Azure/acr-docker-credential-helper) installed and set up. For example, on Windows, the credential helper would be `docker-credential-acr-windows`. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `my_acr_name.azurecr.io/my-app`, the configuration would be:

```xml
<configuration>
  <to>
    <image>my_acr_name.azurecr.io/my-app</image>
  </to>
</configuration>
```

### Build your image

Build your container image with:

```shell
mvn compile jib:build
```

Subsequent builds are much faster than the initial build.

*Having trouble? Let us know by [submitting an issue](/../../issues/new), contacting us on [Gitter](https://gitter.im/google/jib), or posting to the [Jib users forum](https://groups.google.com/forum/#!forum/jib-users).*

#### Build to Docker daemon

Jib can also build your image directly to a Docker daemon. This uses the `docker` command line tool and requires that you have `docker` available on your `PATH`.

```shell
mvn compile jib:dockerBuild
```

If you are using [`minikube`](https://github.com/kubernetes/minikube)'s remote Docker daemon, make sure you [set up the correct environment variables](https://minikube.sigs.k8s.io/docs/handbook/pushing/#1-pushing-directly-to-the-in-cluster-docker-daemon-docker-env) to point to the remote daemon:

```shell
eval $(minikube docker-env)
mvn compile jib:dockerBuild
```

Alternatively, you can set environment variables in the Jib configuration. See [`dockerClient`](#dockerclient-object) for more configuration options.

#### Build an image tarball

You can build and save your image to disk as a tarball with:

```shell
mvn compile jib:buildTar
```

This builds and saves your image to `target/jib-image.tar`, which you can load into docker with:

```shell
docker load --input target/jib-image.tar
```

### Bind to a lifecycle

You can also bind `jib:build` to a Maven lifecycle, such as `package`, by adding the following execution to your `jib-maven-plugin` definition:

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  ...
  <executions>
    <execution>
      <phase>package</phase>
      <goals>
        <goal>build</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

Then, you can build your container image by running:

```shell
mvn package
```

### Additional Build Artifacts

As part of an image build, Jib also writes out the _image digest_ and the _image ID_. By default, these are written out to `target/jib-image.digest` and `target/jib-image.id` respectively, but the locations can be configured using the `<outputFiles><digest>` and `<outputFiles><imageId>` configuration properties. See [Extended Usage](#outputpaths-object) for more details.

## Multi Module Projects

Special handling of project dependencies is recommended when building complex
multi module projects. See [Multi Module Example](https://github.com/GoogleContainerTools/jib/tree/master/examples/multi-module) for detailed information.

## Extended Usage

Extended configuration options provide additional options for customizing the image build.

Field | Type | Default | Description
--- | --- | --- | ---
`to` | [`to`](#to-object) | *Required* | Configures the target image to build your application to.
`from` | [`from`](#from-object) | See [`from`](#from-object) | Configures the base image to build your application on top of.
`container` | [`container`](#container-object) | See [`container`](#container-object) | Configures the container that is run from your image.
`extraDirectories` | [`extraDirectories`](#extradirectories-object) | See [`extraDirectories`](#extradirectories-object) | Configures the directories used to add arbitrary files to the image.
`outputPaths` | [`outputPaths`](#outputpaths-object) | See [`outputPaths`](#outputpaths-object) | Configures the locations of additional build artifacts generated by Jib.
`dockerClient` | [`dockerClient`](#dockerclient-object) | See [`dockerClient`](#dockerclient-object) | Configures Docker for building to/from the Docker daemon.
`skaffold` | [`skaffold`](#skaffold-integration) | See [`skaffold`](#skaffold-integration) | Configures the internal skaffold goals. This configuration should only be used when integrating with [`skaffold`](#skaffold-integration). |
`containerizingMode` | string | `exploded` | If set to `packaged`, puts the JAR artifact built at `${project.build.directory}/${project.build.finalName}.jar` (the default location where many JAR-building plugins put a JAR registered as a main artifact, such as the Maven JAR Plugin) into the final image. If set to `exploded` (default), containerizes individual `.class` files and resources files.
`allowInsecureRegistries` | boolean | `false` | If set to true, Jib ignores HTTPS certificate errors and may fall back to HTTP as a last resort. Leaving this parameter set to `false` is strongly recommended, since HTTP communication is unencrypted and visible to others on the network, and insecure HTTPS is no better than plain HTTP. [If accessing a registry with a self-signed certificate, adding the certificate to your Java runtime's trusted keys](https://github.com/GoogleContainerTools/jib/tree/master/docs/self_sign_cert.md) may be an alternative to enabling this option.
`skip` | boolean | `false` | If set to true, Jib execution is skipped (useful for multi-module projects). This can also be specified via the `-Djib.skip` command line option.

<a name="from-object"></a>`from` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | `adoptopenjdk:{8,11}-jre` (or `jetty` for WAR) | The image reference for the base image. The source type can be specified using a [special type prefix](#setting-the-base-image).
`auth` | [`auth`](#auth-object) | *None* | Specifies credentials directly (alternative to `credHelper`).
`credHelper` | string | *None* | Specifies a credential helper that can authenticate pulling the base image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).
`platforms` | list | See [`platform`](#platform-object) | _Incubating feature_: Configures platforms of base images to select from a manifest list.

<a name="to-object"></a>`to` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | *Required* | The image reference for the target image. This can also be specified via the `-Dimage` command line option.
`auth` | [`auth`](#auth-object) | *None* | Specifies credentials directly (alternative to `credHelper`).
`credHelper` | string | *None* | Specifies a credential helper that can authenticate pushing the target image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).
`tags` | list | *None* | Additional tags to push to.

<a name="auth-object"></a>`auth` is an object with the following properties (see [Using Specific Credentials](#using-specific-credentials)):

Property | Type
--- | ---
`username` | string
`password` | string

<a name="platform-object"></a>`platform` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`architecture` | string | `amd64` | The architecture of a base image to select from a manifest list.
`os` | string | `linux` | The OS of a base image to select from a manifest list.

See [How do I specify a platform in the manifest list (or OCI index) of a base image?](../docs/faq.md#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image) for examples.

<a name="container-object"></a>`container` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`appRoot` | string | `/app` | The root directory on the container where the app's contents are placed. Particularly useful for WAR-packaging projects to work with different Servlet engine base images by designating where to put exploded WAR contents; see [WAR usage](#war-projects) as an example.
`args` | list | *None* | Additional program arguments appended to the command to start the container (similar to Docker's [CMD](https://docs.docker.com/engine/reference/builder/#cmd) instruction in relation with [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint)). In the default case where you do not set a custom `entrypoint`, this parameter is effectively the arguments to the main method of your Java application.
`creationTime` | string | `EPOCH` | Sets the container creation time. (Note that this property does not affect the file modification times, which are configured using `<filesModificationTime>`.) The value can be `EPOCH` to set the timestamps to Epoch (default behavior), `USE_CURRENT_TIMESTAMP` to forgo reproducibility and use the real creation time, or an ISO 8601 date-time parsable with [`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME) such as `2019-07-15T10:15:30+09:00` or `2011-12-03T22:42:05Z`.
`entrypoint` | list | *None* | The command to start the container with (similar to Docker's [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint) instruction). If set, then `jvmFlags`, `mainClass`, `extraClasspath`, and `expandClasspathDependencies` are ignored. You may also set `<entrypoint>INHERIT</entrypoint>` (`<entrypoint><entry>INHERIT</entry></entrypoint>` in old Maven versions) to indicate that the `entrypoint` and `args` should be inherited from the base image.\*
`environment` | map | *None* | Key-value pairs for setting environment variables on the container (similar to Docker's [ENV](https://docs.docker.com/engine/reference/builder/#env) instruction).
`extraClasspath` | list | *None* | Additional paths in the container to prepend to the computed Java classpath.
`expandClasspathDependencies` | boolean | `false` | <ul><li>Java 8 *or* Jib < 3.1: When set to true, does not use a wildcard (for example, `/app/lib/*`) for dependency JARs in the default Java runtime classpath but instead enumerates the JARs. Has the effect of preserving the classpath loading order as defined by the Maven project.</li><li>Java >= 9 *and* Jib >= 3.1: The option has no effect. Jib *always* enumerates the dependency JARs. This is achieved by [creating and using an argument file](#custom-container-entrypoint) for the `--class-path` JVM argument.</li></ul>
`filesModificationTime` | string | `EPOCH_PLUS_SECOND` | Sets the modification time (last modified time) of files in the image put by Jib. (Note that this does not set the image creation time, which can be set using `<creationTime>`.) The value should either be `EPOCH_PLUS_SECOND` to set the timestamps to Epoch + 1 second (default behavior), or an ISO 8601 date-time parsable with [`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME) such as `2019-07-15T10:15:30+09:00` or `2011-12-03T22:42:05Z`.
`format` | string | `Docker` | Use `OCI` to build an [OCI container image](https://www.opencontainers.org/).
`jvmFlags` | list | *None* | Additional flags to pass into the JVM when running your application.
`labels` | map | *None* | Key-value pairs for applying image metadata (similar to Docker's [LABEL](https://docs.docker.com/engine/reference/builder/#label) instruction).
`mainClass` | string | *Inferred*\*\* | The main class to launch the application from.
`ports` | list | *None* | Ports that the container exposes at runtime (similar to Docker's [EXPOSE](https://docs.docker.com/engine/reference/builder/#expose) instruction).
`user` | string | *None* | The user and group to run the container as. The value can be a username or UID along with an optional groupname or GID. The following are all valid: `user`, `uid`, `user:group`, `uid:gid`, `uid:group`, `user:gid`.
`volumes` | list | *None* | Specifies a list of mount points on the container.
`workingDirectory` | string | *None* | The working directory in the container.

<a name="extradirectories-object"></a>`extraDirectories` is an object with the following properties (see [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)):

Property | Type | Default | Description
--- | --- | --- | ---
`paths` | list | `[(project-dir)/src/main/jib]` | List of [`path`](#path-object) objects and/or extra directory paths. Can be absolute or relative to the project root.
`permissions` | list | *None* | Maps file paths (glob patterns) on container to Unix permissions. (Effective only for files added from extra directories.) If not configured, permissions default to "755" for directories and "644" for files. See [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image) for an example.

<a name="path-object"></a>`path` is an object with the following properties (see [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)):

Property | Type | Default | Description
--- | --- | --- | ---
`from` | file | `[(project-dir)/src/main/jib]` | The source directory. Can be absolute or relative to the project root.
`into` | string | `/` | The absolute unix path on the container to copy the extra directory contents into.
`includes` | list | *None* | Glob patterns for including files. See [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image) for an example.
`excludes` | list | *None* | Glob patterns for excluding files. See [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image) for an example.

<a name="outputpaths-object"></a>`outputPaths` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`tar` | string | `(project-dir)/target/jib-image.tar` | The path of the tarball generated by `jib:buildTar`. Relative paths are resolved relative to the project root.
`digest` | string | `(project-dir)/target/jib-image.digest` | The path of the image digest written out during the build. Relative paths are resolved relative to the project root.
`imageId` | string | `(project-dir)/target/jib-image.id` | The path of the image ID written out during the build. Relative paths are resolved relative to the project root.
`imageJson` | string | `(project-dir)/target/jib-image.json` | The path of the image metadata json file written out during the build. Relative paths are resolved relative to the project root.

<a name="dockerclient-object"></a>`dockerClient` is an object used to configure Docker when building to/from the Docker daemon. It has the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`executable` | string | `docker` | Sets the path to the Docker executable that is called to load the image into the Docker daemon.
`environment` | map | *None* | Sets environment variables used by the Docker executable.

#### System Properties

Each of these parameters is configurable via commandline using system properties. Jib's system properties follow the same naming convention as the configuration parameters, with each level separated by dots (i.e. `-Djib.parameterName[.nestedParameter.[...]]=value`). Some examples are below:
```shell
mvn compile jib:build \
    -Djib.to.image=myregistry/myimage:latest \
    -Djib.to.auth.username=$USERNAME \
    -Djib.to.auth.password=$PASSWORD

mvn compile jib:dockerBuild \
    -Djib.dockerClient.executable=/path/to/docker \
    -Djib.container.environment=key1="value1",key2="value2" \
    -Djib.container.args=arg1,arg2,arg3
```

The following table contains additional system properties that are not available as build configuration parameters:

Property | Type | Default | Description
--- | --- | --- | ---
`jib.httpTimeout` | int | `20000` | HTTP connection/read timeout for registry interactions, in milliseconds. Use a value of `0` for an infinite timeout.
`jib.useOnlyProjectCache` | boolean | `false` | If set to true, Jib does not share a cache between different Maven projects (i.e. `jib.baseImageCache` defaults to `[project dir]/target/jib-cache` instead of `[user cache home]/google-cloud-tools-java/jib`).
`jib.baseImageCache` | string | *Platform-dependent*\*\*\* | Sets the directory to use for caching base image layers. This cache can (and should) be shared between multiple images.
`jib.applicationCache` | string | `[project dir]/target/jib-cache` | Sets the directory to use for caching application layers. This cache can be shared between multiple images.
`jib.console` | string | *None* | If set to `plain`, Jib will print plaintext log messages rather than display a progress bar during the build.

*\* If you configure `args` while `entrypoint` is set to `'INHERIT'`, the configured `args` value will take precedence over the CMD propagated from the base image.*

*\*\* Uses the main class defined in the `jar` task or tries to find a valid main class.*

*\*\*\* The default base image cache is in the following locations on each platform:*
 * *Linux: `[cache root]/google-cloud-tools-java/jib/`, where `[cache root]` is `$XDG_CACHE_HOME` (`$HOME/.cache/` if not set)*
 * *Mac: `[cache root]/Google/Jib/`, where `[cache root]` is `$XDG_CACHE_HOME` (`$HOME/Library/Caches/` if not set)*
 * *Windows: `[cache root]\Google\Jib\Cache`, where `[cache root]` is `$XDG_CACHE_HOME` (`%LOCALAPPDATA%` if not set)*

### Global Jib Configuration

Some options can be set in the global Jib configuration file. The file is at the following locations on each platform:

* *Linux: `[config root]/google-cloud-tools-java/jib/config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`$HOME/.config/` if not set)*
* *Mac: `[config root]/Google/Jib/config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`$HOME/Library/Preferences/Config/` if not set)*
* *Windows: `[config root]\Google\Jib\Config\config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`%LOCALAPPDATA%` if not set)*

#### Properties 

* `disableUpdateCheck`: when set to true, disables the periodic up-to-date version check.
* `registryMirrors`: a list of mirror settings for each base image registry. In the following example, if the base image configured in Jib is for a Docker Hub image, then `mirror.gcr.io`, `localhost:5000`, and the Docker Hub (`registry-1.docker.io`) are tried in order until Jib can successfuly pull a base image.

```json
{
  "disableUpdateCheck": false,
  "registryMirrors": [
    {
      "registry": "registry-1.docker.io",
      "mirrors": ["mirror.gcr.io", "localhost:5000"]
    },
    {
      "registry": "quay.io",
      "mirrors": ["private-mirror.test.com"]
    }
  ]
}
```
**Note about `mirror.gcr.io`**: it is _not_ a Docker Hub mirror but a cache. It caches [frequently-accessed public Docker Hub images](https://cloud.google.com/container-registry/docs/pulling-cached-images), and it's often possible that your base image does not exist in `mirror.gcr.io`. In that case, Jib will have to fall back to use Docker Hub.

### Example

In this configuration, the image:
* Is built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Is pushed to `localhost:5000/my-image:built-with-jib`, `localhost:5000/my-image:tag2`, and `localhost:5000/my-image:latest`
* Runs by calling `java -Dmy.property=example.value -Xms512m -Xdebug -cp app/libs/*:app/resources:app/classes mypackage.MyApp some args`
* Exposes port 1000 for tcp (default), and ports 2000, 2001, 2002, and 2003 for udp
* Has two labels (key1:value1 and key2:value2)
* Is built as OCI format

```xml
<configuration>
  <from>
    <image>openjdk:alpine</image>
  </from>
  <to>
    <image>localhost:5000/my-image:built-with-jib</image>
    <credHelper>osxkeychain</credHelper>
    <tags>
      <tag>tag2</tag>
      <tag>latest</tag>
    </tags>
  </to>
  <container>
    <jvmFlags>
      <jvmFlag>-Dmy.property=example.value</jvmFlag>
      <jvmFlag>-Xms512m</jvmFlag>
      <jvmFlag>-Xdebug</jvmFlag>
    </jvmFlags>
    <mainClass>mypackage.MyApp</mainClass>
    <args>
      <arg>some</arg>
      <arg>args</arg>
    </args>
    <ports>
      <port>1000</port>
      <port>2000-2003/udp</port>
    </ports>
    <labels>
      <key1>value1</key1>
      <key2>value2</key2>
    </labels>
    <format>OCI</format>
  </container>
</configuration>
```

### Setting the Base Image

There are three different types of base images that Jib accepts: an image from a container registry, an image stored in the Docker daemon, or an image tarball on the local filesystem. You can specify which you would like to use by prepending the `<from><image>` configuration with a special prefix, listed below:

Prefix | Example | Type
--- | --- | ---
*None* | `adoptopenjdk:11-jre` | Pulls the base image from a registry.
`registry://` | `registry://adoptopenjdk:11-jre` | Pulls the base image from a registry.
`docker://` | `docker://busybox` | Retrieves the base image from the Docker daemon.
`tar://` | `tar:///path/to/file.tar` | Uses an image tarball stored at the specified path as the base image. Also accepts relative paths (e.g. `tar://target/jib-image.tar`).

### Adding Arbitrary Files to the Image

You can add arbitrary, non-classpath files to the image by placing them in a `src/main/jib` directory. This will copy all files within the `jib` folder to the target directory (`/` by default) in the image, maintaining the same structure (e.g. if you have a text file at `src/main/jib/dir/hello.txt`, then your image will contain `/dir/hello.txt` after being built with Jib).

Note that Jib does not follow symbolic links in the container image.  If a symbolic link is present, _it will be removed_ prior to placing the files and directories.

You can configure different directories by using the `<extraDirectories>` parameter in your `pom.xml`:
```xml
<configuration>
  <!-- Copies files from 'src/main/custom-extra-dir' and '/home/user/jib-extras' instead of 'src/main/jib' -->
  <extraDirectories>
    <paths>
      <!-- Copies from 'src/main/custom-extra-dir' into '/' on the container. -->
      <path>src/main/custom-extra-dir</path>
      <!-- Copies from '/home/user/jib-extras' into '/extras' on the container -->
      <path>
        <from>/home/user/jib-extras</from>
        <into>/extras</into>
      </path>
    </paths>
  </extraDirectories>
</configuration>
```

Alternatively, the `<extraDirectories>` parameter can be used as an object to set custom extra directories, as well as the extra files' permissions on the container:

```xml
  <extraDirectories>
    <paths>src/main/custom-extra-dir</paths> <!-- Copies files from 'src/main/custom-extra-dir' -->
    <permissions>
      <permission>
        <file>/path/on/container/to/fileA</file>
        <mode>755</mode> <!-- Read/write/execute for owner, read/execute for group/other -->
      </permission>
      <permission>
        <file>/path/to/another/file</file>
        <mode>644</mode> <!-- Read/write for owner, read-only for group/other -->
      </permission>
      <permission>
        <file>/glob/pattern/**/*.sh</file>
        <mode>755</mode>
      </permission>
    </permissions>
  </extraDirectories>
```

You may also specify the target of the copy and include or exclude files:

```xml
  <extraDirectories>
    <paths>
      <path>
        // copies the contents of 'src/main/extra-dir' into '/' on the container
        <from>src/main/extra-dir</from>
      </path>
      <path>
        // copies the contents of 'src/main/another/dir' into '/extras' on the container
        <from>src/main/another/dir</from>
        <into>/extras</into>
      </path>
      <path>
        // copies a single-file.xml
        <from>src/main/resources/xml-files</from>
        <into>/dest-in-container</into>
        <includes>single-file.xml</includes>
      </path>
      <path>
        // copies only .txt files except for 'hidden.txt' at the source root
        <from>build/some-output</from>
        <into>/txt-files</into>
        <includes>*.txt,**/*.txt</includes>
        <excludes>
          <exclude>hidden.txt</exclude>
        </excludes>
      </path>
    </paths>
  </extraDirectories>
```

### Authentication Methods

Pushing/pulling from private registries require authorization credentials. These can be [retrieved using Docker credential helpers](#using-docker-credential-helpers) or [defined in your Maven settings](#using-maven-settings). If you do not define credentials explicitly, Jib will try to [use credentials defined in your Docker config](/../../issues/101) or infer common credential helpers.

#### Using Docker Credential Helpers

Docker credential helpers are CLI tools that handle authentication with various registries.

Some common credential helpers include:

* Google Container Registry: [`docker-credential-gcr`](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper)
* AWS Elastic Container Registry: [`docker-credential-ecr-login`](https://github.com/awslabs/amazon-ecr-credential-helper)
* Docker Hub Registry: [`docker-credential-*`](https://github.com/docker/docker-credential-helpers)
* Azure Container Registry: [`docker-credential-acr-*`](https://github.com/Azure/acr-docker-credential-helper)

Configure credential helpers to use by specifying them as a `credHelper` for their respective image.

*Example configuration:*
```xml
<configuration>
  ...
  <from>
    <image>aws_account_id.dkr.ecr.region.amazonaws.com/my-base-image</image>
    <credHelper>ecr-login</credHelper>
  </from>
  <to>
    <image>gcr.io/my-gcp-project/my-app</image>
    <credHelper>gcr</credHelper>
  </to>
  ...
</configuration>
```

#### Using Specific Credentials

You can specify credentials directly in the `<auth>` parameter for the `from` and/or `to` images. In the example below, `to` credentials are retrieved from the `REGISTRY_USERNAME` and `REGISTRY_PASSWORD` environment variables.

```xml
<configuration>
  ...
  <from>
    <image>aws_account_id.dkr.ecr.region.amazonaws.com/my-base-image</image>
    <auth>
      <username>my_username</username>
      <password>my_password</password>
    </auth>
  </from>
  <to>
    <image>gcr.io/my-gcp-project/my-app</image>
    <auth>
      <username>${env.REGISTRY_USERNAME}</username>
      <password>${env.REGISTRY_PASSWORD}</password>
    </auth>
  </to>
  ...
</configuration>
```

Alternatively, you can specify credentials via commandline using the following system properties.

Property | Description
--- | ---
`-Djib.from.auth.username` | Username for base image registry.
`-Djib.from.auth.password` | Password for base image registry.
`-Djib.to.auth.username` | Username for target image registry.
`-Djib.to.auth.password` | Password for target image registry.

e.g. `mvn compile jib:build -Djib.to.auth.username=user -Djib.to.auth.password=pass`

**Note:** This method of authentication should be used only as a last resort, as it is insecure to make your password visible in plain text. Note that often cloud registries (for example, Google GCR, Amazon ECR, and Azure ACR) do not accept "user credentials" (such as Gmail account name and password) but require different forms of credentials. For example, you may use [`oauth2accesstoken` or `_json_key`](https://cloud.google.com/container-registry/docs/advanced-authentication) as the username for GCR, and [`AWS`](https://serverfault.com/questions/1004915/what-is-the-proper-way-to-log-in-to-ecr) for ECR. For ACR, you may use a [_service principle_](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-auth-service-principal).

#### Using Maven Settings

Registry credentials can be added to your [Maven settings](https://maven.apache.org/settings.html). These credentials will be used if credentials could not be found in any specified Docker credential helpers.

If you're considering putting credentials in Maven, we highly *recommend* using [maven password encryption](https://maven.apache.org/guides/mini/guide-encryption.html).

*Example `settings.xml`:*
```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>MY_REGISTRY</id>
      <username>MY_USERNAME</username>
      <password>{MY_SECRET}</password>
    </server>
  </servers>
</settings>
```

* The `id` field should be the registry server these credentials are for.
* We *do not* recommend putting your raw password in `settings.xml`.


### Custom Container Entrypoint

If you don't set `<container><entrypoint>`, the default container entrypoint to launch your app will be basically `java -cp <runtime classpath> <app main class>`. (The final `java` command can be further configured by setting `<container>{<jvmFlags>|<args>|<extraClasspath>|<mainClass>|<expandClasspathDependencies>}`.)

Sometimes, you'll want to set a custom entrypoint to use a shell to wrap the `java` command. For example, to let `sh` or `bash` [expand environment variables](https://stackoverflow.com/a/59361658/1701388), or to have more sophisticated logic to construct a launch command. (Note, however, that running a command with a shell forks a new child process unless you run it with `exec` like `sh -c "exec java ..."`. Whether to run the JVM process as PID 1 or a child process of a PID-1 shell is a [decision you should make carefully](https://github.com/GoogleContainerTools/distroless/issues/550#issuecomment-791610603).) In this scenario, you will want to have a way inside a shell script to reliably know the default runtime classpath and the main class that Jib would use by default. To help this, Jib >= 3.1 creates two JVM argument files under `/app` (the default app root) inside the built image.

- `/app/jib-classpath-file`: runtime classpath that Jib would use for default app launch
- `/app/jib-main-class-file`: main class

Therefore, *for example*, the following commands will be able to launch your app:

- (Java 9+) `java -cp @/app/jib-classpath-file @/app/jib-main-class-file`
- (with shell) `java -cp $( cat /app/jib-classpath-file ) $( cat /app/jib-main-class-file )`


### Jib Extensions

The Jib build plugins have an extension framework that enables anyone to easily extend Jib's behavior to their needs. We maintain select [first-party](https://github.com/GoogleContainerTools/jib-extensions/tree/master/first-party) plugins for popular use cases like [fine-grained layer control](https://github.com/GoogleContainerTools/jib-extensions/tree/master/first-party/jib-layer-filter-extension-gradle), builds a [GraalVM native image](https://github.com/GoogleContainerTools/jib-extensions/tree/master/first-party/jib-native-image-extension-maven), and [Quarkus support](https://github.com/GoogleContainerTools/jib-extensions/tree/master/first-party/jib-quarkus-extension-gradle), but anyone can write and publish an extension. Check out the [jib-extensions](https://github.com/GoogleContainerTools/jib-extensions) repository for more information.


### WAR Projects

Jib also containerizes WAR projects. If the Maven project uses [the `war`-packaging type](https://maven.apache.org/plugins/maven-war-plugin/index.html), Jib will by default use [`jetty`](https://hub.docker.com/_/jetty) as a base image to deploy the project WAR. No extra configuration is necessary other than having the packaging type to `war`.

Note that Jib will work slightly differently for WAR projects from JAR projects:
   - `<container><mainClass>` and `<container><jvmFlags>` are ignored.
   - The WAR will be exploded into `/var/lib/jetty/webapps/ROOT`, which is the expected WAR location for the Jetty base image.

To use a different Servlet engine base image, you can customize `<container><appRoot>`, `<container><entrypoint>`, and `<container><args>`. If you do not set `entrypoint` or `args`, Jib will inherit the `ENTRYPOINT` and `CMD` of the base image, so in many cases, you may not need to configure them. However, you will most likely have to set `<container><appRoot>` to a proper location depending on the base image. Here is an example of using a Tomcat image:

```xml
<configuration>
  <from>
    <image>tomcat:8.5-jre8-alpine</image>
  </from>
  <container>
    <!--
      For demonstration only: this directory in the base image contains a Tomcat default
      app (welcome page), so you may first want to delete this directory in the base image.
    -->
    <appRoot>/usr/local/tomcat/webapps/ROOT</appRoot>
  </container>
</configuration>
```
When specifying a [`jetty`](https://hub.docker.com/_/jetty) image yourself with `<from><image>`, you may run into an issue ([#3204](https://github.com/GoogleContainerTools/jib/issues/3204)) and need to override the entrypoint.
```xml
<configuration>
  <from>
    <image>jetty:11.0.2-jre11</image>
  </from>
  <container>
    <entrypoint>java,-jar,/usr/local/jetty/start.jar</entrypoint>
  </container>
</configuration>
```


### Skaffold Integration

Jib is an included builder in [Skaffold](https://github.com/GoogleContainerTools/skaffold). Jib passes build information to skaffold through special internal goals so that skaffold understands when it should rebuild or synchronize files. For complex builds, the defaults may not be sufficient, so the jib plugin provides a `skaffold` configuration object which exposes:

Field | Type | Default | Description
--- | --- | --- | ---
`watch` | [`watch`](#skaffold-watch-object) | *None* | Additional configuration for file watching
`sync` | [`sync`](#skaffold-sync-object) | *None* | Additional configuration for file synchronization

<a name="skaffold-watch-object"></a>`watch` is an object with the following properties:

Field | Type | Default | Description
--- | --- | --- | ---
`buildIncludes` | `List<String>` | *None* | Additional build files that skaffold should watch
`includes` | `List<String>` | *None* | Additional project files or directories that skaffold should watch
`excludes` | `List<String>` | *None* | Files and directories that skaffold should not watch

<a name="skaffold-sync-object"></a>`sync` is an object with the following properties:

Field | Type | Default | Description
--- | --- | --- | ---
`excludes` | `List<String>` | *None* | Files and directories that skaffold should not sync

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Privacy

See the [Privacy page](docs/privacy.md).

## Upcoming Features

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Community

See the [Jib project README](/../../#community).
