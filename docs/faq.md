## Frequently Asked Questions (FAQ)

If a question you have is not answered below, please [submit an issue](/../../issues/new).

| ☑️  Jib User Survey |
| :----- |
| What do you like best about Jib? What needs to be improved? Please tell us by taking a [one-minute survey](https://forms.gle/YRFeamGj51xmgnx28). Your responses will help us understand Jib usage and allow us to serve our customers (you!) better. |

[But, I'm not a Java developer.](#but-im-not-a-java-developer)\
[How do I run the image I built?](#how-do-i-run-the-image-i-built)\
[Where is bash?](#where-is-bash)\
[What image format does Jib use?](#what-image-format-does-jib-use)\
[Why is my image created 48+ years ago?](#why-is-my-image-created-48-years-ago)\
[Where is the application in the container filesystem?](#where-is-the-application-in-the-container-filesystem)\
[How are Jib applications layered?](#how-are-jib-applications-layered)\
[Can I learn more about container images?](#can-i-learn-more-about-container-images)

**How-Tos**\
[How do I set parameters for my image at runtime?](#how-do-i-set-parameters-for-my-image-at-runtime)\
[Can I define a custom entrypoint?](#can-i-define-a-custom-entrypoint-at-runtime)\
[I want to containerize a JAR.](#i-want-to-containerize-a-jar)\
[I need to RUN commands like `apt-get`.](#i-need-to-run-commands-like-apt-get)\
[Can I ADD a custom directory to the image?](#can-i-add-a-custom-directory-to-the-image)\
[I need to add files generated during the build process to a custom directory on the image.](#i-need-to-add-files-generated-during-the-build-process-to-a-custom-directory-on-the-image)\
[Can I build to a local Docker daemon?](#can-i-build-to-a-local-docker-daemon)\
[How do I enable debugging?](#how-do-i-enable-debugging)\
[What would a Dockerfile for a Jib-built image look like?](#what-would-a-dockerfile-for-a-jib-built-image-look-like)\
[How can I inspect the image Jib built?](#how-can-i-inspect-the-image-jib-built)\
[I would like to run my application with a javaagent.](#i-would-like-to-run-my-application-with-a-javaagent)\
[How can I tag my image with a timestamp?](#how-can-i-tag-my-image-with-a-timestamp)\
[How do I specify a platform in the manifest list (or OCI index) of a base image?](#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image)

**Build Problems**\
[How can I diagnose problems pulling or pushing from remote registries?](#how-can-i-diagnose-problems-pulling-or-pushing-from-remote-registries)\
[What should I do when the registry responds with Forbidden or DENIED?](#what-should-i-do-when-the-registry-responds-with-forbidden-or-denied)\
[What should I do when the registry responds with UNAUTHORIZED?](#what-should-i-do-when-the-registry-responds-with-unauthorized)\
[How do I configure a proxy?](#how-do-i-configure-a-proxy)\
[How can I examine network traffic?](#how-can-i-examine-network-traffic)\
[How do I view debug logs for Jib?](#how-do-i-view-debug-logs-for-jib)

**Launch Problems**\
[I am seeing `ImagePullBackoff` on my pods.](#i-am-seeing-imagepullbackoff-on-my-pods-in-minikube)\
[I am seeing `Method Not Found` or `Class Not Found` errors when building.](#i-am-seeing-method-not-found-or-class-not-found-errors-when-building)\
[Why won't my container start?](#why-wont-my-container-start)

---

### But, I'm not a Java developer.

See [rules_docker](https://github.com/bazelbuild/rules_docker) for a similar existing container image build tool for the [Bazel build system](https://github.com/bazelbuild/bazel). The tool can build images for languages such as Python, NodeJS, Java, Scala, Groovy, C, Go, Rust, and D.

### How do I run the image I built?

If you built your image directly to the Docker daemon using `jib:dockerBuild` (Maven) or `jibDockerBuild` (Gradle), you simply need to use `docker run <image name>`.

If you built your image to a registry using `jib:build` (Maven) or `jib` (Gradle), you will need to pull the image using `docker pull <image name>` before using `docker run`.

To run your image on Kubernetes, you can use kubectl:

```shell
kubectl run jib-deployment --image=<image name>
```

For more information, see [steps 4-6 of the Kubernetes Engine deployment tutorial](https://cloud.google.com/kubernetes-engine/docs/tutorials/hello-app#step_4_create_a_container_cluster).

### Where is bash?

By default, Jib uses [`distroless/java`](https://github.com/GoogleContainerTools/distroless/tree/master/java) as the base image. Distroless images contain only runtime dependencies. They do not contain package managers, shells or any other programs you would expect to find in a standard Linux distribution. Check out the [distroless project](https://github.com/GoogleContainerTools/distroless#distroless-docker-images) for more information about distroless images.

If you would like to include a shell for debugging, set the base image to `gcr.io/distroless/java:debug` instead. The shell will be located at `/busybox/sh`. Note that `:debug` images are **not** recommended for production use.

<details>
<summary>Configuring a base image in Maven (click to expand)</summary>
<p>

In [`jib-maven-plugin`](../jib-maven-plugin), you can use the `gcr.io/distroless/java:debug` base image by adding the following configuration:

```xml
<configuration>
  <from>
    <image>gcr.io/distroless/java:debug</image>
  </from>
</configuration>
```
</p>
</details>

<details>
<summary>Configuring a base image in Gradle (click to expand)</summary>
<p>

In [`jib-gradle-plugin`](../jib-gradle-plugin), you can use the `gcr.io/distroless/java:debug` base image by adding the following configuration:

```groovy
jib.from.image = 'gcr.io/distroless/java:debug'
```
</p>
</details><br />

You can then run the image in shell form with Docker: `docker run -it --entrypoint /busybox/sh <image name>`

### What image format does Jib use?

Jib currently builds into the [Docker V2.2](https://docs.docker.com/registry/spec/manifest-v2-2/) image format or [OCI image format](https://github.com/opencontainers/image-spec).

#### Maven

See [Extended Usage](../jib-maven-plugin#extended-usage) for the `<container><format>` configuration.

#### Gradle

See [Extended Usage](../jib-gradle-plugin#extended-usage) for the `container.format` configuration.

### Why is my image created 48+ years ago?

For reproducibility purposes, Jib sets the creation time of the container images to the Unix epoch (00:00:00, January 1st, 1970 in UTC). If you would like to use a different timestamp, set the `jib.container.creationTime` / `<container><creationTime>` parameter to an ISO 8601 date-time. You may also use the value `USE_CURRENT_TIMESTAMP` to set the creation time to the actual build time, but this sacrifices reproducibility since the timestamp will change with every build.

<details>
<summary>Setting <code>creationTime</code> parameter (click to expand)</summary>
<p>

#### Maven

```xml
<configuration>
  <container>
    <creationTime>2019-07-15T10:15:30+09:00</creationTime>
  </container>
</configuration>
```

#### Gradle

```groovy
jib.container.creationTime = '2019-07-15T10:15:30+09:00'
```

</p>
</details>

Note that the modification time of the files in the built image put by Jib will still be 1 second past the epoch. The file modification time can be configured using [`<container><filesModificationTime>`](../jib-maven-plugin#container-object) (Maven) or [`jib.container.filesModificationTime`](../jib-gradle-plugin#container-closure) (Gradle).

#### Please tell me more about reproducibility!

_Reproducible_ means that given the same inputs, a build should produce the same outputs.  Container images are uniquely identified by a digest (or a hash) of the image contents and image metadata.  Tools and infrastructure such the Docker daemon, Docker Hub, registries, Kubernetes, etc) treat images with different digests as being different.

To ensure that a Jib build is reproducible — that the rebuilt container image has the same digest — Jib adds files and directories in a consistent order, and sets consistent creation- and modification-times and permissions for all files and directories.  Jib also ensures that the image metadata is recorded in a consistent order, and that the container image has a consistent creation time.  To ensure consistent times, files and directories are recorded as having a creation and modification time of 1 second past the Unix Epoch (1970-01-01 00:00:01.000 UTC), and the container image is recorded as being created on the Unix Epoch.  Setting `container.creationTime` to `USE_CURRENT_TIMESTAMP` and then rebuilding an image will produce a different timestamp for the image creation time, and so the container images will have different digests and appear to be different.

For more details see [reproducible-builds.org](https://reproducible-builds.org).

### Where is the application in the container filesystem?

Jib packages your Java application into the following paths on the image:

* `/app/libs/` contains all the dependency artifacts
* `/app/resources/` contains all the resource files
* `/app/classes/` contains all the classes files
* the contents of the extra directory (default `src/main/jib`) are placed relative to the container's root directory (`/`)

### How are Jib applications layered?

Jib makes use of [layering](https://containers.gitbook.io/build-containers-the-hard-way/#layers) to allow for fast rebuilds - it will only rebuild the layers containing files that changed since the previous build and will reuse cached layers containing files that didn't change. Jib organizes files in a way that groups frequently changing files separately from large, rarely changing files. For example, `SNAPSHOT` dependencies are placed in a separate layer from other dependencies, so that a frequently changing `SNAPSHOT` will not force the entire dependency layer to rebuild itself.

Jib applications are split into the following layers:
* Classes
* Resources
* Project dependencies
* Snapshot dependencies
* All other dependencies
* Each extra directory (`jib.extraDirectories` in Gradle, `<extraDirectories>` in Maven) builds to its own layer

### Can I learn more about container images?

If you'd like to learn more about container images, [@coollog](https://github.com/coollog) has a guide: [Build Containers the Hard Way](https://containers.gitbook.io/build-containers-the-hard-way/), which takes a deep dive into everything involved in getting your code into a container and onto a container registry.


## Configuring Jib

### How do I set parameters for my image at runtime?

#### JVM Flags

For the default `distroless/java` base image, you can use the `JAVA_TOOL_OPTIONS` environment variable (note that other JRE images may require using other environment variables):

Using Docker: `docker run -e "JAVA_TOOL_OPTIONS=<JVM flags>" <image name>`

Using Kubernetes:
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: <name>
    image: <image name>
    env:
    - name: JAVA_TOOL_OPTIONS
      value: <JVM flags>
```

#### Other Environment Variables

Using Docker: `docker run -e "NAME=VALUE" <image name>`

Using Kubernetes:
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: <name>
    image: <image name>
    env:
    - name: NAME
      value: VALUE
```

#### Arguments to Main

Using Docker: `docker run <image name> <arg1> <arg2> <arg3>`

Using Kubernetes:
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: <name>
    image: <image name>
    args:
    - <arg1>
    - <arg2>
    - <arg3>
```

For more information, see the [`JAVA_TOOL_OPTIONS` environment variable](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html), the [`docker run -e` reference](https://docs.docker.com/engine/reference/run/#env-environment-variables), and [defining environment variables for a container in Kubernetes](https://kubernetes.io/docs/tasks/inject-data-application/define-environment-variable-container/).

### Can I define a custom entrypoint at runtime?

Normally, the plugin sets a default entrypoint for java applications, or lets you configure a custom entrypoint using the `container.entrypoint` configuration parameter. You can also override the default/configured entrypoint by defining a custom entrypoint when running the container. See [`docker run --entrypoint` reference](https://docs.docker.com/engine/reference/run/#entrypoint-default-command-to-execute-at-runtime) for running the image with Docker and overriding the entrypoint command, or see [Define a Command and Arguments for a Container](https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/) for running the image in a [Kubernetes](https://kubernetes.io/) Pod and overriding the entrypoint command.

### <a name="i-want-to-containerize-an-executable-jar"></a>I want to containerize a JAR.

The intention of Jib is to add individual class files, resources, and dependency JARs into the container instead of putting a JAR. This lets Jib choose an opinionated, optimal layout for the application on the container image, which also allows it to skip the extra JAR-packaging step.

However, you can set `<containerizingMode>packaged` (Maven) or `jib.containerizingMode = 'packaged'` (Gradle) to containerize a JAR, but note that your application will always be run via `java -cp ... your.MainClass` (even if it is an executable JAR). Some disadvantages of setting `containerizingMode='packaged'`:

- You need to run the JAR-packaging step (`mvn package` in Maven or the `jar` task in Gradle).
- Reduced granularity in building and caching: if any of your Java source files or resource files are updated, not only the JAR has to be rebuilt, but the entire layer containing the JAR in the image has to be recreated and pushed to the destination.
- If it is a fat or shaded JAR embedding all dependency JARs, you are duplicating the dependency JARs in the image. Worse, it results in far more reduced granularity in building and caching, as dependency JARs can be huge and all of them need to be pushed repeatedly even if they do not change.

Note that for runnable JARs/WARs, currently Jib does not natively support creating an image that runs a JAR (or WAR) through `java -jar runnable.jar` (although it is not impossible to configure Jib to do so at the expense of more complex project setup.)

### I need to RUN commands like `apt-get`.

Running commands like `apt-get` slows down the container build process. We **do not recommend or support** running commands as part of the build.

However, if you need to run commands, you can build a custom image and configure Jib to use it as the base image.

<details>
<summary>Base image configuration examples (click to expand)</summary>
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

Yes, using the _extra directories_ feature. See the [Maven](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#adding-arbitrary-files-to-the-image) and [Gradle](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#adding-arbitrary-files-to-the-image) docs for examples.

### I need to add files generated during the build process to a custom directory on the image.

If the current extra directories design doesn't meet your needs (e.g. you need to set up the extra files directory with files generated during the build process), you can use additional goals/tasks to create the extra directory as part of your build.

<details>
<summary>File copying examples (click to expand)</summary>
<p>

#### Maven

In Maven, you can use the `maven-resources-plugin` to copy files to your extra directory. For example, if you generate files in `target/generated/files` and want to add them to `/my/files` on the container, you can add the following to your `pom.xml`:

```xml
<plugins>
  ...
  <plugin>
    <artifact>jib-maven-plugin</artifact>
    ...
    <configuration>
      <extraDirectories>
        <paths>
          <path>${project.basedir}/target/extra-directory/</path>
        </paths>
      </extraDirectories>
    </configuration>
  </plugin>
  ...
  <plugin>
    <artifact>maven-resources-plugin</artifact>
    <version>3.1.0</version>
    <configuration>
      <outputDirectory>${project.basedir}/target/extra-directory/my/files</outputDirectory>
      <resources>
        <resource>
          <directory>${project.basedir}/target/generated/files</directory>
        </resource>
      </resources>
    </configuration>
  </plugin>
  ...
</plugins>
```

The `copy-resources` goal will run automatically before compile, so if you are copying files from your build output to the extra directory, you will need to either set the life-cycle phase to `post-compile` or later, or run the goal manually:

```sh
mvn compile resources:copy-resources jib:build
```

#### Gradle

The same can be accomplished in Gradle by using a `Copy` task. In your `build.gradle`:

```groovy
jib.extraDirectories = file('build/extra-directory')

task setupExtraDir(type: Copy) {
  from file('build/generated/files')
  into file('build/extra-directory/my/files')
}
tasks.jib.dependsOn setupExtraDir
```

The files will be copied to your extra directory when you run the `jib` task.

</p>
</details>

### Can I build to a local Docker daemon?

There are several ways of doing this:

- Use [`jib:dockerBuild` for Maven](../jib-maven-plugin#build-to-docker-daemon) or [`jibDockerBuild` for Gradle](../jib-gradle-plugin#build-to-docker-daemon) to build directly to your local Docker daemon.
- Use [`jib:buildTar` for Maven](../jib-maven-plugin#build-an-image-tarball) or [`jibBuildTar` for Gradle](../jib-gradle-plugin#build-an-image-tarball) to build the image to a tarball, then use `docker load --input` to load the image into Docker (the tarball built with these commands will be located in `target/jib-image.tar` for Maven and `build/jib-image.tar` for Gradle by default).
- [`docker pull`](https://docs.docker.com/engine/reference/commandline/pull/) the image built with Jib to have it available in your local Docker daemon.
- Alternatively, instead of using a Docker daemon, you can run a local container registry, such as [Docker registry](https://docs.docker.com/registry/deploying/) or other repository managers, and point Jib to push to the local registry.

### How do I enable debugging?

If using the `distroless/java` base image, then use the [`JAVA_TOOL_OPTIONS`](#how-do-i-set-parameters-for-my-image-at-runtime) to pass along debugging configuration arguments.  For example, to have the remote VM accept local debug connections on port 5005, but not suspend:
```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005
```

Then connect your debugger to port 5005 on your local host.  You can port-forward the container port to a localhost port for easy access.

Using Docker: `docker run -p 5005:5005 <image>`

Using Kubernetes: `kubectl port-forward <pod name> 5005:5005`

Beware: in Java 8 and earlier, specifying only a port meant that the JDWP socket was open to all incoming connections which is insecure.  It is recommended to limit the debug port to localhost.


### I would like to run my application with a javaagent.

You can run your container with a javaagent by placing it somewhere in the `src/main/jib` directory to add it to the container's filesystem, then pointing to it using Jib's `container.jvmFlags` configuration.

#### Maven

```xml
<configuration>
  <container>
    <jvmFlags>
      <jvmFlag>-javaagent:/myfolder/agent.jar</jvmFlag>
    </jvmFlags>    
  </container>
</configuration>
```

#### Gradle

```groovy
jib.container.jvmFlags = ['-javaagent:/myfolder/agent.jar']
```

See also [Can I ADD a custom directory to the image?](#can-i-add-a-custom-directory-to-the-image)

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


### What would a Dockerfile for a Jib-built image look like?

A Dockerfile that performs a Jib-like build is shown below:

```Dockerfile
# Jib uses distroless java as the default base image
FROM gcr.io/distroless/java:latest

# Multiple copy statements are used to break the app into layers, allowing for faster rebuilds after small changes
COPY dependencyJars /app/libs
COPY snapshotDependencyJars /app/libs
COPY projectDependencyJars /app/libs
COPY resources /app/resources
COPY classFiles /app/classes

# Jib's extra directory ("src/main/jib" by default) is used to add extra, non-classpath files
COPY src/main/jib /

# Jib's default entrypoint when container.entrypoint is not set
ENTRYPOINT ["java", jib.container.jvmFlags, "-cp", "/app/resources:/app/classes:/app/libs/*", jib.container.mainClass]
CMD [jib.container.args]
```

When unset, Jib will infer the value for `jib.container.mainClass`.

Some plugins, such as the [Docker Prepare Gradle Plugin](https://github.com/gclayburg/dockerPreparePlugin), will even automatically generate a Docker context for your project, including a Dockerfile.

### How can I inspect the image Jib built?

To inspect the image that is produced from the build using Docker, you can use commands such as `docker inspect your/image:tag` to view the image configuration, or you can also download the image using `docker save` to manually inspect the container image. Other tools, such as [dive](https://github.com/wagoodman/dive), provide nicer UI to inspect the image.

### How do I specify a platform in the manifest list (or OCI index) of a base image?

Newer Jib verisons added an _incubating feature_ that provides limited support for selecting a base image with the desired platform from a manifest list. For example,

```xml
  <from>
    <image>... image reference to a manifest list ...</image>
    <platforms>
      <platform>
        <architecture>arm64</architecture>
        <os>linux</os>
      </platform>
    </platforms>
  </from>
```

```gradle
jib.from {
  image = '... image reference to a manifest list ...'
  platforms {
    platform {
      architecture = 'arm64'
      os = 'linux'
    }
  }
}
```

The default platform is "amd64/linux" if not specified, whose behavior is backward-compatible.

As an incubating feature, there are certain limitations:
- OCI image indices are not supported (as opposed to Docker manifest lists).
- Supports specifying only one platform.
- Only `architecture` and `os` are supported. If the base image manifest list contains multiple images with the given architecture and os, the first image will be selected.

Make sure to specify a manifest _list_ in `<from><image>` (whether by a tag name or a digest (`@sha256:...`)). For troubleshooting, you may want to check what platforms a manifest list contains. To view a manifest, [enable experimental docker CLI](https://docs.docker.com/engine/reference/commandline/cli/#experimental-features) features and then run the [manifest inspect](https://docs.docker.com/engine/reference/commandline/manifest_inspect/) command.

```
$ docker manifest inspect openjdk:8
{         
   ...
   // This confirms that openjdk:8 points to a manifest list.
   "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json",
   "manifests": [
      {
         // This entry in the list points to the manifest for the ARM64/Linux manifest.
         "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
         ...
         "digest": "sha256:1fbd49e3fc5e53154fa93cad15f211112d899a6b0c5dc1e8661d6eb6c18b30a6",
         "platform": {
            "architecture": "arm64",
            "os": "linux",
            "variant": "v8"
         }
      }
   ]
}
```

## Build Problems

### <a name="registry-errors"></a>How can I diagnose problems pulling or pushing from remote registries?

There are a few reasons why Jib may be unable to connect to a remote registry, including:

- **Registry reports FORBIDDEN.** See [_What should I do when the registry responds with Forbidden or DENIED?_](#what-should-i-do-when-the-registry-responds-with-forbidden-or-denied)
- **Registry reports UNAUTHORIZED.** See [_What should I do when the registry responds with UNAUTHORIZED?_](#what-should-i-do-when-the-registry-responds-with-unauthorized)
- **Access requires a proxy.** See [_How do I configure a proxy?_](#how-do-i-configure-a-proxy) for details.
- **The registry does not support HTTPS.** We do not pass authentication details on non-HTTPS connections, though this can be overridden with the `sendCredentialsOverHttp` system property, but it is not recommend  (_version 0.9.9_).
- **The registry's SSL certificates have expired or are not trusted.**  We have a separate document on [handling registries that use self-signed certificates](self_sign_cert.md), which may also apply if the SSL certificate is signed by an untrusted Certificate Authority.  Jib supports an  `allowInsecureRegistries` flag to ignore SSL certificate validation, but it is not recommend (_version 0.9.9_).
- **The registry does not support the [Docker Image Format V2 Schema 2](https://github.com/GoogleContainerTools/jib/issues/601)** (sometimes referred to as _v2-2_).  This problem is usually shown by failures wth `INVALID_MANIFEST` errors. Some registries can be configured to support V2-2 such as [Artifactory](https://www.jfrog.com/confluence/display/RTF/Docker+Registry#DockerRegistry-LocalDockerRepositories) and [OpenShift](https://docs.openshift.com/container-platform/3.9/install_config/registry/extended_registry_configuration.html#middleware-repository-acceptschema2). Other registries, such as Quay.io/Quay Enterprise, are in the process of adding support.


### What should I do when the registry responds with Forbidden or DENIED?

If the registry returns `403 Forbidden` or `"code":"DENIED"`, it often means Jib successfully authenticated using your credentials but the credentials do not have permissions to pull or push images. Make sure your account/role has the permissions to do the operation.

Depending on registry implementations, it is also possible that the registry actually meant you are not authenticated. See [What should I do when the registry responds with UNAUTHORIZED?](#what-should-i-do-when-the-registry-responds-with-unauthorized) to ensure you have set up credentials correctly.

### What should I do when the registry responds with UNAUTHORIZED?

If the registry returns `401 Unauthorized` or `"code":"UNAUTHORIZED"`, it is often due to credential misconfiguration. Examples:

* You did not configure auth information in the default places where Jib searches.
   - `$HOME/.docker/config.json`, [one of the configuration files](https://docs.docker.com/engine/reference/commandline/cli/#configuration-files) for the `docker` command line tool. See [configuration files document](https://docs.docker.com/engine/reference/commandline/cli/#configuration-files), [credential store](https://docs.docker.com/engine/reference/commandline/login/#credentials-store) and [credential helper](https://docs.docker.com/engine/reference/commandline/login/#credential-helpers) sections, and [this](https://github.com/GoogleContainerTools/jib/issues/101) for how to configure auth. For example, you can do `docker login` to save auth in `config.json`, but it is often recommended to configure a credential helper (also configurable in `config.json`).
   - (Starting from Jib 2.2.0) You can set the environment variable `$DOCKER_CONFIG` for the _directory_ containing Docker configuration files. `$DOCKER_CONFIG/config.json` takes precedence over `$HOME/.docker/config.json`.
   - Some common credential helpers on `$PATH` (for example, `docker-credential-osxkeychain`, `docker-credential-ecr-login`, etc.) for well-known registries.
   - Jib configurations
      - Configuring credential helpers: [`<from/to><credHelper>`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#using-docker-credential-helpers) (Maven) / [`from/to.credHelper`](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#using-docker-credential-helpers) (Gradle)
      - Specific credentials (not recommend): [`<from/to><auth><username>/<password>`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#using-specific-credentials) or in [`settings.xml`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#using-maven-settings) (Maven) / [`from/to.auth.username/password`](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#using-specific-credentials) (Gradle)
      - These parameters can also be set through properties: [Maven](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#system-properties) / [Gradle](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#system-properties)
* `$HOME/.docker/config.json` may also contain short-lived authorizations in the `auths` block that may have expired. In the case of Google Container Registry, if you had previously used `gcloud docker` to configure these authorizations, you should remove these stale authorizations by editing your `config.json` and deleting lines from `auths` associated with `gcr.io` (for example: `"https://asia.gcr.io"`). You can then run `gcloud auth configure-docker` to correctly configure the `credHelpers` block for more robust interactions with gcr.
* Different auth configurations exist in multiple places, and Jib is not picking up the auth information you are working on.
* You configured a credential helper, but the helper is not on `$PATH`. This is especially common when running Jib inside IDE where the IDE binary is launched directly from an OS menu and does not have access to your shell's environment.
* Configured credentials have access to the base image repository but not to the target image repository (or vice versa).
* Typos in username, password, image names, repository names, or registry names. This is a very common error.
* Image names do not conform to the structure or policy that a registry requires. For example, [Docker Hub returns 401 Unauthorized](https://github.com/GoogleContainerTools/jib/issues/2650#issuecomment-667323777) when trying to use a multi-level repository name.
* Incorrect port number in image references (`registry.hostname:<port>/...`).
* You are using a private registry without HTTPS. See [How can I diagnose problems pulling or pushing from remote registries?](#how-can-i-diagnose-problems-pulling-or-pushing-from-remote-registries).

Note, if Jib was able to retrieve credentials, you should see a log message like these:

```
Using credentials from Docker config (/home/user/.docker/config.json) for localhost:5000/java
```
```
Using credential helper docker-credential-gcr for gcr.io/project/repo
```
```
Using credentials from Maven settings file for gcr.io/project/repo
```
```
Using credentials from <from><auth> for gcr.io/project/repo
```
```
Using credentials from to.auth for gcr.io/project/repo
```

If you encounter issues interacting with a registry other than `UNAUTHORIZED`, check ["How can I diagnose problems pulling or pushing from remote registries?"](#how-can-i-diagnose-problems-pulling-or-pushing-from-remote-registries).

### How do I configure a proxy?

Jib currently requires configuring your build tool to use the appropriate [Java networking properties](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html) (`https.proxyHost`, `https.proxyPort`, `https.proxyUser`, `https.proxyPassword`).


### How can I examine network traffic?

It can be useful to examine network traffic to diagnose connectivity issues. Jib uses the Google HTTP client library to interact with registries which logs HTTP requests using the JVM-provided `java.util.logging` facilities.  It is very helpful to serialize Jib's actions using the `jib.serialize` property.

To see the HTTP traffic, create a `logging.properties` file with the following:
```
handlers = java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level=ALL

# CONFIG hides authentication data
# ALL includes authentication data
com.google.api.client.http.level=CONFIG
```

And then launch your build tool as follows:
```sh
mvn --batch-mode -Djava.util.logging.config.file=path/to/logging.properties -Djib.serialize=true ...
```
or
```sh
gradle --no-daemon --console=plain -Djava.util.logging.config.file=path/to/logging.properties -Djib.serialize=true ...
```

**Note**: Jib Gradle plugins prior to version 2.2.0 have an issue generating HTTP logs ([#2356](https://github.com/GoogleContainerTools/jib/issues/2356)).

You may wish to enable the debug logs too (`-X` for Maven, or `--debug --stacktrace` for Gradle).

When configured correctly, you should see logs like this:
```
Mar 31, 2020 9:55:52 AM com.google.api.client.http.HttpResponse <init>
CONFIG: -------------- RESPONSE --------------
HTTP/1.1 202 Accepted
Content-Length: 0
Docker-Distribution-Api-Version: registry/2.0
Docker-Upload-Uuid: 6292f0d7-93cb-4a8e-8336-78a1bf7febd2
Location: https://registry-1.docker.io/v2/...
Range: 0-657292
Date: Tue, 31 Mar 2020 13:55:52 GMT
Strict-Transport-Security: max-age=31536000

Mar 31, 2020 9:55:52 AM com.google.api.client.http.HttpRequest execute
CONFIG: -------------- REQUEST  --------------
PUT https://registry-1.docker.io/v2/...
Accept:
Accept-Encoding: gzip
Authorization: <Not Logged>
User-Agent: jib 2.1.1-SNAPSHOT jib-maven-plugin Google-HTTP-Java-Client/1.34.0 (gzip)
```
  
### How do I view debug logs for Jib?

Maven: use `mvn -X -Djib.serialize=true` to enable more detailed logging and serialize Jib's actions.

Gradle: use `gradle --debug -Djib.serialize=true` to enable more detailed logging and serialize Jib's actions.


## Launch problems

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

### I am seeing `Method Not Found` or `Class Not Found` errors when building.

Sometimes when upgrading your gradle build plugin versions, you may experience errors due to mismatching versions of dependencies pulled in (for example: [issues/2183](https://github.com/GoogleContainerTools/jib/issues/2183)). This can be due to the buildscript classpath loading behavior described [on gradle forums](https://discuss.gradle.org/t/version-is-root-build-gradle-buildscript-is-overriding-subproject-buildscript-dependency-versions/20746/3). 

This commonly appears in multi module gradle projects. A solution to this problem is to define all of your plugins in the base project and apply them selectively in your subprojects as needed. This should help alleviate the problem of the buildscript classpath using older versions of a library.

`build.gradle` (root)
```groovy
plugins {
  id 'com.google.cloud.tools.jib' version 'x.y.z' apply false
}
```

`build.gradle` (sub-project)
```groovy
plugins {
  id 'com.google.cloud.tools.jib'
}
```

### Why won't my container start?

There are some common reasons why containers fail on launch.

#### My shell script won't run
 
The default base image used by Jib, ([`distoless/java`](https://github.com/GoogleContainerTools/distroless/tree/master/java)), does not include a shell, and thus shell scripts won't launch.

Solution: use a different base image with a shell.

#### The container fails with `exec` errors 

A Jib user reported an error launching their container:
```
standard_init_linux.go:211 exec user process caused "no such file or directory"
```

On examining the container structure with [Dive](https://github.com/wagoodman/dive), the user discovered that the contents of the `/lib` directory had disappeared.

The user had used Jib's ability to install extra files into the image ([Maven](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#adding-arbitrary-files-to-the-image), [Gradle](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#adding-arbitrary-files-to-the-image)) to install a library file by placing it in `src/main/jib/lib/libfoo.so`. This would normally cause the `libfoo.so` to be installed in the image as `/lib/libfoo.so`. But `/lib` and `/lib64` in the user's base image were symbolic links. Jib does not follow such symbolic links when creating the image. And at container initialization time, Docker treats these symlinks as a file, and thus the symbolic link was replaced with `/lib` as a new directory. As a result, none of the system shared libraries were resolved and dynamically-linked programs failed.

Solution: The user installed the file in a different location.
