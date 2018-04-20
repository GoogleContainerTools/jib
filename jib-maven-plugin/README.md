[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib - Containerize your Maven project

Jib is a [Maven](https://maven.apache.org/) plugin for building Docker and OCI images for your Java applications.

For information about the project, see the [Jib project README](..).
For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

## Upcoming Features

These features are not currently supported but will be added in later releases.

* Support for WAR format

## Quickstart

### Setup

In your Maven Java project, add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>0.1.6</version>
  <configuration>
    <registry>myregistry</registry>
    <repository>myapp</repository>
  </configuration>
</plugin>
```

### Configuration

Configure the plugin by changing `registry` and `repository` to be the registry and repository to push the built image to.

#### Using [Google Container Registry (GCR)](https://cloud.google.com/container-registry/)...

*Make sure you have the [`docker-credential-gcr` command line tool](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper). Jib automatically uses `docker-credential-gcr` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `gcr.io/my-gcp-project/my-app`, the configuration would be:

```xml
<configuration>
  <registry>gcr.io</registry>
  <repository>my-gcp-project/my-app</repository>
</configuration>
```

#### Using [Amazon Elastic Container Registry (ECR)](https://aws.amazon.com/ecr/)...

*Make sure you have the [`docker-credential-ecr-login` command line tool](https://github.com/awslabs/amazon-ecr-credential-helper). Jib automatically uses `docker-credential-ecr-login` for obtaining credentials. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `aws_account_id.dkr.ecr.region.amazonaws.com/my-app`, the configuration would be:

```xml
<configuration>
  <registry>aws_account_id.dkr.ecr.region.amazonaws.com</registry>
  <repository>my-app</repository>
</configuration>
```

#### Using [Docker Hub Registry](https://hub.docker.com/)...

*Make sure you have a [docker-credential-helper](https://github.com/docker/docker-credential-helpers#available-programs) set up. For example, on macOS, the credential helper would be `docker-credential-osxkeychain`. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `my-docker-id/my-app`, the configuration would be:

```xml
<configuration>
  <registry>registry.hub.docker.com</registry>
  <repository>my-docker-id/my-app</repository>
  <credHelpers><credHelper>osxkeychain</credHelper></credHelpers>
</configuration>
```

#### *TODO: Add more examples for common registries.* 

### Build Your Image

Build your container image with:

```shell
mvn compile jib:build
```

Subsequent builds are much faster than the initial build. 

If you want to clear Jib's build cache and force it to re-pull the base image and re-build the application layers, run:

```shell
mvn clean compile jib:build
```

*Having trouble? Let us know by [submitting an issue](/../../issues/new), contacting us on [Gitter](https://gitter.im/google/jib), or posting to the [Jib users forum](https://groups.google.com/forum/#!forum/jib-users).*

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

Jib can also export to a Docker context so that you can build with Docker, if needed:

```shell
mvn compile jib:dockercontext
```

The Docker context will be created at `target/jib-dockercontext` by default. You can change this directory with the `targetDir` configuration option or the `jib.dockerDir` parameter:

```shell
mvn compile jib:dockercontext -Djib.dockerDir=my/docker/context/
```

You can then build your image with Docker:

```shell
docker build -t myregistry/myapp my/docker/context/
``` 

## Extended Usage

Extended configuration options provide additional options for customizing the image build.

Field | Default | Description
--- | --- | ---
`from`|[`gcr.io/distroless/java`](https://github.com/GoogleCloudPlatform/distroless)|The base image to build your application on top of.
`registry`|*Required*|The registry server to push the built image to.
`repository`|*Required*|The image name/repository of the built image.
`tag`|`latest`|The image tag of the built image (the part after the colon).
`credHelpers`|*Required*|Suffixes for credential helpers (following `docker-credential-`)
`jvmFlags`|*None*|Additional flags to pass into the JVM when running your application.
`mainClass`|Uses `mainClass` from `maven-jar-plugin`|The main class to launch the application from.
`enableReproducibleBuilds`|`true`|Building with the same application contents always generates the same image. Note that this does *not* preserve file timestamps and ownership. 
`imageFormat`|`Docker`|Use `OCI` to build an [OCI container image](https://www.opencontainers.org/).
`useOnlyProjectCache`|`false`|If set to true, Jib does not share a cache between different Maven projects.

### Example

In this configuration, the image is:
* Built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Pushed to `localhost:5000/my-image:built-with-jib`
* Runs by calling `java -Xms512m -Xdebug -Xmy:flag=jib-rules -cp app/libs/*:app/resources:app/classes mypackage.MyApp`
* Reproducible
* Built as OCI format

```xml
<configuration>
  <from>openjdk:alpine</from>
  <registry>localhost:5000</registry>
  <repository>my-image</repository>
  <tag>built-with-jib</tag>
  <credHelpers>
    <credHelper>osxkeychain</credHelper>
  </credHelpers>
  <jvmFlags>
    <jvmFlag>-Xms512m</jvmFlag>
    <jvmFlag>-Xdebug</jvmFlag>
    <jvmFlag>-Xmy:flag=jib-rules</jvmFlag>
  </jvmFlags>
  <mainClass>mypackage.MyApp</mainClass>
  <enableReproducibleBuilds>true</enableReproducibleBuilds>
  <imageFormat>OCI</imageFormat>
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

Configure credential helpers to use by specifying them in the `credHelpers` configuration.

*Example configuration:* 
```xml
<configuration>
  ...
  <credHelpers>
    <credHelper>osxkeychain</credHelper>
  </credHelpers>
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

## Known Limitations

These limitations will be fixed in later releases.

* Does not build directly to a Docker daemon.
* Pushing to Azure Container Registry is not currently supported.

## Frequently Asked Questions (FAQ)

See the [Jib project README](/../../#frequently-asked-questions-faq).

## Community

See the [Jib project README](/../../#community).
