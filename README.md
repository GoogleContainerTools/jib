[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)
[![Gitter version](https://img.shields.io/gitter/room/gitterHQ/gitter.svg)](https://gitter.im/google/jib)

# Jib

<image src="https://github.com/google/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

## What is Jib?

Jib is a Maven plugin for building Docker and OCI images for your Java applications.

## Goals

* **Fast** - Deploy your changes fast. Jib separates your application into multiple layers, splitting dependencies from classes. Now you don’t have to wait for Docker to rebuild your entire Java application - just deploy the layers that changed.

* **Reproducible** - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.

* **Native** - Reduce your CLI dependencies. Build your Docker image from within Maven <!--or Gradle--> and push to any registry of your choice. *No more writing Dockerfiles and calling docker build/push.*

## Upcoming Features

These features are not currently supported but will be added in later releases.

* Gradle plugin
* Support for WAR format

## Quickstart

### Setup

In your Maven Java project, add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>0.1.4</version>
  <configuration>
    <registry>myregistry</registry>
    <repository>myapp</repository>
  </configuration>
</plugin>
```

### Configuration

Configure the plugin by changing `registry`, `repository`, and `credHelpers` accordingly.

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

*Make sure you have the [`docker-credential-ecr-login` command line tool](https://github.com/awslabs/amazon-ecr-credential-helper). Jib automatically uses `docker-credential-ecr-login` for obtaining credentials. To use a different credential helper, set the [`credHelpers`](#extended-usage) configuration. See [Authentication Methods](#authentication-methods) for other ways of authenticating.*

For example, to build the image `aws_account_id.dkr.ecr.region.amazonaws.com/my-app`, the configuration would be:

```xml
<configuration>
  <registry>aws_account_id.dkr.ecr.region.amazonaws.com</registry>
  <repository>my-app</repository>
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

*Having trouble? Let us know by [submitting an issue](/../../issues/new).*

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

*Example `settings.xml`:*
```xml
<settings>
  ...
  <servers>
    ...
    <server>
      <id>MY_REGISTRY</id>
      <username>MY_USERNAME</username>
      <password>MY_SECRET</password>
    </server>
  </servers>
</settings>
```

* The `id` field should be the registry server these credentials are for. 
* We *do not* recommend putting your raw password in `settings.xml`.

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy separates the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. 

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Known Limitations

These limitations will be fixed in later releases.

* Cannot build directly to a Docker daemon.
* Pushing to Azure Container Registry is not currently supported.

## Community

* Chat with us on [gitter](https://gitter.im/google/jib)
* [jib-users mailing list](https://groups.google.com/forum/#!forum/jib-users)

## Frequently Asked Questions (FAQ)

If a question you have is not answered below, please [submit an issue](/../../issues/new).

### But, I'm not a Java developer.

See [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel). The tool can build images for languages such as Python, NodeJS, Java, Scala, Groovy, C, Go, Rust, and D.

### What image format does Jib use?

Jib currently builds into the [Docker V2.2](https://docs.docker.com/registry/spec/manifest-v2-2/) image format or [OCI image format](https://github.com/opencontainers/image-spec). See [Extended Usage](#extended-usage) for the `imageFormat` configuration.

### Can I define a custom entrypoint?

The plugin attaches a default entrypoint that will run your application automatically.

When running the image, you can override this default entrypoint with your own custom command.

See [`docker run --entrypoint` reference](https://docs.docker.com/engine/reference/run/#entrypoint-default-command-to-execute-at-runtime) for running the image with Docker.

See [Define a Command and Arguments for a Container](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/) for running the image in a [Kubernetes](https://kubernetes.io/) Pod.

### Where is the application in the container filesystem?

Jib packages your Java application into the following paths on the image:

* `/app/libs/` contains all the dependency artifacts
* `/app/resources/` contains all the resource files
* `/app/classes/` contains all the classes files

### I need to RUN commands like `apt-get`.

Running commands like `apt-get` slows down the container build process. We **do not recommend or support** running commands as part of the build. 

However, if you need to run commands, you can build a custom base image. You can then use this custom base image in the `jib-maven-plugin` by adding the following configuration:

```xml
<configuration>
  <from>custom-base-image</from>
</configuration>
```

### Can I ADD a custom directory to the image?

We currently do not support adding a custom directory to the image. If your application needs to use custom files, place them into your application's resources directory (`src/main/resources` by default). These resource files will be available on the classpath.

### Can I build to a local Docker daemon?

We currently do not support building to a local Docker daemon. However, this feature is in the pipeline and will be added in the future.

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
