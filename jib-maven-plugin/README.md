![stable](https://img.shields.io/badge/stability-stable-brightgreen.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib - Containerize your Maven project

Jib is a [Maven](https://maven.apache.org/) plugin for building Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications.

For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

For information about the project, see the [Jib project README](../README.md).

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
* [Extended Usage](#extended-usage)
  * [System Properties](#system-properties)
  * [Example](#example)
  * [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)
  * [Authentication Methods](#authentication-methods)
    * [Using Docker Credential Helpers](#using-docker-credential-helpers)
    * [Using Specific Credentials](#using-specific-credentials)
  * [WAR Projects](#war-projects)
* [Frequently Asked Questions (FAQ)](#frequently-asked-questions-faq)
* [Community](#community)

## Quickstart

You can containerize your application easily with one command:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:1.1.2:build -Dimage=<MY IMAGE>
```

This builds and pushes a container image for your application to a container registry. *If you encounter authentication issues, see [Authentication Methods](#authentication-methods).*

To build to a Docker daemon, use:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:1.1.2:dockerBuild
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
        <version>1.1.2</version>
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

If you are using [`minikube`](https://github.com/kubernetes/minikube)'s remote Docker daemon, make sure you [set up the correct environment variables](https://github.com/kubernetes/minikube/blob/master/docs/reusing_the_docker_daemon.md) to point to the remote daemon:

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
  <groupId>com.google.com.tools</groupId>
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

As part of an image build, Jib also writes out the _image digest_ to
`target/jib-image.digest`, as well as the _image ID_ to `target/jib-image.id`.

## Extended Usage

Extended configuration options provide additional options for customizing the image build.

Field | Type | Default | Description
--- | --- | --- | ---
`to` | [`to`](#to-object) | *Required* | Configures the target image to build your application to.
`from` | [`from`](#from-object) | See [`from`](#from-object) | Configures the base image to build your application on top of.
`container` | [`container`](#container-object) | See [`container`](#container-object) | Configures the container that is run from your image.
`extraDirectory` | [`extraDirectory`](#extradirectory-object) / string | `(project-dir)/src/main/jib` | Configures the directory used to add arbitrary files to the image.
`allowInsecureRegistries` | boolean | `false` | If set to true, Jib ignores HTTPS certificate errors and may fall back to HTTP as a last resort. Leaving this parameter set to `false` is strongly recommended, since HTTP communication is unencrypted and visible to others on the network, and insecure HTTPS is no better than plain HTTP. [If accessing a registry with a self-signed certificate, adding the certificate to your Java runtime's trusted keys](https://github.com/GoogleContainerTools/jib/tree/master/docs/self_sign_cert.md) may be an alternative to enabling this option.
`skip` | boolean | `false` | If set to true, Jib execution is skipped (useful for multi-module projects). This can also be specified via the `-Djib.skip` command line option.

<a name="from-object"></a>`from` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | `gcr.io/distroless/java` | The image reference for the base image.
`auth` | [`auth`](#auth-object) | *None* | Specify credentials directly (alternative to `credHelper`).
`credHelper` | string | *None* | Specifies a credential helper that can authenticate pulling the base image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).

<a name="to-object"></a>`to` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | *Required* | The image reference for the target image. This can also be specified via the `-Dimage` command line option.
`auth` | [`auth`](#auth-object) | *None* | Specify credentials directly (alternative to `credHelper`).
`credHelper` | string | *None* | Specifies a credential helper that can authenticate pushing the target image. This parameter can either be configured as an absolute path to the credential helper executable or as a credential helper suffix (following `docker-credential-`).
`tags` | list | *None* | Additional tags to push to.

<a name="auth-object"></a>`auth` is an object with the following properties (see [Using Specific Credentials](#using-specific-credentials)):

Property | Type
--- | ---
`username` | `String`
`password` | `String`

<a name="container-object"></a>`container` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`appRoot` | string | `/app` | The root directory on the container where the app's contents are placed. Particularly useful for WAR-packaging projects to work with different Servlet engine base images by designating where to put exploded WAR contents; see [WAR usage](#war-projects) as an example.
`args` | list | *None* | Additional program arguments appended to the command to start the container (similar to Docker's [CMD](https://docs.docker.com/engine/reference/builder/#cmd) instruction in relation with [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint)). In the default case where you do not set a custom `entrypoint`, this parameter is effectively the arguments to the main method of your Java application.
`entrypoint` | list | *None* | The command to start the container with (similar to Docker's [ENTRYPOINT](https://docs.docker.com/engine/reference/builder/#entrypoint) instruction). If set, then `jvmFlags` and `mainClass` are ignored. You may also set `<entrypoint>INHERIT</entrypoint>` to indicate that the `entrypoint` and `args` should be inherited from the base image.\*
`environment` | map | *None* | Key-value pairs for setting environment variables on the container (similar to Docker's [ENV](https://docs.docker.com/engine/reference/builder/#env) instruction).
`extraClasspath` | `list` | *None* | Additional paths in the container to prepend to the computed Java classpath.
`format` | string | `Docker` | Use `OCI` to build an [OCI container image](https://www.opencontainers.org/).
`jvmFlags` | list | *None* | Additional flags to pass into the JVM when running your application.
`labels` | map | *None* | Key-value pairs for applying image metadata (similar to Docker's [LABEL](https://docs.docker.com/engine/reference/builder/#label) instruction).
`mainClass` | string | *Inferred*\*\* | The main class to launch the application from.
`ports` | list | *None* | Ports that the container exposes at runtime (similar to Docker's [EXPOSE](https://docs.docker.com/engine/reference/builder/#expose) instruction).
`useCurrentTimestamp` | boolean | `false` | By default, Jib wipes all timestamps to guarantee reproducibility. If this parameter is set to `true`, Jib will set the image's creation timestamp to the time of the build, which sacrifices reproducibility for easily being able to tell when your image was created.
`user` | string | *None* | The user and group to run the container as. The value can be a username or UID along with an optional groupname or GID. The following are all valid: `user`, `uid`, `user:group`, `uid:gid`, `uid:group`, `user:gid`.
`volumes` | list | *None* | Specifies a list of mount points on the container.
`workingDirectory` | string | *None* | The working directory in the container.

<a name="extradirectory-object"></a>`extraDirectory` is an object with the following properties (see [Adding Arbitrary Files to the Image](#adding-arbitrary-files-to-the-image)):

Property | Type
--- | ---
`path` | string
`permissions` | list

<a name="dockerclient-object"></a>**(`jib:dockerBuild` only)** `dockerClient` is an object with the following properties:

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
`jib.baseImageCache` | string | `[user cache home]/google-cloud-tools-java/jib` | Sets the directory to use for caching base image layers. This cache can (and should) be shared between multiple images.
`jib.applicationCache` | string | `[project dir]/target/jib-cache` | Sets the directory to use for caching application layers. This cache can be shared between multiple images.
`jib.console` | `String` | *None* | If set to `plain`, Jib will print plaintext log messages rather than display a progress bar during the build.

*\* If you configure `args` while `entrypoint` is set to `'INHERIT'`, the configured `args` value will take precedence over the CMD propagated from the base image.*

*\*\* Uses the main class defined in the `jar` task or tries to find a valid main class.*

### Example

In this configuration, the image:
* Is built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Is pushed to `localhost:5000/my-image:built-with-jib`, `localhost:5000/my-image:tag2`, and `localhost:5000/my-image:latest`
* Runs by calling `java -Xms512m -Xdebug -Xmy:flag=jib-rules -cp app/libs/*:app/resources:app/classes mypackage.MyApp some args`
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
      <jvmFlag>-Xms512m</jvmFlag>
      <jvmFlag>-Xdebug</jvmFlag>
      <jvmFlag>-Xmy:flag=jib-rules</jvmFlag>
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

### Adding Arbitrary Files to the Image

*\* Note: this is an incubating feature and may change in the future.*

You can add arbitrary, non-classpath files to the image by placing them in a `src/main/jib` directory. This will copy all files within the `jib` folder to the image's root directory, maintaining the same structure (e.g. if you have a text file at `src/main/jib/dir/hello.txt`, then your image will contain `/dir/hello.txt` after being built with Jib).

You can configure a different directory by using the `<extraDirectory>` parameter in your `pom.xml`:
```xml
<configuration>
  <!-- Copies files from 'src/main/custom-extra-dir' instead of 'src/main/jib' -->
  <extraDirectory>${project.basedir}/src/main/custom-extra-dir</extraDirectory>
</configuration>
```

Alternatively, the `<extraDirectory>` parameter can be used as an object to set a custom extra directory, as well as the extra files' permissions on the container:

```xml
<configuration>
  <extraDirectory>
    <path>${project.basedir}/src/main/custom-extra-dir</path> <!-- Copies files from 'src/main/custom-extra-dir' -->
    <permissions>
      <permission>
        <file>/path/on/container/to/fileA</file>
        <mode>755</mode> <!-- Read/write/execute for owner, read/execute for group/other -->
      </permission>
      <permission>
        <file>/path/to/another/file</file>
        <mode>644</mode> <!-- Read/write for owner, read-only for group/other -->
      </permission>
    </permissions>
  </extraDirectory>
</configuration>
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

**Note:** This method of authentication should be used only as a last resort, as it is insecure to make your password visible in plain text.

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

### WAR Projects

Jib also containerizes WAR projects. If the Maven project uses [the `war`-packaging type](https://maven.apache.org/plugins/maven-war-plugin/index.html), Jib will by default use the [distroless Jetty](https://github.com/GoogleContainerTools/distroless/tree/master/java/jetty) as a base image to deploy the project WAR. No extra configuration is necessary other than having the packaging type to `war`.

Note that Jib will work slightly differently for WAR projects from JAR projects:
   - `<container><mainClass>` and `<container><jvmFlags>` are ignored.
   - The WAR will be exploded into `/jetty/webapps/ROOT`, which is the expected WAR location for the distroless Jetty base image.

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

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Upcoming Features

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Community

See the [Jib project README](/../../#community).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/jib-maven-plugin)](https://github.com/igrigorik/ga-beacon)
