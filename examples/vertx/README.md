# Containerize a [Eclipse Vert.x](https://vertx.io/) application with Jib

This is an example of how to easily build a Docker image for a [Eclipse Vert.x application](https://vertx.io/) with Jib.

```shell
./gradlew jibDockerBuild

docker run -d --rm -p 8080:8080 vertx-jib-example
```

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [more about Eclipse Vert.x](https://vertx.io).

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/vertx)](https://github.com/igrigorik/ga-beacon)
