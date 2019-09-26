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
1. Extensions to the filename (like id, digest, tar) will not be automatically appended
1. Existing files at the specified locations will be overwritten
1. Running `clean` will not delete output files created outside of the project's build directory

#### Maven (`pom.xml`)
```xml
<configuration>
  <outputFiles>
    <tar>/some/location.tar</tar>
    <digest>/some/other/location.digest</digest>
    <id>${project.build.directory}/id</id>
  </outputFiles>
</configuration>
```

#### Gradle (`build.gradle`)
```groovy
jib {
  outputFiles {
    tar = file("/some/location.tar")
    digest = file("/some/other/location.digest")
    id = file("$buildDir/id")
  }
}
```