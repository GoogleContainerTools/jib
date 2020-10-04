# Jib CLI Surface Specification

Specification for a command structures exposed to users of the jib-cli

## Usage
`jib COMMAND [OPTIONS]`

## Commands

The jib cli exposes the following top level commands, mirroring what is available in the plugins
```
build        build to registry
dockerBuild  build to docker daemon
buildTar     build to a tar file
```

## Options

### Required
```
-t  --target <image-ref>  a target image reference
```

### Optional
```
-b, --build-file <file>                    location of the build file (default <context>/jib.yaml)
-c, --context <dir>                        location of the build context (default .)
    --credHelper <registry>=<credHelper>   credential helper to use for a registry (repeatable)
    --docker-config <directory>            location of docker configuration
    --help                                 print usage
-p, --parameter <name>=<value>             templating parameters replace `${name}` with `value` in the build file (repeatable)
    --tag <tag1>[,<tag2>,...]              additional tags for target
    --verbosity <level>                    set logging verbosity (error, warn, lifecycle (default), info, debug)
-v, --version                              print version information and exit 
```

### Command specific options

#### `buildTar` Required

```
-o, --output-file <file>      output tar file
```

#### Environment Variables

#### Docker daemon specification

Information on communicating with the docker daemon is determined using the `DOCKER_*` environment variables.


## Alternative Styles

### Use uri style definitions

Instead of defining commands to specify the target image location, we could use the uri style definitions we use to locate source images (`tar://`, `docker://`, `registry://`) and the command line will appear to be more like

```
$ jib --target docker://imageRef
```
