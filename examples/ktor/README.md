# Containerize a [Ktor] application with Jib

This is an example of how to easily build a Docker image for a [Ktor] application with Jib.

```shell
./gradlew jibDockerBuild

docker run --rm -p 8080:8080 ktor-jib-example:1
```

The application can also be ran outside of Jib's build process via `./gradlew run`

## Defined environment variables

A few variables have been added in the code-base to show case some of the unique features of Ktor.

- `KTOR_APP_ID` - The name of the application in the logs
    - Type: String
- `KTOR_METRICS_ENABLED` - Exposes JMX Metrics through the application port (`8080`)
    - Type: Boolean (default: `false`)
- `KTOR_ROUTE_TRACING` - Enables verbose logging of route matches for debugging complex/nested routing tables
    - Type: Boolean

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [Ktor].

  [Ktor]: https://ktor.io
