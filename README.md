[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/badge/gradle%20plugin-v0.9.2-blue.svg)](https://plugins.gradle.org/plugin/com.google.cloud.tools.jib)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib

<image src="https://github.com/GoogleContainerTools/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

## What is Jib?

Jib builds Docker and OCI images for your Java applications and is available as plugins for [Maven](jib-maven-plugin) and [Gradle](jib-gradle-plugin).

[Maven](https://maven.apache.org/): See documentation for [jib-maven-plugin](jib-maven-plugin).\
[Gradle](https://gradle.org/): See documentation for [jib-gradle-plugin](jib-gradle-plugin).

*Jib as a container-building library for Java is work-in-progress. Watch for updates.*

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

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy separates the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. 

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Frequently Asked Questions (FAQ)

See the [Frequently Asked Questions (FAQ) wiki page](/../../wiki/Frequently-Asked-Questions-(FAQ)).

## Community

* Chat with us on [gitter](https://gitter.im/google/jib)
* [jib-users mailing list](https://groups.google.com/forum/#!forum/jib-users)
