# getting-started Project + Quarkus jib extension

This project uses Quarkus, the Supersonic Subatomic Java Framework.
Quarkus Jib documentation: https://quarkus.io/guides/container-image .

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

Maven 

```shell script
./mvnw compile quarkus:dev
```

Gradle
```shell script
./gradlew quarkusDev
```


> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

Maven
```shell script
./mvnw package
```

Gradle
```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

Maven
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

Gradle
```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 

Maven
```shell script
./mvnw package -Pnative
```

Gradle
```shell script
./gradlew build -Dquarkus.package.type=native
```


Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

Maven
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

Gradle
```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/getting-started-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- RESTEasy Reactive ([guide](https://quarkus.io/guides/resteasy-reactive)): A JAX-RS implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
