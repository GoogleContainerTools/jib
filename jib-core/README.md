![experimental](https://img.shields.io/badge/stability-beta-orange.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib Core - Java library for building containers

Jib Core is a Java library for building Docker and [OCI](https://github.com/opencontainers/image-spec) container images. It implements a general-purpose container builder that can be used to build containers without a Docker daemon, for any application. The implementation is pure Java.

*The API is currently in beta and may change substantially.*

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
  <version>0.28.0</version>
</dependency>
```

Add Jib Core as a dependency using Gradle:

```groovy
dependencies {
  compile 'com.google.cloud.tools:jib-core:0.28.0'
}
```

## Examples

```java
Jib.from("busybox")
   .addLayer(Arrays.asList(Paths.get("helloworld.sh")), AbsoluteUnixPath.get("/")) 
   .setEntrypoint("sh", "/helloworld.sh")
   .containerize(
       Containerizer.to(RegistryImage.named("gcr.io/my-project/hello-from-jib")
                                     .addCredential("myusername", "mypassword")));
```

1. [`Jib.from("busybox")`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Jib.html#from-java.lang.String-) creates a new [`JibContainerBuilder`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html) configured with [`busybox`](https://hub.docker.com/_/busybox/) as the base image.
1. [`.addLayer(...)`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibContainerBuilder.html#addLayer-java.util.List-com.google.cloud.tools.jib.api.AbsoluteUnixPath-) configures the `JibContainerBuilder` with a new layer with `helloworld.sh` (local file) to be placed into the container at `/helloworld.sh`.
1. [`.setEntrypoint("sh", "/helloworld.sh")`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibContainerBuilder.html#setEntrypoint-java.lang.String...-) sets the entrypoint of the container to run `/helloworld.sh`.
1. [`RegistryImage.named("gcr.io/my-project/hello-from-jib")`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/RegistryImage.html#named-java.lang.String-) creates a new [`RegistryImage`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/RegistryImage.html) configured with `gcr.io/my-project/hello-from-jib` as the target image to push to.
1. [`.addCredential`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/RegistryImage.html#addCredential-java.lang.String-java.lang.String-) adds the username/password credentials to authenticate the push to `gcr.io/my-project/hello-from-jib`. See [`CredentialRetrieverFactory`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/frontend/CredentialRetrieverFactory.html) for common credential retrievers (to retrieve credentials from Docker config or credential helpers, for example). These credential retrievers can be used with [`.addCredentialRetriever`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/RegistryImage.html#addCredentialRetriever-com.google.cloud.tools.jib.api.CredentialRetriever-).
1. [`Containerizer.to`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html#to-com.google.cloud.tools.jib.api.RegistryImage-) creates a new [`Containerizer`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html) configured to push to the `RegistryImage`.
1. [`.containerize`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibContainerBuilder.html#containerize-com.google.cloud.tools.jib.api.Containerizer-) executes the containerization. If successful, the container image will be available at `gcr.io/my-project/hello-from-jib`.

See [examples](examples/README.md) for links to more jib-core samples. We welcome contributions for additional examples and tutorials!

## API overview

[`Jib`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Jib.html) - the main entrypoint for using Jib Core

[`JibContainerBuilder`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibContainerBuilder.html) - configures the container to build

[`Containerizer`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html) - configures how and where to containerize to

[`JibContainer`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibContainer.html) - information about the built container

Three types define what Jib can accept as either the base image or as the build target:
- [`RegistryImage`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/RegistryImage.html) - an image on a container registry
- [`DockerDaemonImage`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/DockerDaemonImage.html) - an image in the Docker daemon
- [`TarImage`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/TarImage.html) - an image saved as a tarball archive on the filesystem

Other useful classes:
- [`ImageReference`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/ImageReference.html) - represents an image reference and has useful methods for parsing and manipulating image references
- [`LayerConfiguration`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/LayerConfiguration.html) - configures a container layer to build
- [`CredentialRetriever`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/CredentialRetriever.html) - implement with custom credential retrieval methods for authenticating against a container registry
- [`CredentialRetrieverFactory`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/frontend/CredentialRetrieverFactory.html) - provides useful `CredentialRetriever`s to retrieve credentials from Docker config and credential helpers

Java-specific API:
- [`JavaContainerBuilder`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JavaContainerBuilder.html) - configures a `JibContainerBuilder` for Java-specific applications
- [`MainClassFinder`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/MainClassFinder.html) - find the main Java class in a given list of class files

## API reference

[API reference](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/package-summary.html)

## How Jib Core works

The Jib Core system consists 3 main parts:

- an execution orchestrator that executes an asynchronous pipeline of containerization steps,
- an image manipulator capable of handling Docker and OCI image formats, and
- a registry client that implements the [Docker Registry V2 API](https://docs.docker.com/registry/spec/api/).

Some other parts of Jib Core internals include:

- a caching mechanism to speed up builds (configurable with [`Containerizer.setApplicationLayersCache`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html#setApplicationLayersCache-java.nio.file.Path-) and [`Containerizer.setBaseImageLayersCache`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html#setBaseImageLayersCache-java.nio.file.Path-))
- an [eventing system](#events) to react to events from Jib Core during its execution (add handlers with [`Containerizer.addEventHandler`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/Containerizer.html#addEventHandler-java.lang.Class-java.util.function.Consumer-))
- support for fully-concurrent multi-threaded executions

## Events

Throughout the build process, Jib Core dispatches events that provide useful information. These events implement the type [`JibEvent`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/JibEvent.html), and can be handled by registering event handlers with the containerizer.

```java
Jib.from(...)
    ...
    .containerize(
        Containerizer.to(...)
            ...
            .addEventHandler(LogEvent.class, logEvent -> System.out.println(logEvent.getLevel() + ": " + logEvent.getMessage())
            .addEventHandler(TimerEvent.class, timeEvent -> ...));
```

When Jib dispatches events, the event handlers you defined for that event type will be called. The following are the types of events you can listen for in Jib core (see [API reference](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/package-summary.html) for more information):

- [`LogEvent`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/api/LogEvent.html) - Log message events. The message and verbosity can be retrieved using `getMessage()` and `getLevel()`, respectively.
- [`TimerEvent`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/event/events/TimerEvent.html) (*Incubating*) - Events used for measuring how long different build steps take. You can retrieve the duration since the timer's creation and the duration since the same timer's previous event using `getElapsed()` and `getDuration()`, respectively.
- [`ProgressEvent`](http://www.javadoc.io/page/com.google.cloud.tools/jib-core/latest/com/google/cloud/tools/jib/event/events/ProgressEvent.html) (*Incubating*) - Indicates the amount of progress build steps have made. Each progress event consists of an allocation (containing a fraction representing how much of the root allocation this allocation accounts for) and a number of progress units that indicates the amount of work completed since the previous progress event. In other words, the amount of work a single progress event has completed (out of 1.0) can be calculated using `getAllocation().getFractionOfRoot() * getUnits()`.

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Upcoming features

- Extensions to make building Java and other language-specific containers easier

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Community

See the [Jib project README](/../../#community).
