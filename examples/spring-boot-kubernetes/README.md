# Dockerize and deploy a Spring Boot application to Kubernetes

This is an example of how to easily build a Docker image for a Spring Boot application with Jib and deploy it to Kubernetes with `kubectl`.

<!-- Dockerize and deploy a @springboot app to #Kubernetes in seconds @kubernetesio @docker #jib -->
<p align="center">
    <a href="https://twitter.com/intent/tweet?text=Dockerize+and+deploy+a+%40springboot+app+to+%23Kubernetes+in+seconds+%40kubernetesio+%40docker+%23jib&url=https://asciinema.org/a/192977">
    <img src="dockerize-spring-boot-jib.gif" width="600" alt="Dockerize Spring Boot app with Jib and deploy to Kubernetes">
  </a>
</p>

## Try it yourself

*Make sure you have `kubectl` [configured with a cluster](https://cloud.google.com/kubernetes-engine/docs/how-to/creating-a-cluster).*

```shell
IMAGE=<your image, eg. gcr.io/my-project/spring-boot-jib>

./mvnw compile jib:build -Dimage=$IMAGE

kubectl run spring-boot-jib --image=$IMAGE --port=8080 --restart=Never

# Wait until pod is running
kubectl port-forward spring-boot-jib 8080 > /dev/null 2>&1 &
```
```shell
curl localhost:8080
> Greetings from Kubernetes!
```

<!-- Run a @springboot app on #Kubernetes in seconds @kubernetesio #jib #java -->
Give it a [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Run+a+%40springboot+app+on+%23Kubernetes+in+seconds+%40kubernetesio+%23jib+%23java&url=https://github.com/GoogleContainerTools/jib/tree/master/examples/spring-boot-kubernetes&hashtags=docker)

*For Gradle just run `./gradlew jib --image=$IMAGE` instead.*

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/spring-boot-kubernetes)](https://github.com/igrigorik/ga-beacon)
