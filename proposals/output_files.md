# Proposal: Image ID, Digest, and Tar File Location Configuration

Relevant issues: [#1561](https://github.com/GoogleContainerTools/jib/issues/1561), [#1921](https://github.com/GoogleContainerTools/jib/pull/1921)

## Motivation

Currently Jib hardcodes the location of the files it generates during the build, such as the image ID, digest,
and tar file (for tar builds). For some users, it would be useful to allow configuring the location of these
output files in a way that best fits their workflows.

## Current Configuration

Jib currently doesn't allow configuring these locations, and instead it uses hardcoded defaults:

- Image tar -> `${buildDir}/jib-image.tar`
- Image ID -> `${buildDir}/jib-image.id`
- Image digest -> `${buildDir}/jib-image.digest`

## Proposed Configuration

The proposal is to allow users to configure their build with the following rules:
1. Extensions to the filename (like id, digest, tar) will not be automatically appended.
1. Existing files at the specified locations will be overwritten if necessary.
1. Running `clean` will not delete output files created outside of the project's build directory.
1. The configuration will accept both absolute and relative paths. Relative paths are resolved in the build tool's default manner.

#### Maven (`pom.xml`)
```xml
<configuration>
  <outputPaths>
    <tar>/absolute/location.tar</tar>
    <digest>relative/path/digest</digest>
    <imageId>${project.build.directory}/id</imageId>
  </outputPaths>
</configuration>
```

#### Gradle (`build.gradle`)
```groovy
jib {
  outputPaths {
    tar = "/absolute/location.tar"
    digest = file("relative/path/digest")
    imageId = file("$buildDir/id")
  }
}
```

Corresponding system properties will also be added so the outputs can be set via commandline:
* `-Djib.outputPaths.tar`
* `-Djib.outputPaths.digest`
* `-Djib.outputPaths.imageId`