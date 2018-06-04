# Proposal: Build to Docker Daemon

Implemented in: **v0.9.0**

## Motivation

Currently, Jib builds and pushes container images to a Docker registry without the need for a Docker daemon. However, for development use cases where the developer does not have a registry set up but do have a Docker daemon available, they may wish to build to the Docker daemon directly.

## Goals

* Build to local Docker daemon
* Build to [minikube](https://github.com/kubernetes/minikube) (remote) Docker daemon
* Minimize extra configuration

## Constraints

* Building to local Docker daemon should be an extra feature, not the default

## Intended Workflow

Building to a Docker daemon should be as simple as calling a new task/goal (`jib:buildDocker` for Maven and `jibBuildDocker` for Gradle).

## Implementation

There are three main parts to implementing this proposal:

1. Build an image tarball stream, with the format (see [tarexport/load.go](https://github.com/moby/moby/blob/master/image/tarexport/load.go) for full details):
  - Each layer as a GZipped tarball named with its SHA256 digest.
  - A `config.json` as the container config, containing:
    - `OS`: `linux`
    - `RootFS.DiffIDS`: in-order [diff IDs](https://github.com/opencontainers/image-spec/blob/master/config.md#layer-diffid) for all the uncompressed layers
  - A `manifest.json` containing the following fields:
    - `Config`: The filename of the container config JSON
    - `Layers`: in-order list of filenames for each layer
2. Send the image tarball stream to the Docker daemon using [`docker load`](https://docs.docker.com/engine/reference/commandline/load/) CLI command.

Note that currently, build, cache, and push are all in `BuildImageSteps`. In order to implement parts 2 and 3, these stages would need be modularized so that push can be replaced with build image tarball stream and send to Docker daemon.

If in the future, we would like to not use the `docker` CLI, we would need to:

1. Find the Docker daemon.
  - This should use the same Docker daemon that the `docker` CLI would.
    - The default host is `unix:///var/run/docker.sock` or `tcp://127.0.0.1:2375`.
  - Remote Docker daemon can be configured with environment variables, such as those output by `minikube docker-env`. These include:
    - `DOCKER_TLS_VERIFY`
    - `DOCKER_HOST`
    - `DOCKER_CERT_PATH`
    - `DOCKER_API_VERSION`
2. Send the image tarball stream to the Docker daemon using the [Docker Engine API](https://docs.docker.com/engine/api/v1.37/#operation/ImageLoad). This can be done manually with HTTP requests, or by using a Docker-Java client like [docker-java](https://github.com/docker-java/docker-java).
