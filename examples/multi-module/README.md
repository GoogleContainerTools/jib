# Multi-module example

This example shows how to build multiple containers for a multi-module project in both **Maven** and **Gradle**.

# How the example is set up

The project consists of two microservices and a library:

1. `name-service` - responds with a name
1. `shared-library` - a project dependency used by `name-service`
1. `hello-service` - calls `name-service` and responds with a greeting

The **Maven** project is set up with a parent POM ([`pom.xml`](pom.xml)) that defines most of the common build configuration. The module POMs ([`name-service/pom.xml`](name-service/pom.xml) and [`hello-service/pom.xml`](hello-service/pom.xml)) just define inheritance on the parent POM. However, if needed, the module POMs can define custom configuration on `jib-maven-plugin` specific to that module.

The **Gradle** project is set up with a parent [`build.gradle`](build.gradle) that sets some common configuration up for all projects, with each sub-project containing its own `build.gradle` with some custom configuration. [`settings.gradle`](settings.gradle) defines which modules to include in the overall build.

## Reproducibility of dependency module `shared-library`

Since dependency module builds happen with the underlying build system
(maven/gradle), we must add some extra configuration to ensure that the
resulting `jar` that is built conforms to our reproducibility expectations.
The module [`shared-library`](shared-library) uses the [Reproducible Build Maven Plugin](https://zlika.github.io/reproducible-build-maven-plugin/)
for maven, and some special `Jar` properties ([`preserveFileTimestamps`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:preserveFileTimestamps),
[`reproducibleFileOrder`](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar:reproducibleFileOrder))
in gradle to achieve this. This configuration can be seen in the
`shared-library`'s [`pom.xml`](shared-library/pom.xml) and [`build.gradle`](shared-library/build.gradle).

Care must be taken when adding custom attributes to a `MANIFEST.MF`.
Attributes whose values change on every build can affect reproducibility even
with the modifications outlined above.

# How to run

Set the `PROJECT_ID` environment variable to your own Google Cloud Platform project:

```shell
export PROJECT_ID=$(gcloud config list --format 'value(core.project)')
```

Run the **Maven** build:

```shell
# build everything
./mvnw package jib:build

# build just hello-service
./mvnw compile jib:build -pl hello-service

# build name-service (with dependency on shared-library)
# you must use "package" for jib to correctly package "shared-library" with the
# "name-service" container
./mvnw package jib:build -pl name-service -am
```

Run the **Gradle** build:

```shell
# build everything
./gradlew jib

# build just hello-service
./gradlew :hello-service:jib

# build name-service (with dependency on shared-library)
./gradlew :name-service:jib
```

You can also run `./maven-build.sh` or `./gradle-build.sh` as a shorthand.

# Where are the containers

The output of the build should have the container image references highlighted in <span style="color: cyan">cyan</span>. You can expect them to be at:

- `name-service`: `gcr.io/PROJECT_ID/name-service:0.1.0`
- `hello-service`: `gcr.io/PROJECT_ID/hello-service:0.1.0`

# How to run on Kubernetes

[`kubernetes.yaml`](kubernetes.yaml) defines the manifests for running the two microservices on Kubernetes. Make sure to open the file and change `PROJECT_ID` to your own Google Cloud Platform project.

Create a Kubernetes cluster:

```shell
gcloud container clusters create jib
```

Apply to your Kubernetes cluster:

```shell
kubectl apply -f kubernetes.yaml
```

Find the `EXTERNAL-IP` of the `hello-service`.

```
NAME                TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)        AGE
svc/hello-service   LoadBalancer   10.19.243.223   35.237.89.148   80:30196/TCP   1m
```

Visit the IP in your web browser and you should see:

```
Hello Jib Multimodule: A string from 'shared-library'
```
