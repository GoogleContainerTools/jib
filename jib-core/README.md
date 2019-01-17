![experimental](https://img.shields.io/badge/stability-experimental-red.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib Core - Java library for building containers

Jib Core is a Java library for building Docker and [OCI](https://github.com/opencontainers/image-spec) container images. It implements a general-purpose container builder that can be used to build containers without a Docker daemon, for any application. The implementation is pure Java.

*The API is currently in alpha and may change substantially.*

Jib Core powers the popular Jib plugins for Maven and Gradle. The plugins build containers specifically for JVM languages and separate the application into multiple layers to optimize for fast rebuilds.\
For the Maven plugin, see the [jib-maven-plugin project](../jib-maven-plugin).\
For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

For information about the Jib project, see the [Jib project README](../README.md).

## Adding Jib Core to your build

Add Jib Core as a dependency using Maven:

```xml
<dependency>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-core</artifactId>
  <version>0.1.1</version>
</dependency>
```

Add Jib Core as a dependency using Gradle:

```groovy
dependencies {
  compile 'com.google.cloud.tools:jib-core:0.1.1'
}
```

## Simple example

```java
Jib.from("busybox")
   .addLayer(Arrays.asList(Paths.get("helloworld.sh")), AbsoluteUnixPath.get("/")) 
   .setEntrypoint("sh", "/helloworld.sh")
   .containerize(
       Containerizer.to(RegistryImage.named("gcr.io/my-project/hello-from-jib")
                                     .addCredential("myusername", "mypassword")));
```

1. [`Jib.from("busybox")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Jib.html#from-java.lang.String-) creates a new [`JibContainerBuilder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html) configured with [`busybox`](https://hub.docker.com/_/busybox/) as the base image.
1. [`.addLayer(...)`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JibContainerBuilder.html#addLayer-java.util.List-com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath-) configures the `JibContainerBuilder` with a new layer with `helloworld.sh` (local file) to be placed into the container at `/helloworld.sh`.
1. [`.setEntrypoint("sh", "/helloworld.sh")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JibContainerBuilder.html#setEntrypoint-java.lang.String...-) sets the entrypoint of the container to run `/helloworld.sh`.
1. [`RegistryImage.named("gcr.io/my-project/hello-from-jib")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/RegistryImage.html#named-java.lang.String-) creates a new [`RegistryImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/RegistryImage.html) configured with `gcr.io/my-project/hello-from-jib` as the target image to push to.
1. [`.addCredential`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/RegistryImage.html#addCredential-java.lang.String-java.lang.String-) adds the username/password credentials to authenticate the push to `gcr.io/my-project/hello-from-jib`. See [`CredentialRetrieverFactory`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/frontend/CredentialRetrieverFactory.html) for common credential retrievers (to retrieve credentials from Docker config or credential helpers, for example). These credential retrievers an be used with [`.addCredentialRetriever`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/RegistryImage.html#addCredentialRetriever-com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever-).
1. [`Containerizer.to`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html#to-com.google.cloud.tools.jib.api.RegistryImage-) creates a new [`Containerizer`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html) configured to push to the `RegistryImage`.
1. [`.containerize`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JibContainerBuilder.html#containerize-com.google.cloud.tools.jib.api.Containerizer-) executes the containerization. If successful, the container image will be available at `gcr.io/my-project/hello-from-jib`.

## Tutorials

*None yet available. We welcome contributions for examples and tutorials!*

## API overview

[`Jib`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Jib.html) - the main entrypoint for using Jib Core

[`JibContainerBuilder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JibContainerBuilder.html) - configures the container to build

[`Containerizer`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html) - configures how and where to containerize to

[`JibContainer`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JibContainer.html) - information about the built container

Three `TargetImage` types define the 3 different targets Jib can build to:
- [`RegistryImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/RegistryImage.html) - builds to a container registry
- [`DockerDaemonImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/DockerDaemonImage.html) - builds to a Docker daemon
- [`TarImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/TarImage.html) - saves as a tarball archive

Other useful classes:
- [`ImageReference`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/image/ImageReference.html) - represents an image reference and has useful methods for parsing and manipulating image references
- [`LayerConfiguration`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/configuration/LayerConfiguration.html) - configures a container layer to build
- [`CredentialRetriever`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/configuration/credentials/CredentialRetriever.html) - implement with custom credential retrieval methods for authenticating against a container registry
- [`CredentialRetrieverFactory`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/frontend/CredentialRetrieverFactory.html) - provides useful `CredentialRetriever`s to retrieve credentials from Docker config and credential helpers
- [`EventHandlers`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/event/EventHandlers.html) - attach event handlers to handle events dispatched during the container build execution

Java-specific API:
- [`JavaContainerBuilder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/JavaContainerBuilder.html) - configures a `JibContainerBuilder` for Java-specific applications
- [`MainClassFinder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/frontend/MainClassFinder.html) - find the main Java class in a given list of class files

## API reference

[API reference](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/package-summary.html)

## How Jib Core works

The Jib Core system consists 3 main parts:

- an execution orchestrator that executes an asynchronous pipeline of containerization steps,
- an image manipulator capable of handling Docker and OCI image formats, and
- a registry client that implements the [Docker Registry V2 API](https://docs.docker.com/registry/spec/api/).

Some other parts of Jib Core internals include:

- a caching mechanism to speed up builds (configurable with [`Containerizer.setApplicationLayersCache`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html#setApplicationLayersCache-java.nio.file.Path-) and [`Containerizer.setBaseImageLayersCache`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html#setBaseImageLayersCache-java.nio.file.Path-))
- an eventing system to react to events from Jib Core during its execution (add handlers with [`Containerizer.setEventHandlers`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.1/com/google/cloud/tools/jib/api/Containerizer.html#setEventHandlers-com.google.cloud.tools.jib.event.EventHandlers-))
- support for fully-concurrent multi-threaded executions

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Upcoming features

- Extensions to make building Java and other language-specific containers easier
- Structured events to react to parts of Jib Core's execution

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Community

See the [Jib project README](/../../#community).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/jib-core)](https://github.com/igrigorik/ga-beacon)
