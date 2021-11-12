![stable](https://img.shields.io/badge/stability-stable-brightgreen.svg)
[![Maven Central](https://img.shields.io/maven-central/v/com.google.cloud.tools/jib-maven-plugin)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/google/cloud/tools/jib/com.google.cloud.tools.jib.gradle.plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle)](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-ubuntu-master-orb.svg)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-windows-master-orb.svg)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-macos-master-orb.svg)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib

<image src="https://github.com/GoogleContainerTools/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

| ☑️  Jib User Survey |
| :----- |
| What do you like best about Jib? What needs to be improved? Please tell us by taking a [one-minute survey](https://forms.gle/YRFeamGj51xmgnx28). Your responses will help us understand Jib usage and allow us to serve our customers (you!) better. |

## What is Jib?

Jib builds optimized Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications without a Docker daemon - and without deep mastery of Docker best-practices. It is available as plugins for [Maven](jib-maven-plugin) and [Gradle](jib-gradle-plugin) and as a Java library.

[Maven](https://maven.apache.org/): See documentation for [jib-maven-plugin](jib-maven-plugin).\
[Gradle](https://gradle.org/): See documentation for [jib-gradle-plugin](jib-gradle-plugin).\
[Jib Core](jib-core): A general-purpose container-building library for Java.\
[Jib CLI](jib-cli): A command-line interface for building images that uses Jib Core.

For more information, check out the [official blog post](https://cloudplatform.googleblog.com/2018/07/introducing-jib-build-java-docker-images-better.html) or watch [this talk](https://www.youtube.com/watch?v=H6gR_Cv4yWI) ([slides](https://speakerdeck.com/coollog/build-containers-faster-with-jib-a-google-image-build-tool-for-java-applications)).

## Goals

* **Fast** - Deploy your changes fast. Jib separates your application into multiple layers, splitting dependencies from classes. Now you don’t have to wait for Docker to rebuild your entire Java application - just deploy the layers that changed.

* **Reproducible** - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.

* **Daemonless** - Reduce your CLI dependencies. Build your Docker image from within Maven or Gradle and push to any registry of your choice. *No more writing Dockerfiles and calling docker build/push.*

## Quickstart

* **Maven** - See the jib-maven-plugin [Quickstart](jib-maven-plugin#quickstart).

* **Gradle** - See the jib-gradle-plugin [Quickstart](jib-gradle-plugin#quickstart).

* **Jib Core** - See the Jib Core [Quickstart](jib-core#adding-jib-core-to-your-build).

* **Jib CLI** - See the Jib CLI [doc](jib-cli).

## Examples

The [examples](examples) directory includes the following examples (and more).
   * [helloworld](examples/helloworld)
   * [Spring Boot](examples/spring-boot)
   * [Micronaut](examples/micronaut)
   * [Multi-module project](examples/multi-module)
   * [Spark Java using Java Agent](examples/java-agent)

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy separates the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of an [OpenJDK base image](docs/default_base_image.md), but you can also configure a custom base image. For more information, check out the [official blog post](https://cloudplatform.googleblog.com/2018/07/introducing-jib-build-java-docker-images-better.html) or watch [this talk](https://www.youtube.com/watch?v=H6gR_Cv4yWI) ([slides](https://speakerdeck.com/coollog/build-containers-faster-with-jib-a-google-image-build-tool-for-java-applications)).

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Need Help?

A lot of questions are already answered!

* [Frequently Asked Questions (FAQ)](docs/faq.md)
* [Stack Overflow](https://stackoverflow.com/questions/tagged/jib)
* [GitHub issues](https://stackoverflow.com/questions/tagged/jib)

_For usage questions, please ask them on Stack Overflow._

## Privacy

See the [Privacy page](docs/privacy.md).

## Get involved with the community

We welcome contributions! Here's how you can contribute:

* [Browse issues](https://github.com/GoogleContainerTools/jib/issues) or [file an issue](https://github.com/GoogleContainerTools/jib/issues/new)
* Chat with us on [gitter](https://gitter.im/google/jib)
* Join the [jib-users mailing list](https://groups.google.com/forum/#!forum/jib-users)
* Contribute:
  * *Read the [contributing guide](https://github.com/GoogleContainerTools/jib/blob/master/CONTRIBUTING.md) before starting work on an issue*
  * Try to fix [good first issues](https://github.com/GoogleContainerTools/jib/labels/good%20first%20issue)
  * Help out on [issues that need help](https://github.com/GoogleContainerTools/jib/labels/kind%2Fquestion)
  * Join in on [discussion issues](https://github.com/GoogleContainerTools/jib/labels/discuss)
<!--  * Read the [style guide] -->
*Make sure to follow the [Code of Conduct](https://github.com/GoogleContainerTools/jib/blob/master/CODE_OF_CONDUCT.md) when contributing so we can foster an open and welcoming community.*

## Disclaimer

This is not an officially supported Google product.
