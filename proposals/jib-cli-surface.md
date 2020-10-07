# Jib CLI Surface Specification

Specification for a command structures exposed to users of the jib-cli

## Usage
`jib COMMAND [OPTIONS]`

## Commands

The jib cli exposes the following top level commands
```
build        build a container
```

## Options

### Required
```
-t  --target <image-ref>  a target image reference, using the url schema defined in jib
                          gcr.io/project/image (default is build to registry)
                          registry://gcr.io/project/image
                          docker://some-image-ref
                          tar://relative/path.tar
                          tar:///aboslute/path.tar
```

### Optional


#### Build Config
```
-b, --build-file <file>                    location of the build file (default <context>/jib.yaml)
-c, --context <dir>                        location of the build context (default .)
    --docker-config <directory>            location of docker configuration
    --name <image-ref>                     image name to bake into tar file (only available when using -t tar://...)
-p, --parameter <name>=<value>             templating parameters replace `${name}` with `value` in the build file (repeatable)
    --tag <tag1>[,<tag2>,...]              additional tags for target
```

#### Auth/Security
```
    --allow-insecure-registries            allow jib to communicate with registries over https
    --credHelper <registry>=<credHelper>   credential helper to use for a registry (repeatable)
    --send-credentials-over-http           allow jib to send credentials over http (used in conjunction with --allow-insecure-registries)
    --to-username <username>               configure a username for authenticating on the registry that an image is being built to
    --to-password <password>               configure a password for authenticating on the registry and image is being built to (interactive if <password> is omitted)
    --from-username <username>             configure a username for authentication on the registry that a base image is being sourced from
    --from-password <password>             configure a password for authentication on the registry that a base image is being sourced from (interactive if <password> is omitted)
```

#### Info Params
```
    --help                                 print usage and exit
    --stacktrace                           print stacktrace on error (for debugging issues in the jib-cli)
    --verbosity <level>                    set logging verbosity (error, warn, lifecycle (default), info, debug)
-v, --version                              print version information and exit 
```

### Flag Files

Pico cli allows the use of flag files (https://picocli.info/#AtFiles). The jib cli will enable this feature.

## Environment Variables

### Docker daemon specification

Information on communicating with the docker daemon is determined using the `DOCKER_*` environment variables.

## Other considerations

- Users have asked to warm up the cache, perhaps we can have a cache build?
- In the future we should be able to build `jar`s from the command line
- 

