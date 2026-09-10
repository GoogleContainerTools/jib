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
                          tar:///absolute/path.tar
```

### Optional


#### Build Config
```
    --additional-tags <tag1>[,<tag2>,...]  additional tags for target
    --application-cache <directory>        location of the application cache (jib default is temp directory)
    --base-image-cache <directory>         location of the base image cache (jib default is user cache)
-b, --build-file <file>                    location of the build file (default <context>/jib.yaml)
-c, --context <dir>                        location of the build context (default .)
    --docker-config <directory>            location of docker configuration
    --name <image-ref>                     image name to bake into tar file, required when using "-t tar://..." 
-p, --parameter <name>=<value>             templating parameters replace `${name}` with `value` in the build file (repeatable)
```

#### Auth/Security
```
    --allow-insecure-registries            allow jib to communicate with registries over https
    --send-credentials-over-http           allow jib to send credentials over http (used in conjunction with --allow-insecure-registries)
```
##### Credentials

Credentials can be specified using credential helpers or username + password. The following options are available
```
    --credential-helper <credHelper>       credential helper to use for registries, a path or name suffix (docker-credential-<suffix>)
    --to-credential-helper <credHelper>    credential helper to use only for the target registry
    --from-credential-helper <credHelper>  credential helper to use only for the base image registry

    --username <username>                  configure a username for authenticating against registries
    --password <password>                  configure a password for authenticating against registries (interactive if <password> is omitted)
    --to-username <username>               configure a username for authenticating on the registry that an image is being built to
    --to-password <password>               configure a password for authenticating on the registry and image is being built to (interactive if <password> is omitted)
    --from-username <username>             configure a username for authentication on the registry that a base image is being sourced from
    --from-password <password>             configure a password for authentication on the registry that a base image is being sourced from (interactive if <password> is omitted)
```
combinations of `credential-helper`, `username` and `password` flags come with restrictions and can be use only in the following ways:

Only Credential Helper
1. `--credential-helper`
1. `--to-credential-helper`
1. `--from-credential-helper`
1. `--to-credential-helper`, `--from-credential-helper`

Only Username and Password
1. `--username`, `--password`
1. `--to-username`, `--to-password`
1. `--from-username`, `--from-password`
1. `--to-username`, `--to-password`, `--from-username`, `--from-password`

Mixed Mode
1. `--to-credential-helper`, `--from-username`, `--from-password`
1. `--from-credential-helper`, `--to-username`, `--to-password`


#### Info Params
```
    --help                  print usage and exit
    --console <type>        set the console type (auto (default), plain, rich)
    --verbosity <level>     set logging verbosity (quiet, error, warn, lifecycle (default), info, debug)
-v, --version               print version information and exit
```

#### Debugging Params (hidden in help)
```
    --stacktrace            print stacktrace on error (for debugging issues in the jib-cli)
    --http-trace            enable http tracing at level=config, output=console
    --serialize             run jib in serialized mode
```

### Flag Files

Pico cli allows the use of flag files (https://picocli.info/#AtFiles). The jib cli will enable this feature. This will allow allow build environment configuration to be defined in a configuration file.

## Environment Variables

### Docker daemon specification

Information on communicating with the docker daemon is determined using the `DOCKER_*` environment variables.

## Other considerations

- Users have asked to warm up the cache, perhaps we can have a `cache` command option
- In the future we should be able to build `jar`s from the command line
- Default application cache directory is a temporary directory (will lead to slow rebuilds), we should do a better job of defining a default here.
