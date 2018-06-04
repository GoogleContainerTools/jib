# Proposal: Align `jib-maven-plugin` configuration with `jib-gradle-plugin`

Implemented in: **v0.9.0**

## Motivation

`jib-gradle-plugin` introduced a different configuration schema from `jib-maven-plugin`. This schema has many benefits over the original `jib-maven-plugin` configuration. Therefore, to maintain consistency between the two plugins, the `jib-maven-plugin` configuration should be updated to be more similar to the `jib-gradle-plugin` configuration.

## Goals

- Have `jib-maven-plugin` configuration be as similar to `jib-gradle-plugin` configuration as possible

## Non-Goals

- Maintain compatibility with older versions of `jib-maven-plugin` (we are still in alpha)

## Current Configuration Schema

The current `jib-maven-plugin` configuration looks like:

```xml
<configuration>
  <from>openjdk:alpine</from>
  <registry>localhost:5000</registry>
  <repository>my-image</repository>
  <tag>built-with-jib</tag>
  <credHelpers>
    <credHelper>osxkeychain</credHelper>
  </credHelpers>
  <jvmFlags>
    <jvmFlag>-Xms512m</jvmFlag>
    <jvmFlag>-Xdebug</jvmFlag>
    <jvmFlag>-Xmy:flag=jib-rules</jvmFlag>
  </jvmFlags>
  <mainClass>mypackage.MyApp</mainClass>
  <enableReproducibleBuilds>true</enableReproducibleBuilds>
  <imageFormat>OCI</imageFormat>
</configuration>
```

And the current `jib-gradle-plugin` configuration looks like:

```groovy
jib {
  from {
    image = 'openjdk:alpine'
  }
  to {
    image = 'localhost:5000/my-image/built-with-jib'
    credHelper = 'osxkeychain'
  }
  jvmFlags = ['-Xms512m', '-Xdebug', '-Xmy:flag=jib-rules']
  mainClass = 'mypackage.MyApp'
  reproducible = true
  format = 'OCI'
}
```

## Desired Configuration Schema

Therefore, the desired `jib-maven-plugin` configuration should look like:

```xml
<configuration>
  <from>
    <image>openjdk:alpine</image>
  </from>
  <to>
    <image>gcr.io/my-gcp-project/my-app</image>
    <credHelper>gcr</credHelper>
  </to>
  <jvmFlags>
    <jvmFlag>-Xmy:flag=jib-rules</jvmFlag>
  </jvmFlags>
  <mainClass>mypackage.MyApp</mainClass>
  <reproducible>true</reproducible>
  <format>OCI</format>
</configuration>
```

The key changes are:

- Removes `registry`, `repository`, and `tag` - these are now defined as one field `to.image`.
- Adds an image configuration object that takes:
  - `to` - the image reference, and
  - `credHelper` - the credential helper for that image
- The image configuration object configures both the base image (`from`) and the target image (`to`).
- `enableReproducibleBuilds` is renamed to `reproducible`.
- `imageFormat` is renamed to `format`.

## Implementation Details

1. Change `BuildConfiguration` to individual `credHelper`s for the base and target images, not as a list.
2. Change the `BuildImageMojo` (and `DockerContextMojo`) schema.
  - The only required field now is `to.image`. 