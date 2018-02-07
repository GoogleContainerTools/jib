[![experimental](http://badges.github.io/stability-badges/dist/experimental.svg)](http://github.com/badges/stability-badges)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.google.cloud.tools/jib-maven-plugin)

# Jib

<image src="https://github.com/google/jib/raw/master/logo/jib-build-docker-java-container-image.png" alt="Jib - Containerize your Java applications." width="650px" />

## What is Jib?

Jib is a tool for building container images for your Java applications.

## Goals

* **Fast** - Your Java application gets broken down into multiple layers, separating dependencies from classes. Deploy your changes faster - don’t wait for Docker to rebuild your entire Java application.

<!--* Reproducible - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.-->

* **Native** - Reduce your CLI dependencies. Build your Docker image from within Maven <!--or Gradle--> and push to any registry of your choice. No more writing Dockerfiles and calling docker build/push.

## Quickstart

### Setup

In your Maven Java project, add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>com.google.com.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>0.1.0</version>
  <configuration>
    <registry>myregistry</registry>
    <repository>myapp</repository>
  </configuration>
</plugin>
```

### Configuration

Configure the plugin by changing `registry`, `repository`, and `credentialHelperName` accordingly.

#### I am using Google Container Registry (GCR)

*Make sure you have the [`docker-credential-gcr` command line tool](https://cloud.google.com/container-registry/docs/advanced-authentication#docker_credential_helper). Jib automatically uses `docker-credential-gcr` for obtaining credentials. To use a different credential helper, set the [`credentialHelperName`](#extended-usage) configuration.*

For example, to build the image `gcr.io/my-gcp-project/my-app`, the configuration would be:

```xml
<configuration>
  <registry>gcr.io</registry>
  <repository>my-gcp-project/my-app</repository>
</configuration>
```

#### I am using Amazon Elastic Container Registry (ECR)

*Make sure you have the [`docker-credential-ecr-login` command line tool](https://github.com/awslabs/amazon-ecr-credential-helper). Jib automatically uses `docker-credential-ecr-login` for obtaining credentials. To use a different credential helper, set the [`credentialHelperName`](#extended-usage) configuration.*

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

```commandline
mvn compile jib:build
```

Subsequent builds would usually be much faster than the initial build.

*Having trouble? Let us know by [submitting an issue](/../../issues/new).*

### Bind to a lifecycle

You can also bind `jib:build` to a Maven lifecycle such as `package` by adding the following execution to your `jib-maven-plugin` definition:

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

Then, you can build your container image with just:

```commandline
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
`jvmFlags`|*None*|Additional flags to pass into the JVM when running your application.
`credentialHelperName`|*Required*|The credential helper suffix (following `docker-credential-`)
`mainClass`|Uses `mainClass` from `maven-jar-plugin`|The main class to launch the application from.

### Example

In this configuration, the image is:
* Built from a base of `openjdk:alpine` (pulled from Docker Hub)
* Pushed to `localhost:5000/my-image:built-with-jib`
* Runs by calling `java -Xms512m -Xdebug -Xmy:flag=jib-rules -cp app/libs/*:app/resources:app/classes mypackage.MyApp`

```
<configuration>
    <from>openjdk:alpine</from>
    <registry>localhost:5000</registry>
    <repository>my-image</repository>
    <tag>built-with-jib</tag>
    <jvmFlags>
        <jvmFlag>-Xms512m</jvmFlag>
        <jvmFlag>-Xdebug</jvmFlag>
        <jvmFlag>-Xmy:flag=jib-rules</jvmFlag>
    </jvmFlags>
    <mainClass>mypackage.MyApp</mainClass>
</configuration>
```

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, Jib's build strategy breaks the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. 

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Known Limitations

These limitations will be fixed in the future.

* Does not build OCI images.
* Pushing to Docker Hub does not seem to work.
* Cannot build directly to a Docker daemon.
* Cannot use a private image as a base image.

## Frequently Asked Questions (FAQ)

If a question you have is not answered before, please [submit an issue](/../../issues/new).

### But, I'm not a Java developer.

See [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel). The tool can build images for languages such as Python, NodeJS, Java, Scala, Groovy, C, Go, Rust, and D.

### Can I use other authentication methods besides a Docker credential helper?

Other authentication methods will be added in our next release (`v0.2.0`).

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
