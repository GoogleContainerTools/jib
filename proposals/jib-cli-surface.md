# Jib CLI Surface Specification

Specification for a command structures exposed to users of the jib-cli

## Usage
`jib command [OPTIONS]`

## Options

### Required
1. `-t, --target` a target image reference

### Optional
1. `-b, --build-file` location of the build file (default ./jib.yaml)
1. `--credHelper<registry>=<credHelper>` the credential helper to use for a registry
1. `-P<name>=<value>, `, templating parameters replace `${name}` with `value` in the build file
1. `--tags` additional tags for target (optional)
1. `-v, --version` print version information and exit
1. `--verbostiy` change logging verbosity (error, warn, lifecycle (default), info, debug)

## Commands
1. `build` build to registry
1. `dockerBuild` build to docker daemon
1. `buildTar` build to a tar file

## `build`

## `dockerBuilder` only

## `buildTar` only

### Required
1. `-f` output tar file  
