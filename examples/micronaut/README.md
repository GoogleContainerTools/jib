# Containerize a [Micronaut](http://micronaut.io/) app with Jib

This is an example to show how easily a docker image can be build [Micronaut framework Groovy/Java application](http://guides.micronaut.io/creating-your-first-micronaut-app-groovy/guide/index.html) with Jib.

<!-- Dockerize and run a "Hello World" @Java @micronautfw app with #Jib in seconds -->
<p align="center">
    <a href="https://twitter.com/intent/tweet?text=Dockerize%20and%20run%20a%20%22Hello%20World%22%20%40Java%20%40micronautfw%20app%20with%20%23Jib%20in%20seconds&url=https://asciinema.org/a/191805&hashtags=docker,kubernetes">
    <img src="dockerize-micronaut-jib.gif?raw=true" width="600" alt="Dockerize Micronaut app with Jib">
  </a>
</p>

## Quickstart

### With Docker

```shell
./gradlew jibDockerBuild

docker run -d -p 8080:8080 micronaut-jib:0.1
```
```shell
curl localhost:8080/hello
> Hello World
```

<!-- Dockerize and run a "Hello World" @Java @micronautfw app with #Jib in seconds -->
Give it a [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Dockerize%20and%20run%20a%20%22Hello%20World%22%20%40Java%20%40micronautfw%20app%20with%20%23Jib%20in%20seconds&url=https://github.com/GoogleContainerTools/jib/tree/master/examples/micronaut&hashtags=docker,kubernetes)

### With Kubernetes

```shell
IMAGE=<your image, eg. gcr.io/my-project/micronaut-jib>

./gradlew jib --image=$IMAGE

kubectl run micronaut-jib --image=$IMAGE --port=8080 --restart=Never

# Wait until pod is running
kubectl port-forward micronaut-jib 8080 > /dev/null 2>&1 &
```
```shell
curl localhost:8080/hello
> Hello World
```

<!-- Run a "Hello World" @java @micronautfw app on #Kubernetes with #Jib in seconds -->
Give it a [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Run%20a%20%22Hello%20World%22%20%40java%20%40micronautfw%20app%20on%20%23Kubernetes%20with%20%23Jib%20in%20seconds&url=https://github.com/GoogleContainerTools/jib/tree/master/examples/micronaut&hashtags=docker,kubernetes)

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).
Learn [more about Micronaut](https://micronaut.io).
