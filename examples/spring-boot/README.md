# Dockerize a Spring Boot application using Jib

This is an example of how to easily build a Docker image for a Spring Boot application with Jib.

## Try it yourself

You can containerize the application with one of the following commands.

**Maven:**
```shell
./mvnw compile jib:build -Dimage=<your image, eg. gcr.io/my-project/spring-boot-jib>
```

**Gradle:**
```shell
./gradlew jib --image=<your image, eg. gcr.io/my-project/spring-boot-jib>
```

## Deploying to Kubernetes using `kubectl`

<!-- Dockerize and deploy a @springboot app to #Kubernetes in seconds @kubernetesio @docker #jib -->
<p align="center">
    <a href="https://twitter.com/intent/tweet?text=Dockerize+and+deploy+a+%40springboot+app+to+%23Kubernetes+in+seconds+%40kubernetesio+%40docker+%23jib&url=https://asciinema.org/a/192977">
    <img src="dockerize-spring-boot-jib.gif" width="600" alt="Dockerize Spring Boot app with Jib and deploy to Kubernetes">
  </a>
</p>

*Make sure you have `kubectl` installed and [configured with a cluster](https://cloud.google.com/kubernetes-engine/docs/how-to/creating-a-cluster).*

```shell
IMAGE=<your image, eg. gcr.io/my-project/spring-boot-jib>

./mvnw compile jib:build -Dimage=$IMAGE

kubectl run spring-boot-jib --image=$IMAGE --port=8080 --restart=Never

# Wait until pod is running
kubectl port-forward spring-boot-jib 8080
```
```shell
curl localhost:8080
> Greetings from Spring Boot and Jib!
```

\* If you are using Gradle, use `./gradlew jib --image=$IMAGE` instead of the `./mvnw` command

<!-- Run a @springboot app on #Kubernetes in seconds @kubernetesio #jib #java -->
Give it a [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Run+a+%40springboot+app+on+%23Kubernetes+in+seconds+%40kubernetesio+%23jib+%23java&url=https://github.com/GoogleContainerTools/jib/tree/master/examples/spring-boot&hashtags=docker)

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

## Build and run on Google Cloud

[![Run on Google Cloud](https://deploy.cloud.run/button.svg)](https://deploy.cloud.run?git_repo=https://github.com/GoogleContainerTools/jib.git&dir=examples/spring-boot)
