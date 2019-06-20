# Multi-module example (DRAFT)

This example shows how to build multiple containers for a multi-module project in both **Maven** and **Gradle**.

# How the example is set up

The project consists of two microservices:

1. `name-service` - responds with a name
1. `hello-service` - calls `name-service` and responds with a greeting

The **Maven** project is set up with a parent POM ([`pom.xml`](pom.xml)) that defines most of the common build configuration. The module POMs ([`name-service/pom.xml`](name-service/pom.xml) and [`hello-service/pom.xml`](hello-service/pom.xml)) just define inheritance on the parent POM. However, if needed, the module POMs can define custom configuration on `jib-maven-plugin` specific to that module.

The **Gradle** project is set up with a single [`build.gradle`](build.gradle) that configures both subprojects (modules). [`settings.gradle`](settings.gradle) defines which modules to include in the overall build. If needed, the subprojects can have `build.gradle` files to define custom configuration on `jib-gradle-plugin` specific to that module. 

# How to run

Set the `PROJECT_ID` environment variable to your own Google Cloud Platform project:

```shell
export PROJECT_ID=$(gcloud config list --format 'value(core.project)')
```

Run the **Maven** build:

```shell
./mvnw compile jib:build
```

Run the **Gradle** build:

```shell
./gradlew jib
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
Hello Jib Multimodule
```
