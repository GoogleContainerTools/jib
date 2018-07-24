[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/jib-maven-plugin)](https://github.com/igrigorik/ga-beacon)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib - Containerize your Maven project

Jib is a [Maven](https://maven.apache.org/) plugin for building Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications.

For information about the project, see the [Jib project README](../README.md).
For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

## Upcoming Features

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Quickstart

You can containerize your application easily with one command:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:0.9.7:build -Dimage=<MY IMAGE>
```

This builds and pushes a container image for your application to a container registry. *If you encounter authentication issues, see [Authentication Methods](#authentication-methods).*

To build to a Docker daemon, use:

```shell
mvn compile com.google.cloud.tools:jib-maven-plugin:0.9.7:dockerBuild
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
        <version>0.9.7</version>
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
    <image>registry.hub.docker.com/my-docker-id/my-app</image>
  </to>
</configuration>
```

#### *TODO: Add more examples for common registries.*

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

### Export to a Docker context

Jib can also export a Docker context so that you can build with Docker, if needed:

```shell
mvn compile jib:exportDockerContext
```

The Docker context will be created at `target/jib-docker-context` by default. You can change this directory with the `targetDir` configuration option or the `jibTargetDir` parameter:

```shell
mvn compile jib:exportDockerContext -DjibTargetDir=my/docker/context/
```

You can then build your image with Docker:

```shell
docker build -t myimage my/docker/context/
```

## Extended Usage

Extended configuration options provide additional options for customizing the image build.

Field | Type | Default | Description
--- | --- | --- | ---
`from` | [`from`](#from-object) | See [`from`](#from-object) | Configures the base image to build your application on top of.
`to` | [`to`](#to-object) | *Required* | Configures the target image to build your application to.
`container` | [`container`](#container-object) | See [`container`](#container-object) | Configures the container that is run from your image.
`useOnlyProjectCache` | boolean | `false` | If set to true, Jib does not share a cache between different Gradle projects.
`allowInsecureRegistries` | boolean | `false` | If set to true, Jib uses HTTP as a fallback for registries that do not support HTTPS or whose certificates cannot be verified. Leaving this parameter set to `false` is strongly recommended, since communication with insecure registries is unencrypted and visible to others on the network.

<a name="from-object"></a>`from` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | `gcr.io/distroless/java` | The image reference for the base image.
`credHelper` | string | *None* | Suffix for the credential helper that can authenticate pulling the base image (following `docker-credential-`).

<a name="to-object"></a>`to` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`image` | string | *Required* | The image reference for the target image. This can also be specified via the `-Dimage` command line option.
`credHelper` | string | *None* | Suffix for the credential helper that can authenticate pulling the base image (following `docker-credential-`).

<a name="container-object"></a>`container` is an object with the following properties:

Property | Type | Default | Description
--- | --- | --- | ---
`jvmFlags` | list | *None* | Additional flags to pass into the JVM when running your application.
`mainClass` | string | *Inferred\** | The main class to launch the application from.
`args` | list | *None* | Default main method arguments to run your application with.
`ports` | list | *None* | Ports that the container exposes at runtime (similar to Docker's [EXPOSE](https://docs.docker.com/engine/reference/builder/#expose) instruction).
`format` | string | `Docker` | Use `OCI` to build an [OCI container image](https://www.opencontainers.org/).
`useCurrentTimestamp` | boolean | `false` | By default, Jib wipes all timestamps to guarantee reproducibility. If this parameter is set to `true`, Jib will set the image's creation timestamp to the time of the build, which sacrifices reproducibility for easily being able to tell when your image was created.

You can also configure HTTP connection/read timeouts for registry interactions using the `jib.httpTimeout` system property, configured in milliseconds via commandline (the default is `20000`; you can also set it to `0` for infinite timeout):

```shell
mvn compile jib:build -Djib.httpTimeout=3000
```

*\* Uses `mainClass` from `maven-jar-plugin` or tries to find a valid main class.*

### Example

In this configuration, the image:
* Is built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Is pushed to `localhost:5000/my-image:built-with-jib`
* Runs by calling `java -Xms512m -Xdebug -Xmy:flag=jib-rules -cp app/libs/*:app/resources:app/classes mypackage.MyApp some args`
* Exposes port 1000 for tcp (default), and ports 2000, 2001, 2002, and 2003 for udp
* Is built as OCI format

```xml
<configuration>
  <from>
    <image>openjdk:alpine</image>
  </from>
  <to>
    <image>localhost:5000/my-image:built-with-jib</image>
    <credHelper>osxkeychain</credHelper>
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
    <format>OCI</format>
  </container>
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
<!--* Azure Container Registry: [`docker-credential-acr-*`](https://github.com/Azure/acr-docker-credential-helper)
-->

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

## How Jib Works

See the [Jib project README](/../../#how-jib-works).

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Community

See the [Jib project README](/../../#community).
