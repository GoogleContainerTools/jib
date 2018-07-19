[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/wiki/faq)](https://github.com/igrigorik/ga-beacon)

## Frequently Asked Questions (FAQ)

If a question you have is not answered below, please [submit an issue](/../../issues/new).

[But, I'm not a Java developer.](#but-im-not-a-java-developer)\
[What image format does Jib use?](#what-image-format-does-jib-use)\
[Can I define a custom entrypoint?](#can-i-define-a-custom-entrypoint)\
[But I just want to set some JVM flags when running the image?](#but-i-just-want-to-set-some-jvm-flags-when-running-the-image)\
[Where is the application in the container filesystem?](#where-is-the-application-in-the-container-filesystem)\
[I need to RUN commands like `apt-get`.](#i-need-to-run-commands-like-apt-get)\
[Can I ADD a custom directory to the image?](#can-i-add-a-custom-directory-to-the-image)\
[Can I build to a local Docker daemon?](#can-i-build-to-a-local-docker-daemon)\
[I am seeing `ImagePullBackoff` on my pods.](#i-am-seeing-imagepullbackoff-on-my-pods-in-minikube)\
[How do I enable debugging?](#how-do-i-enable-debugging)\
[Why is my image created 48 years ago?](#why-is-my-image-created-48-years-ago)\
[I would like to run my application with a javaagent.](#i-would-like-to-run-my-application-with-a-javaagent)\
[How can I tag my image with a timestamp?](#how-can-i-tag-my-image-with-a-timestamp)

### But, I'm not a Java developer.

See [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel). The tool can build images for languages such as Python, NodeJS, Java, Scala, Groovy, C, Go, Rust, and D.

### What image format does Jib use?

Jib currently builds into the [Docker V2.2](https://docs.docker.com/registry/spec/manifest-v2-2/) image format or [OCI image format](https://github.com/opencontainers/image-spec).

#### Maven

See [Extended Usage](../jib-maven-plugin#extended-usage) for the `<container><format>` configuration.

#### Gradle

See [Extended Usage](../jib-gradle-plugin#extended-usage) for the `container.format` configuration.

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

In [`jib-maven-plugin`](../jib-maven-plugin), you can then use this custom base image by adding the following configuration:

```xml
<configuration>
  <from>
    <image>custom-base-image</image>
  </from>
</configuration>
```

#### Gradle

In [`jib-gradle-plugin`](../jib-gradle-plugin), you can then use this custom base image by adding the following configuration:

```groovy
jib.from.image = 'custom-base-image'
```
</p>
</details>

### Can I ADD a custom directory to the image?

We currently support adding a custom directory with an **incubating** feature. This feature may change in later versions. If your application needs to use custom files, place them into the `src/main/jib` folder. Files placed here will be added to the filesystem of the container. For example, `src/main/jib/foo/bar` would add `/foo/bar` into the container filesystem.

### Can I build to a local Docker daemon?

See [`jib:dockerBuild` for Maven](../jib-maven-plugin#build-to-docker-daemon) and [`jibDockerBuild` for Gradle](../jib-gradle-plugin#build-to-docker-daemon).

You can also [`docker pull`](https://docs.docker.com/engine/reference/commandline/pull/) the image built with Jib to have it available in your local Docker daemon.

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

### Why is my image created 48 years ago?

For reproducibility purposes, Jib sets the creation time of the container images to 0 (January 1st, 1970). If you would like to forgo reproducibility and use a real creation time, set [work in progress](https://github.com/GoogleContainerTools/jib/issues/413).

### I would like to run my application with a javaagent.

See [Can I ADD a custom directory to the image?](#can-i-add-a-custom-directory-to-the-image).

*TODO: Provide more comprehensive solution.*

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
  <to>
    <image>my-image-name:${maven.build.timestamp}</image>
  </to>
</configuration>
```

You can then use the same timestamp to reference the image in other plugins.

#### Gradle

To tag the image with a timestamp, simply set the timestamp as the tag for `to.image` in your `jib` configuration. For example:

```groovy
jib.to.image = 'gcr.io/my-gcp-project/my-app:' + System.nanoTime()
```
