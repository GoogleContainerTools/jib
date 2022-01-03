# Containerize a [Dropwizard](https://dropwizard.io) application with Jib

## How to start the Dropwizard application

1. Run `./mvnw clean package` to build your container
1. Start the application
    - **With Docker**: `docker run --rm -p 8080:8080 dropwizard-jib-example:1`
    - **Without Docker**: `./mvnw exec:java`
1. Check that your application is running at http://localhost:8080

## Health Check

See your application's health at http://localhost:8080/admin/healthcheck

## Extras

FreeMaker templating is a setup for [`dropwizard.yml`](src/main/resources/dropwizard.yml) through [`tkrille/dropwizard-template-config`](https://github.com/tkrille/dropwizard-template-config); this allows one to heavily customize the properties file via the container environment with FTL conditional checks and for loops, for example.

## How this example was generated

Starter Maven template generated with [`dropwizard-archetypes`](https://github.com/dropwizard/dropwizard/tree/master/dropwizard-archetypes)

```sh
mvn archetype:generate \
  -DarchetypeGroupId=io.dropwizard.archetypes \
  -DarchetypeArtifactId=example \
  -DarchetypeVersion=[REPLACE ME WITH A VALID DROPWIZARD VERSION]
```

Ref. [Dropwizard - Getting Started, Setting up With Maven](https://www.dropwizard.io/1.3.5/docs/getting-started.html#setting-up-using-maven)

The remainder of the archetype code was filled-in following the above guide.

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [more about Dropwizard](https://dropwizard.io).

## Build and run on Google Cloud

[![Run on Google Cloud](https://deploy.cloud.run/button.svg)](https://deploy.cloud.run?git_repo=https://github.com/GoogleContainerTools/jib.git&dir=examples/dropwizard)
