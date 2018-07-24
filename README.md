[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://img.shields.io/maven-metadata/v/http/central.maven.org/maven2/com/google/cloud/tools/jib-maven-plugin/maven-metadata.xml.svg?colorB=007ec6)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/badge/gradle%20plugin-v0.9.7-blue.svg)](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-ubuntu-master-orb.svg)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-windows-master-orb.svg)
![Build Status](https://storage.googleapis.com/cloud-tools-for-java-kokoro-build-badges/jib-macos-master-orb.svg)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib

<image src="https://github.com/GoogleContainerTools/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

## What is Jib?

Jib builds Docker and [OCI](https://github.com/opencontainers/image-spec) images for your Java applications and is available as plugins for [Maven](jib-maven-plugin) and [Gradle](jib-gradle-plugin).

[Maven](https://maven.apache.org/): See documentation for [jib-maven-plugin](jib-maven-plugin).\
[Gradle](https://gradle.org/): See documentation for [jib-gradle-plugin](jib-gradle-plugin).

*Jib as a container-building library for Java is work-in-progress. [Watch for updates.](https://github.com/GoogleContainerTools/jib/issues/337)*

## Goals

* **Fast** - Deploy your changes fast. Jib separates your application into multiple layers, splitting dependencies from classes. Now you don’t have to wait for Docker to rebuild your entire Java application - just deploy the layers that changed.

* **Reproducible** - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.

* **Daemonless** - Reduce your CLI dependencies. Build your Docker image from within Maven or Gradle and push to any registry of your choice. *No more writing Dockerfiles and calling docker build/push.*

## Quickstart

### Maven

See documentation for using [jib-maven-plugin](jib-maven-plugin#quickstart).

### Gradle

See documentation for using [jib-gradle-plugin](jib-gradle-plugin#quickstart).

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy separates the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. For more information, check out the [official blog post](https://cloudplatform.googleblog.com/2018/07/introducing-jib-build-java-docker-images-better.html).

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Frequently Asked Questions (FAQ)

See the [Frequently Asked Questions (FAQ) page](docs/faq.md).

## Get involved with the community

We welcome contributions! Here's how you can contribute:

* [Browse issues](https://github.com/GoogleContainerTools/jib/issues) or [file an issue](https://github.com/GoogleContainerTools/jib/issues/new)
* Chat with us on [gitter](https://gitter.im/google/jib)
* Join the [jib-users mailing list](https://groups.google.com/forum/#!forum/jib-users)
* Contribute:
  * *Read the [contributing guide](https://github.com/GoogleContainerTools/jib/blob/master/CONTRIBUTING.md) before starting work on an issue*
  * Try to fix [good first issues](https://github.com/GoogleContainerTools/jib/labels/good%20first%20issue)
  * Help out on [issues that need help](https://github.com/GoogleContainerTools/jib/labels/help%20wanted)
  * Join in on [discussion issues](https://github.com/GoogleContainerTools/jib/labels/discuss)
<!--  * Read the [style guide] -->
*Make sure to follow the [Code of Conduct](https://github.com/GoogleContainerTools/jib/blob/master/CODE_OF_CONDUCT.md) when contributing so we can foster an open and welcoming community.*

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/index)](https://github.com/igrigorik/ga-beacon)
