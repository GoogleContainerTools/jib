[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Gradle Plugin Portal](https://img.shields.io/badge/gradle%20plugin-v0.1.0-blue.svg)](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib)

# Jib - plugin for [Gradle](https://gradle.org/)

For information about the project, see the [Jih project README](/../../).
For the Maven plugin, see the [jib-maven-plugin project](/../../tree/master/jib-maven-plugin).

## Upcoming Features

These features are not currently supported but will be added in later releases.

* Support for WAR format
* Define credentials in configuration

## Quickstart

### Setup

*Make sure you are using Gradle version 4.6 or newer.*

In your Gradle Java project, add the plugin to your `build.gradle`:

```groovy
plugins {
  id 'com.google.cloud.tools.jib' version '0.1.0'
}
```

See the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib) for more details.

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

#### *TODO: Add more examples for common registries.*

### Build Your Image

Build your container image with:

```shell
gradle build jib
```

Subsequent builds are much faster than the initial build. 

If you want to clear Jib's build cache and force it to re-pull the base image and re-build the application layers, run:

```shell
gradle clean build jib
```

*Having trouble? Let us know by [submitting an issue](/../../issues/new), contacting us on [Gitter](https://gitter.im/google/jib), or posting to the [Jib users forum](https://groups.google.com/forum/#!forum/jib-users).*

### Run `jib` with each build

You can also have `jib` run with each build by attaching it to the `build` task:

```groovy
tasks.build.finalizedBy tasks.jib
```

Then, ```gradle build``` will build and containerize your application.

### Export to a Docker context

*Not yet supported*

## Extended Usage

The plugin provides the `jib` extension for configuration with the following options for customizing the image build:

```groovy
jib {
  // Configures the base image to build your application on top of.
  // OPTIONAL
  from {
    // The image reference for the base image.
    // OPTIONAL STRING, defaults to 'gcr.io/distroless/java'
    image =
    // Suffix for the credential helper that can authenticate pulling the base image
    // (following `docker-credential-`).
    // OPTIONAL STRING
    credHelper =
  }
  
  // Configures the target image to build your application to.
  // REQUIRED
  to {
    // The image reference for the target image.
    // REQUIRED STRING
    image =
    // Suffix for the credential helper that can authenticate pushing the target image
    // (following `docker-credential-`).
    // OPTIONAL STRING
    credHelper = 
  }
  // Additional flags to pass into the JVM when running your application.
  // OPTIONAL LIST of STRING, defaults to none
  jvmFlags = []
  // The main class to launch your application from.
  // OPTIONAL, defaults to use the main class defined in the 'jar' task
  mainClass =
  // Building with the same application contents always generates the same image. 
  // Note that this does *not* preserve file timestamps and ownership.
  // OPTIONAL, defaults to 'true'
  reproducible =
  // Use 'OCI' to build an OCI container image (https://www.opencontainers.org/).
  // OPTIONAL, defaults to 'Docker'
  format = 
  // If set to true, Jib does not share a cache between different Maven projects.
  // OPTIONAL, defaults to 'false'
  useProjectOnlyCache =
}
```

### Example

In this configuration, the image is:
* Built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Pushed to `localhost:5000/my-image:built-with-jib`
* Runs by calling `java -Xms512m -Xdebug -Xmy:flag=jib-rules -cp app/libs/*:app/resources:app/classes mypackage.MyApp`
* Reproducible
* Built as OCI format

```groovy
jib {
  from {
    image = 'openjdk:alpine'
  }
  to {
    image = 'localhost:5000/my-image/built-with-jib'
    credHelper = 'osxkeychain'
  }
  jvmFlags = ['-Xms512m', '-Xdebug', '-Xmy:flag=jib-rules']
  mainClass = 'mypackage.MyApp'
  reproducible = true
  format = 'OCI'
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
<!--* Azure Container Registry: [`docker-credential-acr-*`](https://github.com/Azure/acr-docker-credential-helper)
-->

Configure credential helpers to use by specifying them as a `credHelper` for their respective image in the `jib` extension.

*Example configuration:* 
```xml
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

*Not yet supported*

## How Jib Works

See the [Jih project README](/../../#how-jib-works).

## Known Limitations

These limitations will be fixed in later releases.

* Does not build directly to a Docker daemon.
* Pushing to Azure Container Registry is not currently supported.

## Frequently Asked Questions (FAQ)

See the [Jih project README](/../../#frequently-asked-questions-faq).

## Community

See the [Jih project README](/../../#community).