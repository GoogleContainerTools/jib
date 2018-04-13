[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib

<image src="https://github.com/google/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

## What is Jib?

Jib builds Docker and OCI images for your Java applications and is available as plugins for [Maven](jib-maven-plugin) and [Gradle](jib-gradle-plugin).

[Maven](https://maven.apache.org/): See documentation for [jib-maven-plugin](jib-maven-plugin).
[Gradle](https://gradle.org/): See documentation for [jib-gradle-plugin](jib-gradle-plugin).

## Goals

* **Fast** - Deploy your changes fast. Jib separates your application into multiple layers, splitting dependencies from classes. Now you don’t have to wait for Docker to rebuild your entire Java application - just deploy the layers that changed.

* **Reproducible** - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.

* **Native** - Reduce your CLI dependencies. Build your Docker image from within Maven <!--or Gradle--> and push to any registry of your choice. *No more writing Dockerfiles and calling docker build/push.*

## Quickstart

### Maven

See documentation for using [jib-maven-plugin](jib-maven-plugin#quickstart).

### Gradle

See documentation for using [jib-gradle-plugin](jib-gradle-plugin#quickstart).

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy separates the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. 

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Frequently Asked Questions (FAQ)

If a question you have is not answered below, please [submit an issue](/../../issues/new).

### But, I'm not a Java developer.

See [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel). The tool can build images for languages such as Python, NodeJS, Java, Scala, Groovy, C, Go, Rust, and D.

### What image format does Jib use?

Jib currently builds into the [Docker V2.2](https://docs.docker.com/registry/spec/manifest-v2-2/) image format or [OCI image format](https://github.com/opencontainers/image-spec). 

#### Maven

See [Extended Usage](jib-maven-plugin#extended-usage) for the `imageFormat` configuration.

#### Gradle

See [Extended Usage](jib-gradle-plugin#extended-usage) for the `format` configuration.

### Can I define a custom entrypoint?

The plugin attaches a default entrypoint that will run your application automatically.

When running the image, you can override this default entrypoint with your own custom command.

See [`docker run --entrypoint` reference](https://docs.docker.com/engine/reference/run/#entrypoint-default-command-to-execute-at-runtime) for running the image with Docker and overriding the entrypoint command.

See [Define a Command and Arguments for a Container](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/) for running the image in a [Kubernetes](https://kubernetes.io/) Pod and overriding the entrypoint command.

### But I just want to set some JVM flags when running the image?

When running the image, you can pass in additional JVM flags via the [`JAVA_TOOL_OPTIONS` environment variable](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html).

See [`docker run -e` reference](https://docs.docker.com/engine/reference/run/#env-environment-variables) for running the image with Docker and setting environment variables.

See [Define Environment Variables for a Container](https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/) for running the image in a [Kubernetes](https://kubernetes.io/) Pod and setting environment variables.

### Where is the application in the container filesystem?

Jib packages your Java application into the following paths on the image:

* `/app/libs/` contains all the dependency artifacts
* `/app/resources/` contains all the resource files
* `/app/classes/` contains all the classes files

### I need to RUN commands like `apt-get`.

Running commands like `apt-get` slows down the container build process. We **do not recommend or support** running commands as part of the build. 

However, if you need to run commands, you can build a custom image and configure Jib to use it as the base image. 

<details>
<summary>Base image configuration examples</summary>
<p>
#### Maven

In [`jib-maven-plugin`](jib-maven-plugin), you can then use this custom base image by adding the following configuration:

```xml
<configuration>
  <from>custom-base-image</from>
</configuration>
```

#### Gradle

In [`jib-gradle-plugin`](jib-gradle-plugin), you can then use this custom base image by adding the following configuration:

```groovy
jib.from.image = 'custom-base-image'
```
</p>
</details>

### Can I ADD a custom directory to the image?

We currently do not support adding a custom directory to the image. If your application needs to use custom files, place them into your application's resources directory (`src/main/resources` by default). These resource files will be available on the classpath.

### Can I build to a local Docker daemon?

We currently do not support building to a local Docker daemon. However, [this feature is in the pipeline and will be added in the future](/../../issues/48).

You can still [`docker pull`](https://docs.docker.com/engine/reference/commandline/pull/) the image built with `jib-maven-plugin` to have it available in your local Docker daemon.

You can also [run a local Docker registry](https://docs.docker.com/registry/deploying/) and point Jib to push to the local registry.

### I am seeing `ImagePullBackoff` on my pods (in [minikube](https://github.com/kubernetes/minikube)).

When you use your private image built with Jib in a [Kubernetes cluster](kubernetes.io), the cluster needs to be configured with credentials to pull the image. This involves 1) creating a [Secret](https://kubernetes.io/docs/concepts/configuration/secret/), and 2) using the Secret as [`imagePullSecrets`](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account).

```shell
kubectl create secret docker-registry registry-json-key \
  --docker-server=<registry> \
  --docker-username=<username> \
  --docker-password=<password> \
  --docker-email=<any valid email address>

kubectl patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"registry-json-key"}]}'
```

For example, if you are using GCR, the commands would look like (see [Advanced Authentication Methods](https://cloud.google.com/container-registry/docs/advanced-authentication)):

```shell
kubectl create secret docker-registry gcr-json-key \
  --docker-server=https://gcr.io \
  --docker-username=_json_key \
  --docker-password="$(cat keyfile.json)" \
  --docker-email=any@valid.com

kubectl patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"gcr-json-key"}]}'
```

See more at [Using Google Container Registry (GCR) with Minikube](https://ryaneschinger.com/blog/using-google-container-registry-gcr-with-minikube/).

### How do I enable debugging?

*TODO: Provide solution.*

### I would like to run my application with a javaagent.

*TODO: Provide solution.*

### How can I tag my image with a timestamp?

#### Maven

To tag the image with a simple timestamp, add the following to your `pom.xml`:

```xml
<properties>
  <maven.build.timestamp.format>yyyyMMdd-HHmmssSSS</maven.build.timestamp.format>
</properties>
```

Then in the `jib-maven-plugin` configuration, set the `tag` to:

```xml
<configuration>
  <tag>${maven.build.timestamp}</tag>
</configuration>
```

You can then use the same timestamp to reference the image in other plugins.

#### Gradle

To tag the image with a timestamp, simply set the timestamp as the tag for `to.image` in your `jib` configuration. For example:

```groovy
jib.to.image = 'gcr.io/my-gcp-project/my-app:' + System.nanoTime()
```

## Community

* Chat with us on [gitter](https://gitter.im/google/jib)
* [jib-users mailing list](https://groups.google.com/forum/#!forum/jib-users)
