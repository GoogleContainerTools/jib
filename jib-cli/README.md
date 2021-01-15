# Jib CLI

<img src="https://img.shields.io/badge/status-experimental-orange">

`jib` is a command-line utility for building containers images from file system content. 
It serves as a demonstration of [Jib Core](https://github.com/GoogleContainerTools/jib/tree/master/jib-core),
a Java library for building containers without Docker.

This CLI tool is _experimental_ and its options and structure
are almost certain to change.

## Get the Jib CLI

### Build yourself

Use the `application` plugin's `installDist` task to create a runnable installation in
`build/install/jib`.  A zip and tar file are also created in `build/distributions`.
```sh
# build
$ ./gradlew jib-cli:installDist
# run
$ ./jib-cli/build/install/jib/bin/jib
```

<!-- TODO: ### Download a java application -->

<!-- TODO: ### Download an executable -->

## Usage

Currently only one command is supported: `build`

```
jib build --target gcr.io/my-project/my-image [options]
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

```yaml
# required apiVersion and kind, for compatibility over versions of the cli
apiVersion: jib/v1alpha1
kind: BuildFile

# full base image specification with detail for manifest lists or multiple architectures
from:
  image: "ubuntu"
  # set platforms for multi architecture builds, defaults to `linux/amd64`
  platforms:
    - architecture: "arm"
      os: "linux"
    - architecture: "amd64"
      os: "darwin"

# creation time sets the creation time of the container only
# can be: millis since epoch (ex: 1000) or an ISO 8601 creation time (ex: 2020-06-08T14:54:36+00:00)
creationTime: 2000

format: Docker # Docker or OCI

# container environment variables
environment:
  "KEY1": "v1"
  "KEY2": "v2"
  
# container labels
labels:
  "label1": "l1"
  "label2": "l2"
  
# specify volume mount points
volumes:
  - "/volume1"
  - "/volume2"

# specify exposed ports metadata (port-number/protocol)
exposedPorts:
  - "123/udp"
  - "456"      # default protocol is tcp
  - "789/tcp"

# the user to run the container (does not affect file permissions)
user: "customUser"

workingDirectory: "/home"

entrypoint:
  - "sh"
  - "script.sh"
cmd:
  - "--param"
  - "param"

# file layers of the container
layers: 
  properties:                        # file properties applied to all layers
    filePermissions: "123"           # octal file permissions, default is 644
    directoryPermissions: "123"      # octal directory permissions, default is 755
    user: "2"                        # default user is 0
    group: "4"                       # default group is 0
    timestamp: "1232"                # timestamp can be millis since epoch or ISO 8601 format, default is "Epoch + 1 second"
  entries:
    - name: "scripts"                # first layer
      properties:                    # file properties applied to only this layer
        filePermissions: "123"           
        # see above for full list of properties...
      files:                         # a list of copy directives constitute a single layer
        - src: "project/run.sh"      # a simple copy directive (inherits layer level file properties)
          dest: "/home/run.sh"       # all 'dest' specifications must be absolute paths on the container
        - src: "scripts"             # a second copy directive in the same layer
          dest: "/home/scripts"
          excludes:                  # exclude all files matching these patterns
            - "**/exclude.me"
            - "**/*.ignore"
          includes:                  # include only files matching these patterns
            - "**/include.me"            
          properties:                # file properties applied to only this copy directive
            filePermissions: "123"           
            # see above for full list of properties...  
    - name: "images"                 # second layer, inherits file properties from global
      files:
        - src: "images"
        - dest: "/images"            
```

#### Layers behavior
- Copy directives are bound by the following rules
  `src`: filetype determined by type on local disk
   - if `src` is directory, `dest` is always considered a directory, directory and contents will be copied over and renamed to `dest`
   - if `src` is file
     - if `dest` ends with `/` then it is considered a target directory, file will be copied into directory
     - if `dest` doesn't end with `/` then is is the target file location, `src` file will be copied and renamed to `dest`
- Permissions for a file or directory that appear in multiple layers will prioritize the *last* layer and copy directive the file appears in. In the following example, `file.txt` as seen on the running container will have filePermissions `234`.
    ```
    - name: layer1
      properties:
        filePermissions: "123"
      - src: file.txt
        dest: /file.txt
    - name: layer2
      properties:
        filePermissions: "234"
      - src: file.txt
        dest: /file.txt
     ```
- Parent directories that are not exiplicitly defined in a layer will the default properties in jib-core (permissions: 755, modification-time: epoch+1). In the following example, `/somewhere` on the container will have the directory permissions `755`, not `777` as some might expect.
    ```
    - name: layer
      properties:
        directoryPermissions: "777"
      - src: file.txt
        dest: /somewhere/file.txt
    ```
- `excludes` on a directory can lead to unintended inclusion of files in the directory, to exclude a directory *and* all its files
     ```
     excludes:
       - "**/exclude-dir"
       - "**/exclude-dir/**
     ```
     
#### Base image parameter inheritance
Some values defined in the base image may be preserved and propogated into the new container.

Parameters will append to base image value:
  - `volumes`
  - `exposedPorts`

Parameters that will append any new keys, and overwrite existing keys:
- `labels`
- `environment`

Parameters that will be overwritten:
- `user`
- `workingDirectory`
- `entrypoint`
- `cmd`
