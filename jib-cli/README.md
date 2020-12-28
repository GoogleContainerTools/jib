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

## Usage

Currently only one command is supported: `build`

```
jib build -t gcr.io/my-project/my-image [options]
```

#### Options
```
      [@<filename>...]      One or more argument files containing options.
      --additional-tags=<tag>[,<tag>...]
                            Additional tags for target image
      --allow-insecure-registries
                            Allow jib to communicate with registries over http
                              (insecure)
  -b, --build-file=<build-file>
                            The path to the build file (ex: path/to/other-jib.
                              yaml)
      --base-image-cache=<cache-directory>
                            A path to a base image cache
  -c, --context=<project-root>
                            The context root directory of the build (ex:
                              path/to/my/build/things)
      --console=<type>      set console output type, candidates: auto, rich,
                              plain, default: auto
      --credential-helper=<credential-helper>
                            credential helper for communicating with both
                              target and base image registries, either a path
                              to the helper, or a suffix for an executable
                              named `docker-credential-<suffix>`
      --from-credential-helper=<credential-helper>
                            credential helper for communicating with base image
                              registry, either a path to the helper, or a
                              suffix for an executable named
                              `docker-credential-<suffix>`
      --from-password[=<password>]
                            password for communicating with base image registry
      --from-username=<username>
                            username for communicating with base image registry
      --name=<image-reference>
                            The image reference to inject into the tar
                              configuration (required when using --target tar:
                              //...)
  -p, --parameter=<name>=<value>
                            templating parameter to inject into build file,
                              replace ${<name>} with <value> (repeatable)
      --password[=<password>]
                            password for communicating with both target and
                              base image registries
      --project-cache=<cache-directory>
                            A path to the project cache
      --send-credentials-over-http
                            Allow jib to send credentials over http (very
                              insecure)
  -t, --target=<target-image>
                            The destination image reference or jib style url,
                            examples:
                             gcr.io/project/image,
                             registry://image-ref,
                             docker://image,
                             tar://path
      --to-credential-helper=<credential-helper>
                            credential helper for communicating with target
                              registry, either a path to the helper, or a
                              suffix for an executable named
                              `docker-credential-<suffix>`
      --to-password[=<password>]
                            password for communicating with target image
                              registry
      --to-username=<username>
                            username for communicating with target image
                              registry
      --username=<username> username for communicating with both target and
                              base image registries
      --verbosity=<level>   set logging verbosity, candidates: quiet, error,
                              warn, lifecycle, info, debug, default: lifecycle

```

## Build File

The CLI uses a build file to define the container being built. The default is a file named `jib.yaml` in the project root.

### Annotated `jib.yaml`

Contains all possible options of the `jib.yaml`

```
# required apiVersion and kind, no real value yet, should help with compatibility over version of the cli
apiVersion: jib/v1alpha1
kind: BuildFile

# full base image specification with detail for manifest lists or multiple architectures
from:
  image: "ubuntu"
  # optional: if missing, then defaults to `linux/amd64`
  platforms:
    - architecture: "arm"
      os: "linux"
    - architecture: "amd64"
      os: "darwin"

creationTime: 2000 # millis since epoch or an ISO 8601 creation time
format: Docker # Docker (default) or OCI

environment:
  "KEY1": "v1"
  "KEY2": "v2"
labels:
  "label1": "l1"
  "label2": "l2"
volumes:
  - "/volume1"
  - "/volume2"

exposedPorts:
  - "123/udp"
  - "456"
  - "789/tcp"

user: "customUser"
workingDirectory: "/home"
entrypoint:
  - "sh"
  - "script.sh"
cmd:
  - "--param"
  - "param"

layers:
  entries:
    - name: "scripts"
      files:
        - src: "project/script.sh"
          dest: "/home/script.sh"

```
