# Jib CLI

<img src="https://img.shields.io/badge/status-experimental-orange">

`jib` is a command-line utility for building containers images from file system content. 
It serves as a demonstration of [Jib Core](https://github.com/GoogleContainerTools/jib/tree/master/jib-core),
a Java library for building containers without Docker.

This CLI tool is _experimental_ and its options and structure
are almost certain to change.

## Building

Use the `application` plugin's `installDist` task to create a runnable installation in
`build/install/jib`.  A zip and tar file are also created in `build/distributions`.
```sh
$ ../gradlew installDist
$ ./build/install/jib/bin/jib
Missing required subcommand
Usage: jib [-kv] [-C=helper]... COMMAND
A tool for creating container images.
...
```

## Examples

### Using nginx to serve a static website

The following example creates an nginx-based container to serve static content that is found in `path/to/website`.
The result is loaded to the local Docker daemon as `my-static-website`:

    $ ./build/install/jib/bin/jib build \
      --docker \
      nginx \
      my-static-website \
      --port 80 \
      --entrypoint "nginx,-g,daemon off;" \
      path/to/website:/usr/share/nginx/html
    $ docker run -it --rm -p 8080:80 my-static-website

### Containerizing a Java application

The following example uses _jib_ to containerize itself.  The image is pushed to a registry at `localhost:5000`:

    $ ../gradlew installDist
    $ ./build/install/jib/bin/jib --insecure build \
      --registry \
      gcr.io/distroless/java \
      localhost:5000/jib:latest \
      --entrypoint "java,-cp,/app/lib/*,com.google.cloud.tools.jib.cli.JibCli" \
      build/install/jib/lib,/app/lib
    $ docker run --rm localhost:5000/cram:latest

We need to use `--insecure` assuming the local registry does not support SSL.

(Note that we'd be better off using `jib-gradle-plugin` to create the container since it would create better layer strategy that negates the
need to use a fatjar.)
