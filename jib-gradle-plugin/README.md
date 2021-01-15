![stable](https://img.shields.io/badge/stability-stable-brightgreen.svg)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/google/cloud/tools/jib/com.google.cloud.tools.jib.gradle.plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle)](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib - Containerize your Gradle Java project

Jib is a [Gradle](https://gradle.org/) plugin for building Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications.

For the Maven plugin, see the [jib-maven-plugin project](../jib-maven-plugin).

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
  * [Run `jib` with each build](#run-jib-with-each-build)
  * [Additional Build Artifacts](#additional-build-artifacts)
* [Multi Module Projects](#multi-module-projects)
* [Extended Usage](#extended-usage)
  * [System Properties](#system-properties)
  * [Example](#example)
  * [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)
  * [Authentication Methods](#authentication-methods)
    * [Using Docker Credential Helpers](#using-docker-credential-helpers)
    * [Using Specific Credentials](#using-specific-credentials)
  * [WAR Projects](#war-projects)
  * [Skaffold Integration](#skaffold-integration)
* [Frequently Asked Questions (FAQ)](#frequently-asked-questions-faq)
* [Community](#community)

## Quickstart

### Setup

*Make sure you are using Gradle version 5.1 or later.*

In your Gradle Java project, add the plugin to your `build.gradle`:

```groovy
plugins {
  id 'com.google.cloud.tools.jib' version '2.7.1'
}
```

*See the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib) for more details.*

You can containerize your application easily with one command:

```shell
gradle jib --image=<MY IMAGE>
```

This builds and pushes a container image for your application to a container registry. *If you encounter authentication issues, see [Authentication Methods](#authentication-methods).*

To build to a Docker daemon, use:

```shell
gradle jibDockerBuild
```

If you would like to set up Jib as part of your Gradle build, follow the guide below.

## Configuration

Configure the plugin by setting the image to push to:

#### Using [Google Container Registry (GCR)](https://cloud.google.com/container-registry/)...

*Make sure you have the [`docker-credential-gcr` command line tool](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper). Jib automatically uses `docker-credential-gcr` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `gcr.io/my-gcp-project/my-app`, the configuration would be:

```groovy
jib.to.image = 'gcr.io/my-gcp-project/my-app'
```

#### Using [Amazon Elastic Container Registry (ECR)](https://aws.amazon.com/ecr/)...

*Make sure you have the [`docker-credential-ecr-login` command line tool](https://github.com/awslabs/amazon-ecr-credential-helper). Jib automatically uses `docker-credential-ecr-login` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `aws_account_id.dkr.ecr.region.amazonaws.com/my-app`, the configuration would be:

```groovy
jib.to.image = 'aws_account_id.dkr.ecr.region.amazonaws.com/my-app'
```

#### Using [Docker Hub Registry](https://hub.docker.com/)...

*Make sure you have a [docker-credential-helper](https://github.com/docker/docker-credential-helpers#available-programs) set up. For example, on macOS, the credential helper would be `docker-credential-osxkeychain`. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `my-docker-id/my-app`, the configuration would be:

```groovy
jib.to.image = 'my-docker-id/my-app'
```

#### Using [Azure Container Registry (ACR)](https://azure.microsoft.com/en-us/services/container-registry/)...

*Make sure you have a [`ACR Docker Credential Helper`](https://github.com/Azure/acr-docker-credential-helper) installed and set up. For example, on Windows, the credential helper would be `docker-credential-acr-windows`. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `my_acr_name.azurecr.io/my-app`, the configuration would be:

```groovy
jib.to.image = 'my_acr_name.azurecr.io/my-app'
```

### Build Your Image

Build your container image with:

```shell
gradle jib
```

Subsequent builds are much faster than the initial build.

*Having trouble? Let us know by [submitting an issue](/../../issues/new), contacting us on [Gitter](https://gitter.im/google/jib), or posting to the [Jib users forum](https://groups.google.com/forum/#!forum/jib-users).*

#### Build to Docker daemon

Jib can also build your image directly to a Docker daemon. This uses the `docker` command line tool and requires that you have `docker` available on your `PATH`.

```shell
gradle jibDockerBuild
```

If you are using [`minikube`](https://github.com/kubernetes/minikube)'s remote Docker daemon, make sure you [set up the correct environment variables](https://github.com/kubernetes/minikube/blob/master/docs/reusing_the_docker_daemon.md) to point to the remote daemon:

```shell
eval $(minikube docker-env)
gradle jibDockerBuild
```

Alternatively, you can set environment variables in the Jib configuration. See [`dockerClient`](#dockerclient-closure) for more configuration options.

#### Build an image tarball

You can build and save your image to disk as a tarball with:

```shell
gradle jibBuildTar
```

This builds and saves your image to `build/jib-image.tar`, which you can load into docker with:

```shell
docker load --input build/jib-image.tar
```

### Run `jib` with each build

You can also have `jib` run with each build by attaching it to the `build` task:

```groovy
tasks.build.dependsOn tasks.jib
```

Then, ```gradle build``` will build and containerize your application.

### Additional Build Artifacts

As part of an image build, Jib also writes out the _image digest_ and the _image ID_. By default, these are written out to `build/jib-image.digest` and `build/jib-image.id` respectively, but the locations can be configured using the `jib.outputFiles.digest` and `jib.outputFiles.imageId` configuration properties. See [Extended Usage](#outputpaths-closure) for more details.

## Multi Module Projects

Special handling of project dependencies is recommended when building complex
multi module projects. See [Multi Module Example](https://github.com/GoogleContainerTools/jib/tree/master/examples/multi-module) for detailed information.

## Extended Usage

The plugin provides the `jib` extension for configuration with the following options for customizing the image build:

Field | Type | Default | Description
--- | --- | --- | ---
`to` | [`to`](#to-closure) | *Required* | Configures the target image to build your application to.
`from` | [`from`](#from-closure) | See [`from`](#from-closure) | Configures the base image to build your application on top of.
`container` | [`container`](#container-closure) | See [`container`](#container-closure) | Configures the container that is run from your built image.
`extraDirectories` | [`extraDirectories`](#extradirectories-closure) | See [`extraDirectories`](#extradirectories-closure) | Configures the directories used to add arbitrary files to the image.
`outputPaths` | [`outputPaths`](#outputpaths-closure) | See [`outputPaths`](#outputpaths-closure) | Configures the locations of additional build artifacts generated by Jib.
`dockerClient` | [`dockerClient`](#dockerclient-closure) | See [`dockerClient`](#dockerclient-closure) | Configures Docker for building to/from the Docker daemon.
`skaffold` | [`skaffold`](#skaffold-integration) | See [`skaffold`](#skaffold-integration) | Configures the internal skaffold tasks. This configuration should only be used when integrating with [`skaffold`](#skaffold-integration). |
`containerizingMode` | `String` | `exploded` | If set to `packaged`, puts the JAR artifact built by the Gradle Java plugin into the final image. If set to `exploded` (default), containerizes individual `.class` files and resources files.
`allowInsecureRegistries` | `boolean` | `false` | If set to true, Jib ignores HTTPS certificate errors and may fall back to HTTP as a last resort. Leaving this parameter set to `false` is strongly recommended, since HTTP communication is unencrypted and visible to others on the network, and insecure HTTPS is no better than plain HTTP. [If accessing a registry with a self-signed certificate, adding the certificate to your Java runtime's trusted keys](https://github.com/GoogleContainerTools/jib/tree/master/docs/self_sign_cert.md) may be an alternative to enabling this option.

<a name="from-closure"></a>`from` is a closure with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | `String` | `gcr.io/distroless/java` | The image reference for the base image. The source type can be specified using a [special type prefix](#setting-the-base-image).
`auth` | [`auth`](#auth-closure) | *None* | Specifies credentials directly (alternative to `credHelper`).
`credHelper` | `String` | *None* | Specifies a credential helper that can authenticate pulling the base image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).
`platforms` | [`platforms`](#platforms-closure) | See [`platforms`](#platforms-closure) | _Incubating feature_: Configures platforms of base images to select from a manifest list.

<a name="to-closure"></a>`to` is a closure with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | `String` | *Required* | The image reference for the target image. This can also be specified via the `--image` command line option.
`auth` | [`auth`](#auth-closure) | *None* | Specifies credentials directly (alternative to `credHelper`).
`credHelper` | `String` | *None* | Specifies a credential helper that can authenticate pushing the target image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).
`tags` | `List<String>` | *None* | Additional tags to push to.

<a name="auth-closure"></a>`auth` is a closure with the following properties (see [Using Specific Credentials](#using-specific-credentials)):

Property | Type
--- | ---
`username` | `String`
`password` | `String`

<a name="platforms-closure"></a>`platforms` can configure multiple `platform` closures.  Each individual `platform` has the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`architecture` | `String` | `amd64` | The architecture of a base image to select from a manifest list.
`os` | `String` | `linux` | The OS of a base image to select from a manifest list.

See [How do I specify a platform in the manifest list (or OCI index) of a base image?](../docs/faq.md#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image) for examples.

<a name="container-closure"></a>`container` is a closure with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`appRoot` | `String` | `/app` | The root directory on the container where the app's contents are placed. Particularly useful for WAR-packaging projects to work with different Servlet engine base images by designating where to put exploded WAR contents; see [WAR usage](#war-projects) as an example.
`args` | `List<String>` | *None* | Additional program arguments appended to the command to start the container (similar to Docker's [CMD](https://docs.docker.com/engine/reference/builder/#cmd) instruction in relation with [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint)). In the default case where you do not set a custom `entrypoint`, this parameter is effectively the arguments to the main method of your Java application.
`creationTime` | `String` | `EPOCH` | Sets the container creation time. (Note that this property does not affect the file modification times, which are configured using `jib.container.filesModificationTime`.) The value can be `EPOCH` to set the timestamps to Epoch (default behavior), `USE_CURRENT_TIMESTAMP` to forgo reproducibility and use the real creation time, or an ISO 8601 date-time parsable with [`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME) such as `2019-07-15T10:15:30+09:00` or `2011-12-03T22:42:05Z`.
`entrypoint` | `List<String>` | *None* | The command to start the container with (similar to Docker's [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint) instruction). If set, then `jvmFlags`, `mainClass`, `extraClasspath`, and `expandClasspathDependencies` are ignored. You may also set `jib.container.entrypoint = 'INHERIT'` to indicate that the `entrypoint` and `args` should be inherited from the base image.\*
`environment` | `Map<String, String>` | *None* | Key-value pairs for setting environment variables on the container (similar to Docker's [ENV](https://docs.docker.com/engine/reference/builder/#env) instruction).
`extraClasspath` | `List<String>` | *None* | Additional paths in the container to prepend to the computed Java classpath.
`expandClasspathDependencies` | `boolean` | `false` | When set to true, does not use a wildcard (for example, `/app/lib/*`) for dependency JARs in the default Java runtime classpath but instead enumerates the JARs. Has the effect of preserving the classpath loading order as defined by the Gradle project.
`filesModificationTime` | `String` | `EPOCH_PLUS_SECOND` | Sets the modification time (last modified time) of files in the image put by Jib. (Note that this does not set the image creation time, which can be set using `jib.container.creationTime`.) The value should either be `EPOCH_PLUS_SECOND` to set the timestamps to Epoch + 1 second (default behavior), or an ISO 8601 date-time parsable with [`DateTimeFormatter.ISO_DATE_TIME`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME) such as `2019-07-15T10:15:30+09:00` or `2011-12-03T22:42:05Z`.
`format` | `String` | `Docker` | Use `OCI` to build an [OCI container image](https://www.opencontainers.org/).
`jvmFlags` | `List<String>` | *None* | Additional flags to pass into the JVM when running your application.
`labels` | `Map<String, String>` | *None* | Key-value pairs for applying image metadata (similar to Docker's [LABEL](https://docs.docker.com/engine/reference/builder/#label) instruction).
`mainClass` | `String` | *Inferred*\*\* | The main class to launch your application from.
`ports` | `List<String>` | *None* | Ports that the container exposes at runtime (similar to Docker's [EXPOSE](https://docs.docker.com/engine/reference/builder/#expose) instruction).
`user` | `String` | *None* | The user and group to run the container as. The value can be a username or UID along with an optional groupname or GID. The following are all valid: `user`, `uid`, `user:group`, `uid:gid`, `uid:group`, `user:gid`.
`volumes` | `List<String>` | *None* | Specifies a list of mount points on the container.
`workingDirectory` | `String` | *None* | The working directory in the container.

<a name="extradirectories-closure"></a>`extraDirectories` is a closure with the following properties (see [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)):

Property | Type | Default | Description
--- | --- | --- | ---
`paths` | [`paths`](#paths-closure) closure, or `Object` | `(project-dir)/src/main/jib` | May be configured as a closure configuring `path` elements, or as source directory values recognized by [`Project.files()`](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html#files-java.lang.Object...-), such as `String`, `File`, `Path`, `List<String\|File\|Path>`, etc.
`permissions` | `Map<String, String>` | *None* | Maps file paths (glob patterns) on container to Unix permissions. (Effective only for files added from extra directories.) If not configured, permissions default to "755" for directories and "644" for files. See [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image) for an example.

<a name="paths-closure"></a>`paths` can configure multiple `path` closures (see [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)). Each individual `path` has the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`from` | `Object` | `(project-dir)/src/main/jib` | Accepts source directories that are recognized by [`Project.files()`](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html#files-java.lang.Object...-), such as `String`, `File`, `Path`, `List<String\|File\|Path>`, etc.
`into` | `String` | `/` | The absolute unix path on the container to copy the extra directory contents into.

<a name="outputpaths-closure"></a>`outputPaths` is a closure with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`tar` | `File` | `(project-dir)/build/jib-image.tar` | The path of the tarball generated by `jibBuildTar`. Relative paths are resolved relative to the project root.
`digest` | `File` | `(project-dir)/build/jib-image.digest` | The path of the image digest written out during the build. Relative paths are resolved relative to the project root.
`imageId` | `File` | `(project-dir)/build/jib-image.id` | The path of the image ID written out during the build. Relative paths are resolved relative to the project root.

<a name="dockerclient-closure"></a>`dockerClient` is an object used to configure Docker when building to/from the Docker daemon. It has the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`executable` | `File` | `docker` | Sets the path to the Docker executable that is called to load the image into the Docker daemon.
`environment` | `Map<String, String>` | *None* | Sets environment variables used by the Docker executable.

#### System Properties

Each of these parameters is configurable via commandline using system properties. Jib's system properties follow the same naming convention as the configuration parameters, with each level separated by dots (i.e. `-Djib.parameterName[.nestedParameter.[...]]=value`). Some examples are below:
```shell
gradle jib \
    -Djib.to.image=myregistry/myimage:latest \
    -Djib.to.auth.username=$USERNAME \
    -Djib.to.auth.password=$PASSWORD

gradle jibDockerBuild \
    -Djib.dockerClient.executable=/path/to/docker \
    -Djib.container.environment=key1="value1",key2="value2" \
    -Djib.container.args=arg1,arg2,arg3
```

The following table contains additional system properties that are not available as build configuration parameters:

Property | Type | Default | Description
--- | --- | --- | ---
`jib.httpTimeout` | `int` | `20000` | HTTP connection/read timeout for registry interactions, in milliseconds. Use a value of `0` for an infinite timeout.
`jib.useOnlyProjectCache` | `boolean` | `false` | If set to true, Jib does not share a cache between different Maven projects.
`jib.baseImageCache` | `File` | *Platform-dependent*\*\*\* | Sets the directory to use for caching base image layers. This cache can (and should) be shared between multiple images.
`jib.applicationCache` | `File` | `[project dir]/build/jib-cache` | Sets the directory to use for caching application layers. This cache can be shared between multiple images.
`jib.console` | `String` | *None* | If set to `plain`, Jib will print plaintext log messages rather than display a progress bar during the build.

*\* If you configure `args` while `entrypoint` is set to `'INHERIT'`, the configured `args` value will take precedence over the CMD propagated from the base image.*

*\*\* Uses the main class defined in the `jar` task or tries to find a valid main class.*

*\*\*\* The default base image cache is in the following locations on each platform:*
 * *Linux: `[cache root]/google-cloud-tools-java/jib/`, where `[cache root]` is `$XDG_CACHE_HOME` (`$HOME/.cache/` if not set)*
 * *Mac: `[cache root]/Google/Jib/`, where `[cache root]` is `$XDG_CACHE_HOME` (`$HOME/Library/Caches/` if not set)*
 * *Windows: `[cache root]\Google\Jib\Cache`, where `[cache root]` is `$XDG_CACHE_HOME` (`%LOCALAPPDATA%` if not set)*

### Example

In this configuration, the image:
* Is built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Is pushed to `localhost:5000/my-image:built-with-jib`, `localhost:5000/my-image:tag2`, and `localhost:5000/my-image:latest`
* Runs by calling `java -Dmy.property=example.value -Xms512m -Xdebug -cp app/libs/*:app/resources:app/classes mypackage.MyApp some args`
* Exposes port 1000 for tcp (default), and ports 2000, 2001, 2002, and 2003 for udp
* Has two labels (key1:value1 and key2:value2)
* Is built as OCI format

```groovy
jib {
  from {
    image = 'openjdk:alpine'
  }
  to {
    image = 'localhost:5000/my-image/built-with-jib'
    credHelper = 'osxkeychain'
    tags = ['tag2', 'latest']
  }
  container {
    jvmFlags = ['-Dmy.property=example.value', '-Xms512m', '-Xdebug']
    mainClass = 'mypackage.MyApp'
    args = ['some', 'args']
    ports = ['1000', '2000-2003/udp']
    labels = [key1:'value1', key2:'value2']
    format = 'OCI'
  }
}
```

### Setting the Base Image

There are three different types of base images that Jib accepts: an image from a container registry, an image stored in the Docker daemon, or an image tarball on the local filesystem. You can specify which you would like to use by prepending the `jib.from.image` configuration with a special prefix, listed below:

Prefix | Example | Type
--- | --- | ---
*None* | `gcr.io/distroless/java` | Pulls the base image from a registry.
`registry://` | `registry://gcr.io/distroless/java` | Pulls the base image from a registry.
`docker://` | `docker://busybox` | Retrieves the base image from the Docker daemon.
`tar://` | `tar:///path/to/file.tar` | Uses an image tarball stored at the specified path as the base image. Also accepts relative paths (e.g. `tar://build/jib-image.tar`).

### Adding Arbitrary Files to the Image

You can add arbitrary, non-classpath files to the image without extra configuration by placing them in a `src/main/jib` directory. This will copy all files within the `jib` folder to the target directory (`/` by default) in the image, maintaining the same structure (e.g. if you have a text file at `src/main/jib/dir/hello.txt`, then your image will contain `/dir/hello.txt` after being built with Jib).

Note that Jib does not follow symbolic links in the container image.  If a symbolic link is present, _it will be removed_ prior to placing the files and directories.

You can configure different directories by using the `jib.extraDirectories.paths` parameter in your `build.gradle`:
```groovy
jib {
  // Copies files from 'src/main/custom-extra-dir' and '/home/user/jib-extras' instead of 'src/main/jib'
  extraDirectories.paths = ['src/main/custom-extra-dir', '/home/user/jib-extras']
}
```

Alternatively, the `jib.extraDirectories` parameter can be used as a closure to set custom extra directories, as well as the extra files' permissions on the container:

```groovy
jib {
  extraDirectories {
    paths = 'src/main/custom-extra-dir'  // Copies files from 'src/main/custom-extra-dir'
    permissions = [
        '/path/on/container/to/fileA': '755',  // Read/write/execute for owner, read/execute for group/other
        '/path/to/another/file': '644',  // Read/write for owner, read-only for group/other
        '/glob/pattern/**/*.sh': 755
    ]
  }
}
```

You may also specify the target of the copy using `paths` as a closure:

```groovy
  extraDirectories {
    paths {
      path {
        // copies the contents of 'src/main/extra-dir' into '/' on the container
        from = file('src/main/extra-dir')
      }
      path {
        // copies the contents of 'src/main/another/dir' into '/extras' on the container
        from = file('src/main/another/dir')
        into = '/extras'
      }
    }
  }
```

### Authentication Methods

Pushing/pulling from private registries require authorization credentials. These can be [retrieved using Docker credential helpers](#using-docker-credential-helpers)<!-- or in the `jib` extension-->. If you do not define credentials explicitly, Jib will try to [use credentials defined in your Docker config](/../../issues/101) or infer common credential helpers.

#### Using Docker Credential Helpers

Docker credential helpers are CLI tools that handle authentication with various registries.

Some common credential helpers include:

* Google Container Registry: [`docker-credential-gcr`](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper)
* AWS Elastic Container Registry: [`docker-credential-ecr-login`](https://github.com/awslabs/amazon-ecr-credential-helper)
* Docker Hub Registry: [`docker-credential-*`](https://github.com/docker/docker-credential-helpers)
* Azure Container Registry: [`docker-credential-acr-*`](https://github.com/Azure/acr-docker-credential-helper)

Configure credential helpers to use by specifying them as a `credHelper` for their respective image in the `jib` extension.

*Example configuration:*
```groovy
jib {
  from {
    image = 'aws_account_id.dkr.ecr.region.amazonaws.com/my-base-image'
    credHelper = 'ecr-login'
  }
  to {
    image = 'gcr.io/my-gcp-project/my-app'
    credHelper = 'gcr'
  }
}
```

#### Using Specific Credentials

You can specify credentials directly in the extension for the `from` and/or `to` images.

```groovy
jib {
  from {
    image = 'aws_account_id.dkr.ecr.region.amazonaws.com/my-base-image'
    auth {
      username = USERNAME // Defined in 'gradle.properties'.
      password = PASSWORD
    }
  }
  to {
    image = 'gcr.io/my-gcp-project/my-app'
    auth {
      username = 'oauth2accesstoken'
      password = 'gcloud auth print-access-token'.execute().text.trim()
    }
  }
}
```

These credentials can be stored in `gradle.properties`, retrieved from a command (like `gcloud auth print-access-token`), or read in from a file.

For example, you can use a key file for authentication (for GCR, see [Using a JSON key file](https://cloud.google.com/container-registry/docs/advanced-authentication#using_a_json_key_file)):

```groovy
jib {
  to {
    image = 'gcr.io/my-gcp-project/my-app'
    auth {
      username = '_json_key'
      password = file('keyfile.json').text
    }
  }
}
```

### WAR Projects

Jib also containerizes WAR projects. If the Gradle project uses the [WAR Plugin](https://docs.gradle.org/current/userguide/war_plugin.html), Jib will by default use the [distroless Jetty](https://github.com/GoogleContainerTools/distroless/tree/master/java/jetty) as a base image to deploy the project WAR. No extra configuration is necessary other than using the WAR Plugin to make Jib build WAR images.

Note that Jib will work slightly differently for WAR projects from JAR projects:
   - `container.mainClass` and `container.jvmFlags` are ignored.
   - The WAR will be exploded into `/jetty/webapps/ROOT`, which is the expected WAR location for the distroless Jetty base image.

To use a different Servlet engine base image, you can customize `container.appRoot`, `container.entrypoint`, and `container.args`. If you do not set `entrypoint` or `args`, Jib will inherit the `ENTRYPOINT` and `CMD` of the base image, so in many cases, you may not need to configure them. However, you will most likely have to set `container.appRoot` to a proper location depending on the base image. Here is an example of using a Tomcat image:

```gradle
jib {
  from.image = 'tomcat:8.5-jre8-alpine'

  // For demonstration only: this directory in the base image contains a Tomcat default
  // app (welcome page), so you may first want to delete this directory in the base image.
  container.appRoot = '/usr/local/tomcat/webapps/ROOT'
}
```

### Skaffold Integration

Jib is an included builder in [Skaffold](https://github.com/GoogleContainerTools/skaffold). Jib passes build information to skaffold through special internal tasks so that skaffold understands when it should rebuild or synchronize files. For complex builds, the defaults may not be sufficient, so the `jib` extension provides a `skaffold` configuration closure which exposes:

Field | Type | Default | Description
--- | --- | --- | ---
`watch` | [`watch`](#skaffold-watch-closure) | *None* | Additional configuration for file watching
`sync` | [`sync`](#skaffold-sync-closure) | *None* | Additional configuration for file synchronization

<a name="skaffold-watch-closure"></a>`watch` is a closure with the following properties:

Field | Type | Default | Description
--- | --- | --- | ---
`buildIncludes` | `List<String>` | *None* | Additional build files that skaffold should watch
`includes` | `List<String>` | *None* | Additional project files or directories that skaffold should watch
`excludes` | `List<String>` | *None* | Files and directories that skaffold should not watch

<a name="skaffold-sync-closure"></a>`sync` is a closure with the following properties:

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
