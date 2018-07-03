# Proposal: Build Image Tarball

## Motivation

Currently, Jib can build a container image to either a registry or a Docker daemon. Recently, there
have been requests for the ability to build an image tarball directly to the filesystem so that the
user may load it into a Docker daemon via `docker load`, either manually or via a build system.

## Goals

* Build the image and output as a tarball
* Minimize extra configuration

## Constraints

* The tarball will be saved to the project's build output directory with the name `jib-image.tar`

## Intended Workflow

The user will be able to output a `docker load`able image tarball by running `gradle jibBuildTar`
for Gradle or `mvn jib:buildTar` for Maven.

## Implementation

The "build tarball" task is almost identical to the "build to docker daemon" task, except for the
final step, which writes the final tarball to a file instead of piping it to a `docker load`
command. To avoid duplicate code, a boolean parameter can be used in the steps runners to determine
whether to build to a Docker daemon or build to a tarball.

The following changes will be made to the code:
1. Add a private output path configuration parameter to `BuildConfiguration`
2. Rename:
   - `BuildTarballAndLoadDockerStep` to `BuildTarballStep`
   - `runBuildTarballAndLoadDockerStep` to `runBuildTarballStep`
   - `waitOnBuildTarballAndLoadDockerStep` to `waitOnBuildTarballStep`
3. Add a boolean parameter `doDockerLoad` to `runBuildTarballStep` and `BuildTarballStep`'s
constructor
   - Alternatively, parameterize an action to more explicitly define the behavior
4. Check the value of `doDockerLoad` in `BuildTarballStep#afterPushBaseImageLayerFuturesFuture()`
   - If true, run the existing `new DockerClient().load(...)`
   - If false, write the tarball blob to a file at the output location
5. Add `BuildSteps#forBuildToTarball()`, which would pass in the required messages/boolean parameter
6. Add a new task and mojo that would call `BuildSteps.forBuildToTarball()`