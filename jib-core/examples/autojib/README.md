![experimental](https://img.shields.io/badge/stability-experimental-red.svg)

# AutoJib

**Note: This is just an example. Do NOT ready for production.**

Add AutoJib as a dependency and your application will containerize itself.

## Quickstart

Install AutoJib into your local Maven repository:

```bash
./gradlew install
```

In your Java application, add AutoJib as dependency:

`pom.xml`
```xml
<dependency>
  <groupId>com.google.cloud.tools.examples.autojib</groupId>
  <artifactId>autojib</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`build.gradle`
```groovy
dependencies {
  implementation 'com.google.cloud.tools.examples.autojib:autojib:0.1.0-SNAPSHOT'
}
```

Configure your application to run with the AutoJib main class:

`build.gradle`
```groovy
plugins {
  id 'application'
}

mainClassName = 'com.google.cloud.tools.examples.autojib.Main'
run {
  systemProperty 'autojibImage': 'YOUR IMAGE'
}
```

Run your application and it will containerize itself:

```bash
$ gradle run
``` 

If you run your application with arguments, those arguments will be used for the container execution as well.

## Demo

`demo/build.gradle` - play around with `run` task configuration.

Self-containerize:

```bash
$ IMAGE=<YOUR IMAGE>
$ (cd demo && ./gradlew run -DautojibImage=${IMAGE})
```

Use docker to run the built image:

```bash
docker run ${IMAGE}
```
