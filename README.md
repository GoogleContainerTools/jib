## Important: This project is in an early alpha stage and NOT ready for use yet.

*TODO: Add a logo and badges.*

# Jib

## What is Jib?

Jib is a tool for building container images for your Java applications.

## Goals

* Fast - Your Java application gets broken down into multiple layers, separating dependencies from classes. Deploy your changes faster - don’t wait for Docker to rebuild your entire Java application.

<!--* Reproducible - Rebuilding your container image with the same contents always generates the same image. Never trigger an unnecessary update again.-->

* Native - Reduce your CLI dependencies. Build your Docker image from within Maven or Gradle and push to any registry of your choice. No more writing Dockerfiles and calling docker build/push.

## Quickstart

### Setup

In your Maven Java project, add the plugin to your `pom.xml`:

```
<plugin>
    <groupId>com.google.com.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>0.1.0</version>
    <configuration>
        <registry></registry>
        <repository></repository>
        <credentialHelperName></credentialHelperName>
    </configuration>
</plugin>
```

### Configuration

Configure the plugin by changing `registry`, `repository`, and `credentialHelperName` accordingly.

#### I am using Google Container Registry (GCR)

*TODO: Add reference for how to get the credential helper.*

For example, to build the image `gcr.io/my-gcp-project/my-app`, the configuration would be:

```
<configuration>
    <registry>gcr.io</registry>
    <repository>my-gcp-project/my-app</repository>
    <credentialHelperName>gcr</credentialHelperName>
</configuration>
```

#### I am using Amazon Elastic Container Registry (ECR)

*TODO: Add reference for how to get the credential helper.*

For example, to build the image `aws_account_id.dkr.ecr.region.amazonaws.com/my-app`, the configuration would be:

```
<configuration>
    <registry>aws_account_id.dkr.ecr.region.amazonaws.com</registry>
    <repository>my-app</repository>
    <credentialHelperName>ecr-login</credentialHelperName>
</configuration>
```

#### *TODO: Add more examples for common registries.* 

### Build Your Image

Build your container image with:

```
$ mvn compile jib:build
```

Subsequent builds would usually be much faster than the initial build.

## Extended Usage

Extended configuration options provide additional options for customizing the image build.

Field | Default | Description
--- | --- | ---
from|`gcr.io/distroless/java`|The base image to build your application on top of.
baseImageRegistry|`gcr.io`|The registry for the base image
baseImageRepository|`distroless/java`|The image name/repository of the base image
baseImageTag|`latest`|The tag for the base image
registry|*Required*|The registry server to push the built image to.
repository|*Required*|The image name/repository of the built image.
tag|`latest`|The image tag of the built image (the part after the colon).
jvmFlags|*None*|Additional flags to pass into the JVM when running your application.
credentialHelperName|*Required*|The credential helper suffix (following `docker-credential-`)

## How Jib Works

Whereas traditionally a Java application is built as a single image layer with the application JAR, the build strategy here breaks the Java application into multiple layers for more granular incremental builds. When you change your code, only your changes are rebuilt, not your entire application. These layers, by default, are layered on top of a [distroless](https://github.com/GoogleCloudPlatform/distroless) base image. 

See also [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel).

## Frequently Asked Questions (FAQ)

*TODO: Add more answers.*

### But, I'm not a Java developer.

[bazelbuild/rules_docker](https://github.com/bazelbuild/rules_docker)
