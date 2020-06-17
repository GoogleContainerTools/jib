# Jib CLI Buildfile Specification

Specification for a YAML buildfile describing building a container image. This buildfile can be
used by the jib-cli to generate a container. It is translated directly into a buildplan and
passed to the builder.

```yaml
# "FROM" with option to use os/architecture for manifest lists
from: "gcr.io/distroless/java:8"

# "FROM" with detail for manifest lists or multiple architectures
from:
  image: "gcr.io/distroless/java:8"
  platforms:
    - architecture: "arm"
      os: "linux"
      os.version: "a"
      os.features:
        - "b1"
        - "b2"
      variant: "c"
      features:
        - "d1"
        - "d2"
    - architecutre: amd64
      os: darwin

creationTime: 0 # millis since epoch or iso8601 creation time
format: Docker # Docker or OCI

environment:
  "MY_KEY1": "value"
  "MY_KEY2": "value"
labels:
  "com.example.owner": "person"
  "com.example.mode": "dev"
volumes:
  - "/myvolume"
  - "/youvolume"

exposedPorts:
  - "123/udp"
  - "456"
  - "789/tcp"

user: root
workingDirectory: "/somewhere"
entrypoint:
  - "java"
  - "-jar"
cmd:
  - "myjar.jar"

# global file properties applied to all layers
layerProperties:
  filePermissions: "644"
  directoryPermissions: "755"
  user: "0"
  group: "0"
  timestamp: "0"
layers:
  - name: "scripts and classes"
    # file properties only applied to this layer "scripts and classes"
    properties:
      filePermissions: "333"
      directoryPermissions: "777"
      user: "goose"
      group: "3"
      timestamp: "2020-06-03T19:31:50+00:00"
    files:
      - from: "target/scripts"
        to: "/app/scripts"
        # file properties only applied to this copy directive
        properties:
          filePermissions: "777"
        # another copy for the same layer, with includes and excludes
      - from: "target/classes"
        to: "/app/classes"
        excludes:
          - "**/goose.class"
          - "**/moose.class"
        includes:
          - "**/*.class"

    # another layer, only globally defined file permissions are applied here
  - name: "other"
    files:
      - from: "build/other"
        to: "/app"

    # a archive layer using a tar, default mediaType
  - name: "some tar layer"
    archive: "build/generated.tar"

    # a foreign layer using the optional mediatype for archive layers
  - name: "some foreign layer"
    mediaType: "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip"
    archive: "https://somewhere.com/layer"
    # should we include size and digest here? I guess we can always ad tings
```

## Layers

`layers` are a list of `layer` directives

`layer` directives can be `archive` or `file` layers

a `archive` layer consists of 3 parts
* `name`: the name/description of the layer (metadata)
* `archive`: a tar file to include as a layer
* `mediatype`: the mediatype of archive

a `file` layer consists of 3 parts
* `name`: the name of the layer (metadata)
* `properties`: on disk metadata for all files in the layer
* `files`: a list of *copy directies* (see below)

`files` are a list of *copy directives* that consist of 2 required and 3 optional parts
* `to` *required*: an absolute path to copy the files to on the container
* `from` *required*: a file, or directory on disk to copy from
* `includes`: only includes the patterns matched in this parameter
* `exludes`: excludes all files (higher prescendence than `includes`) matched in this parameter
* `properties`: FilesProperties to represent on disk metadata for all files in this copy directive

### FileProperties

A list of properties that can be user specified for each file in a file layer
* filePermissions: An octal representing permissions for files
* directoryPermissions: An octal representing permissions for directories
* user: The ownership user property
* group: The ownership group property
* timestamp: millis since epoch or iso8601 creation time

File properties are available at 3 levels
* Global (`layerProperties`): applies to all layers
* Layer (`properties`): applies to a single layer
* Copy (`properties`): applies to a single copy directive

Each property (`filePermissions`, `directoryPermissions`, etc) can be defined at any level and are resolved in the follow order
- All properties in `Copy` are applied first
- Any properties not in `Copy` are applied from `Layer`
- Any properties not in `Copy` or `Layer` are applied from `Global`
- Any properties not defined anywhere use jib system defaults.


## Extented features (not included in this build)

### Other time options
* `actual`: use timestamp from file on disk
* `now`: use time of build

### Configurable base image value inheritance
Jib has some default behavior on inheritance of config parameters from the base image.
Perhaps this needs to be configurable

(this is just an exploration, open to some ideas here)

For all values in config of the base image, allow inheritance.
```
baseImage:
  from: "gcr.io/birds/goose"
  inherit:
    environment: true
    labels: true
    volumes: false
    exposedPorts: false
    user: true
    workingDirectory: false
    entrypoint: false
    cmd: true
```
If a value is marked inherit: true, then the value(s) defined in the base image are
preserved and propogated into the config of the new container.

The behavior of the buildfile values post-inheritance must be considered

These parameters will allow appending to the base image value:
- `environment`
- `volumes`
- `labels`
- `exposedPorts`

The paratmeters will be overwritten:
- `user`
- `workingDirectory`
- `entrypoint`
- `cmd`
