# Jib CLI Buildfile Specification

Specification for a YAML buildfile describing building a container image. This buildfile can be
used by the jib-cli to generate a container using jib-core.

```yaml
apiVersion: jib/v1alpha1
kind: Buildfile

# "FROM" with detail for manifest lists or multiple architectures
from:
  image: "adoptopenjdk:11-jre"
  # optional: if missing, then defaults to `linux/amd64`
  platforms:
    - architecture: "arm"
      os: "linux"
    - architecture: amd64
      os: darwin

# potentially simple form of "FROM" (based on ability to define schema)
from: "adoptopenjdk:11-jre"

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

layers:
  properties:
    # file properties applied to all layers
    filePermissions: "644"
    directoryPermissions: "755"
    user: "0"
    group: "0"
    timestamp: "0"
  entries:
    - name: "scripts and classes"
      # file properties only applied to this layer "scripts and classes"
      properties:
        filePermissions: "333"
        directoryPermissions: "777"
        user: "goose"
        group: "3"
        timestamp: "2020-06-03T19:31:50+00:00"
      files:
        - src: "target/scripts"
          dest: "/app/scripts"
          # file properties only applied to this copy directive
          properties:
            filePermissions: "777"
          # another copy for the same layer, with includes and excludes
        - src: "target/classes"
          dest: "/app/classes"
          excludes:
            - "**/goose.class"
            - "**/moose.class"
          includes:
            - "**/*.class"

      # another layer, only globally defined file permissions are applied here
    - name: "other"
      files:
        - src: "build/other"
          dest: "/app"

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

`layers.entries` are a list of `layer` directives

`layer` directives can be `archive` or `file` layers

a `archive` layer consists of 3 parts
* `name`: the name/description of the layer (metadata), it will also be used to populate the history entry for the layer
* `archive`: a tar file to include as a layer
* `mediatype`: the mediatype of archive

a `file` layer consists of 3 parts
* `name`: the name of the layer (metadata)
* `properties`: on disk metadata for all files in the layer
* `files`: a list of *copy directies* (see below)

`files` are a list of *copy directives* that consist of 2 required and 3 optional parts
* `dest` *required*: an absolute path to copy the files to on the container
* `src` *required*: a file, or directory on disk to copy from
* `includes`: only includes the patterns matched in this parameter
* `exludes`: excludes all files (higher prescendence than `includes`) matched in this parameter
* `properties`: FilesProperties to represent on disk metadata for all files in this copy directive

### File Copy Behavior

`src`: filetype determined by type on local disk
`dest`: 
 - if `src` is directory, dest is always considered a directory
 - if `src` is file
   - if `dest` ends with `/` then it is considered a target directory, file will be copied into directory
   - if `dest` doesn't end with `/` then is is the target file location, `src` file will be copied and renamed to `dest`

### FileProperties

A list of properties that can be user specified for each file in a file layer
* filePermissions: An octal representing permissions for files
* directoryPermissions: An octal representing permissions for directories
* user: The ownership user property
* group: The ownership group property
* timestamp: millis since epoch or iso8601 creation time

File properties are available at 3 levels
* Global (`layers.properties`): applies to all layers
* Layer (`layers.<entry>.properties`): applies to a single layer
* Copy (`layers.<entry>.<copy>.properties`): applies to a single copy directive

Each property (`filePermissions`, `directoryPermissions`, etc) can be defined at any level and are resolved in the follow order
- All properties in `Copy` are applied first
- Any properties not in `Copy` are applied from `Layer`
- Any properties not in `Copy` or `Layer` are applied from `Global`
- Any properties not defined anywhere use jib system defaults.

### Base image value inheritance
The value(s) defined in the base image are preserved and propagated into the
config of the new container.

The behavior of the buildfile values post-inheritance must be considered

These parameters will allow appending to the base image value:
- `volumes`
- `exposedPorts`

These parameters will allow appending for new keys, and overwriting for existing keys:
- `labels`
- `environment`

These parameters will be overwritten:
- `user`
- `workingDirectory`
- `entrypoint`
- `cmd`

If we start getting specific user requests to control this, we can explore
inheritance control in the future.

## Extended features (not included in first pass)

### Other time options
* `actual`: use timestamp from file on disk
* `current`: use time of build

### Platform

#### Handle sub categories for platforms
Users should be able to further specify details for selecting platforms such as `os.version`, `os.features`, `variant` and `features`
```
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
```

#### Special platform specific layers
Layer entries can contain platform specific filters that are only applied for builds matching that platform

```yaml
entries:
  # arm things will only apply to builds for arm achitectures
  - name: "arm things"
    platform:
      architecture: arm
    ...
```

#### Mark values for erasure
If a user does not want to inherit specific values from the base image a mechanism for *erasing* them could be useful.
