# Jib CLI

<img src="https://img.shields.io/badge/status-preview-orange">

[![Chocolatey](https://img.shields.io/chocolatey/v/jib.svg)](https://chocolatey.org/packages/jib)
[![Chocolatey](https://img.shields.io/chocolatey/dt/jib.svg)](https://chocolatey.org/packages/jib)

`jib` is a general-purpose command-line utility for building Docker or [OCI](https://github.com/opencontainers/image-spec) container images from file system content as well as JAR files. Jib CLI builds containers [fast and reproducibly without Docker](https://github.com/GoogleContainerTools/jib#goals) like [other Jib tools](https://github.com/GoogleContainerTools/jib#what-is-jib).

```sh
# docker not required
$ docker
-bash: docker: command not found
# build and upload an image
$ jib build --target=my-registry.example.com/built-by-jib
```

Additionally, Jib CLI can directly build an optimized image for JAR files (including Spring Boot fat JAR).
```sh
$ jib jar --target=my-registry.example.com/jar-app myapp.jar
```

The CLI tool is powered by [Jib Core](https://github.com/GoogleContainerTools/jib/tree/master/jib-core), a Java library for building containers without Docker.

## Table of Contents
* [Get the Jib CLI](#get-the-jib-cli)
  * [Download a Java Application](#download-a-java-application)
  * [Install on Windows with `choco`](#Windows-install-with-choco)
  * [Build Yourself from Source (for Advanced Users)](#build-yourself-from-source-for-advanced-users)
* [Supported Commands](#supported-commands)
* [Build Command](#build-command)
  * [Quickstart](#quickstart)
  * [Options](#options)
* [Jar Command](#jar-command)
  * [Quickstart](#quickstart-1)
  * [Options](#options-1)
* [Common Jib CLI Options](#common-jib-cli-options)
  * [Auth/Security](#authsecurity)
  * [Info Params](#info-params)
  * [Debugging Params](#debugging-params)
* [Global Jib Configuration](#global-jib-configuration)
* [References](#references)
  * [Fully Annotated Build File (`jib.yaml`)](#fully-annotated-build-file-jibyaml)

## Get the Jib CLI

Most users should download a ZIP archive (Java application). We are working on releasing a native executable binary using GraalVM. (Help wanted!)

### Download a Java Application

A JRE is required to run this Jib CLI distribution.

Find the [latest jib-cli 0.5.0 release](https://github.com/GoogleContainerTools/jib/releases/tag/v0.5.0-cli) on the [Releases page](https://github.com/GoogleContainerTools/jib/releases), download `jib-jre-<version>.zip`, and unzip it. The zip file contains the `jib` (`jib.bat` for Windows) script at `jib/bin/`. Optionally, add the binary directory to your `$PATH` so that you can call `jib` from anywhere.

### Windows install with choco

On Windows, you can use the [`choco`](https://community.chocolatey.org/packages/jib) commmand to install, upgrade or uninstall `Jib`.

To install Jib CLI, run the following command from the command line or from PowerShell:

```
choco install jib
```

To upgrade Jib CLI, run the following command from the command line or from PowerShell:

```
choco upgrade jib
```

To uninstall Jib CLI, run the following command from the command line or from PowerShell:

```
choco uninstall jib
```

### Build Yourself from Source (for Advanced Users)

Use the `application` plugin's `installDist` task to create a runnable installation in
`build/install/jib`.  A zip and tar file are also created in `build/distributions`.
```sh
# build
$ ./gradlew jib-cli:installDist
# run
$ ./jib-cli/build/install/jib/bin/jib

```
## Supported Commands

The Jib CLI supports two commands:
 1. `build` - containerizes using a [build file](#fully-annotated-build-file-jibyaml).
 2. `jar` - containerizes JAR files.

## Build Command
This command follows the following pattern:
```
jib build --target <image name> [options]
```
### Quickstart
1. Create a hello world script (`script.sh`) containing:
    ```sh
    #!/bin/sh
    echo "Hello World"
    ```
2. Create a [build file](#fully-annotated-build-file-jibyaml). The default is a file named `jib.yaml` in the project root.
    ```yaml
    apiVersion: jib/v1alpha1
    kind: BuildFile
    
    from:
      image: ubuntu
    
    entrypoint: ["/script.sh"]
    
    layers:
      entries:
        - name: scripts
          files:
            - properties:
                filePermissions: 755
              src: script.sh
              dest: /script.sh

    ```

3. Build to docker daemon
   ```
    $ jib build --target=docker://jib-cli-quickstart
   ```
4. Run the container
   ```
    $ docker run jib-cli-quickstart
    Hello World
   ```

### Options
Optional flags for the `build` command:

Option | Description
---     | ---
`-b, --build-file` |  The path to the build file (ex: path/to/other-jib.yaml)
`-c, --context`    |  The context root directory of the build (ex: path/to/my/build/things)
`-p, --parameter`  |  Templating parameter to inject into build file, replace ${<name>} with <value> (repeatable)


## Jar Command
This command follows the following pattern:
```
jib jar --target <image name> path/to/myapp.jar [options]
```
### Quickstart
1. Have your JAR (thin or fat) ready. We will be using the [Spring Petclinic](https://projects.spring.io/spring-petclinic/) JAR in this Quickstart.
   ```
    $ git clone https://github.com/spring-projects/spring-petclinic.git
    $ cd spring-petclinic
    $ ./mvnw package
   ```
2. Containerize your JAR using the `jar` command. In the default mode (exploded), the entrypoint will be set to `java -cp /app/dependencies/:/app/explodedJar/ HelloWorld`
   ```
    $ jib jar --target=docker://cli-jar-quickstart target/spring-petclinic-*.jar
   ```

3. Run the image and open your browser at http://localhost:8080
   ```
    $ docker run -p 8080:8080 cli-jar-quickstart
   ```
   
### Options
Optional flags for the `jar` command:

Option | Description
---       | ---
`--creation-time` | The creation time of the container in milliseconds since epoch or iso8601 format. Overrides the default (1970-01-01T00:00:00Z)
`--entrypoint`    | Entrypoint for container. Overrides the default entrypoint, example: `--entrypoint='custom entrypoint'`
`--environment-variables`  | Environment variables to write into container, example: `--environment-variables env1=env_value1, env2=env_value2`.
`--expose`        | Ports to expose on container, example: `--expose=5000,7/udp`.
`--from`          | The base image to use.
`--image-format`  | Format of container, candidates: Docker, OCI, default: Docker.
`--jvm-flags`     | JVM arguments, example: `--jvm-flags=-Dmy.property=value,-Xshare:off`
`--labels`        | Labels to write into container metadata, example: `--labels=label1=value1,label2=value2`.
`--mode`          | The jar processing mode, candidates: exploded, packaged, default: exploded
`--program-args`  | Program arguments for container entrypoint.
`-u, --user`      | The user to run the container as, example: `--user=myuser:mygroup`.
`--volumes`       | Directories on container to hold extra volumes, example: `--volumes=/var/log,/var/log2`.


## Common Jib CLI Options
The options can either be specified in the command line or defined in a configuration file:
```
[@<filename>...]      One or more argument files containing options.
```
### Auth/Security
```
    --allow-insecure-registries            Allow jib to send credentials over http (insecure)
    --send-credentials-over-http           Allow jib to send credentials over http (very insecure)
```

### Registry Credentials
Credentials can be specified using credential helpers or username + password. The following options are available:

```
    --credential-helper <credHelper>      credential helper for communicating with both target and base image registries, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`
    --to-crendential-helper <credHelper>  credential helper for communicating with target registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>
    --from-credential-helper <credHelper> credential helper for communicating with base image registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`

    --username <username>                  username for communicating with both target and base image registries
    --password <password>                  password for communicating with both target and base image registries
    --to-username <username>               username for communicating with target image registry
    --to-password <password>               password for communicating with target image registry
    --from-username <username>             username for communicating with base image registry
    --from-password <password>             password for communicating with base image registry
```
*Note* - Combinations of `credential-helper`, `username` and `password` flags come with restrictions and can be use only in the following ways:

Only Credential Helper
1. `--credential-helper`
2. `--to-credential-helper`
3. `--from-credential-helper`
4. `--to-credential-helper`, `--from-credential-helper`

Only Username and Password
1. `--username`, `--password`
2. `--to-username`, `--to-password`
3. `--from-username`, `--from-password`
4. `--to-username`, `--to-password`, `--from-username`, `--from-password`

Mixed Mode
1. `--to-credential-helper`, `--from-username`, `--from-password`
2. `--from-credential-helper`, `--to-username`, `--to-password`

### Info Params
```
    --help                  print usage and exit
    --console <type>        set console output type, candidates: auto, rich, plain, default: auto
    --verbosity <level>     set logging verbosity, candidates: quiet, error, warn, lifecycle, info, debug, default: lifecycle
-v, --version               Jib CLI version information
```

### Debugging Params
```
    --stacktrace            print stacktrace on error (for debugging issues in the jib-cli)
    --http-trace            enable http tracing at level=config, output=console
    --serialize             run jib in serialized mode
```

## Global Jib Configuration

Some options can be set in the global Jib configuration file. The file is at the following locations on each platform:

* *Linux: `[config root]/google-cloud-tools-java/jib/config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`$HOME/.config/` if not set)*
* *Mac: `[config root]/Google/Jib/config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`$HOME/Library/Preferences/Config/` if not set)*
* *Windows: `[config root]\Google\Jib\Config\config.json`, where `[config root]` is `$XDG_CONFIG_HOME` (`%LOCALAPPDATA%` if not set)*

### Properties 

* `disableUpdateCheck`: when set to true, disables the periodic up-to-date version check.
* `registryMirrors`: a list of mirror settings for each base image registry. In the following example, if the base image configured in Jib is for a Docker Hub image, then `mirror.gcr.io`, `localhost:5000`, and the Docker Hub (`registry-1.docker.io`) are tried in order until Jib can successfuly pull a base image.

```json
{
  "disableUpdateCheck": false,
  "registryMirrors": [
    {
      "registry": "registry-1.docker.io",
      "mirrors": ["mirror.gcr.io", "localhost:5000"]
    },
    {
      "registry": "quay.io",
      "mirrors": ["private-mirror.test.com"]
    }
  ]
}
```

## References

### Fully Annotated Build File (`jib.yaml`)

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

#### Layers Behavior
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
     
#### Base Image Parameter Inheritance
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
