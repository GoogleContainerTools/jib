# Jib CLI Surface Specification

Specification for a command structures exposed to users of the jib-cli

## Usage
`jib command [OPTIONS]`

## Options

### Required
```
-t  --target <image-ref>  a target image reference
```

### Optional
```
-b  --build-file <file>                    location of the build file (default ./jib.yaml)
    --credHelper <registry>=<credHelper>   credential helper to use for a registry (repeatable)
    --docker-config <directory>            location of docker configuration
    --help                                 print usage
-P <name>=<value>                          templating parameters replace `${name}` with `value` in the build file
    --tag <tag1>[,<tag2>,...]              additional tags for target
    --verbosity <level>                    set logging verbosity (error, warn, lifecycle (default), info, debug)
-v  --version                              print version information and exit 
```

## Commands

The jib cli exposes the following top level commands, mirroring what is available in the plugins
```
build        build to registry
dockerBuild  build to docker daemon
buildTar     build to a tar file
```

### `build`

Will build to a registry and repository as defined in the `--target`

### `dockerBuild`

Will build to a docker daemon and should reflect the same daemon a docker cli will connect to. Can be configured by various `DOCKER_*` environment variables.

### `buildTar`

Will build to a tar file on the local file system.

#### Required
```
-o  --output-file <file>      output tar file
```


## Alternative Styles

### Use uri style definitions

Instead of defining commands to specify the target image location, we could use the uri style definitions we use to locate source images (`tar://`, `docker://`, `registry://`) and the command line will appear to be more like

```
$ jib --target docker://imageRef
```
