[![experimental](https://img.shields.io/badge/stability-experimental-red.svg)]
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-core)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib Core - Java library for building containers

Jib Core is a Java library for building Docker and [OCI](https://github.com/opencontainers/image-spec) container images. 

For information about the Jib project, see the [Jib project README](../README.md).
For the Maven plugin, see the [jib-maven-plugin project](../jib-maven-plugin).
For the Gradle plugin, see the [jib-gradle-plugin project](../jib-gradle-plugin).

## Upcoming features

See [Milestones](https://github.com/GoogleContainerTools/jib/milestones) for planned features. [Get involved with the community](https://github.com/GoogleContainerTools/jib/tree/master#get-involved-with-the-community) for the latest updates.

## Adding Jib Core to your build

Add Jib Core as a dependency using Maven:

```xml
<dependency>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

Add Jib Core as a dependency using Gradle:

```groovy
dependencies {
  compile 'com.google.cloud.tools:jib-core:0.1.0'
}
```

## Simple example

```java
Jib.from("busybox")
   .addLayer(Arrays.asList(Paths.get("helloworld.sh")), "/") 
   .setEntrypoint("/helloworld.sh")
   .containerize(
       Containerizer.to(RegistryImage.named("gcr.io/my-project/hello-from-jib")
                                     .addCredential("myusername", "mypassword")));
```

1. [`Jib.from("busybox")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/Jib.html#from-java.lang.String-) creates a new [`JibContainerBuilder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html) configured with [`busybox`](https://hub.docker.com/_/busybox/) as the base image.
1. [`.addLayer(...)`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html#addLayer-java.util.List-com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath-) configures the `JibContainerBuilder` with a new layer with `helloworld.sh` (local file) to be placed into the container at `/helloworld.sh`.
1. [`.setEntrypoint("/helloworld.sh")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html#setEntrypoint-java.lang.String...-) sets the entrypoint of the container to `/helloworld.sh`.
1. [`RegistryImage.named("gcr.io/my-project/hello-from-jib")`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/RegistryImage.html#named-java.lang.String-) creates a new [`RegistryImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/RegistryImage.html) configured with `gcr.io/my-project/hello-from-jib` as the target image to push to.
1. [`.addCredential`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/RegistryImage.html#addCredential-java.lang.String-java.lang.String-) adds the username/password credentials to authenticate the push to `gcr.io/my-project/hello-from-jib`.
1. [`Containerizer.to`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/Containerizer.html#to-com.google.cloud.tools.jib.api.RegistryImage-) creates a new [`Containerizer`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/Containerizer.html) configured to push to the `RegistryImage`.
1. `.containerize` executes the containerization. If successful, the container image will be available at `gcr.io/my-project/hello-from-jib`.

## API overview

[`Jib`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/Jib.html) - the main entrypoint for using Jib Core

[`JibContainerBuilder`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/JibContainerBuilder.html) - configures the container to build

[`Containerizer`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/Containerizer.html) - configures how and where to containerize to

Three `TargetImage` types define the 3 different targets Jib can build to:
- [`RegistryImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/RegistryImage.html) - builds to a container registry
- [`DockerDaemonImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/DockerDaemonImage.html) - builds to a Docker daemon
- [`TarImage`](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/TarImage.html) - saves as a tarball archive

Other useful classes:

- ImageReference
- LayerConfiguration
- CredentialRetriever
- EventHandlers
- `CredentialRetrieverFactory`

## API reference

[API reference](http://static.javadoc.io/com.google.cloud.tools/jib-core/0.1.0/com/google/cloud/tools/jib/api/package-summary.html)

## How Jib Core works

TODO

## How Jib Works

See the [Jib project README](/../../#how-jib-works).

## Frequently Asked Questions (FAQ)

See the [Jib project FAQ](../docs/faq.md).

## Community

See the [Jib project README](/../../#community).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/jib-core)](https://github.com/igrigorik/ga-beacon)
