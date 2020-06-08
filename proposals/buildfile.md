# Jib CLI Buildfile Specification

Specification for a buildfile describing building a container image. This buildfile can be
used by the jib-cli to generate a container. It is translated directly into a buildplan and
passed to the builder.

```
# "FROM" with option to use os/architecture for manifest lists
baseImage: "gcr.io/distroless/java:8"
osHint: linux
architectureHint: amd64

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
fileProperties:
  filePermissions: "644"
  directoryPermissions: "755"
  user: "0"
  group: "0"
  timestamp: "0"
layers:
  - name: "scripts and classes"
    # file properties only applied to layer this layer "scripts and classes"
    fileProperties:
      filePermissions: "333"
      directoryPermissions: "777"
      user: "goose"
      group: "3"
      timestamp: "2020-06-03T19:31:50+00:00"
    files:
      - from: "target/scripts"
        to: "/app/scripts"
        # file properties only applied to this copy directive
        fileProperties:
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
```

## Layers

`layers` are a list of layer directives, each directive consisting of 3 parts
* `name`: the name of the layer (metadata)
* `filesProperites`: on disk metadata for all files in the layer
* `files`: a list of copy directies

`files` are a list of copy directives that consist of 2 required and 3 optional parts
* `to` *required*: an absolute path to copy the files to on the container
* `from` *required*: a file, or directory on disk to copy from
* `includes`: only includes the patterns matched in this parameter
* `exludes`: excludes all files (higher prescendence than `includes`) matched in this parameter
* `fileproperties`: on disk metadata for all files in this copy directive

### FileProperties

File properties are available at 3 levels
* Global: applies to all layers
* Layer: applies to a single layer
* Copy: applies to a single copy directive

Properties can be defined at any level and are applied in a cascading fashion.
- All properties in `Copy` are applied first
- Any property not in `Copy` are applied from `Layer`
- Any property not in `Copy` or `Layer` are applied from `Global`
- Any property not defined anywhere use jib system defaults.


## Extented features (not included in this build)

### Other time options
* `actual`: use timestamp from file on disk
* `now`: use time of build

### Templating
Allow passing of values into build to be replaced in buildfile
```
baseImage: ${baseImage}
```
```
$ jib build -PbaseImage=gcr.io/distroless/java
```

