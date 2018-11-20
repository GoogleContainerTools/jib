# Proposal: Provide feedback on push progress

Relevant issues: [#806](https://github.com/GoogleContainerTools/jib/issues/806), [#1251](https://github.com/GoogleContainerTools/jib/issues/1251)

## Motivation

Currently, Jib lacks any progress feedback for layer pushes. In cases where layers take a long time to push, this lack of progress indication:

- makes it hard to estimate how long the build will take
- makes it unclear as to whether the build is stuck or will complete in some reasonable amount of time
- may deter first-time users from continuing to use Jib 

This is especially prevalent for first-time users whose first builds would need to push all the layers, including the large base image layers.

## Current output

An example of the current output (as of versoin `0.10.0`) looks like:

```
Containerizing application to <image>...
warning: Base image 'gcr.io/distroless/java' does not use a specific image digest - build may not be reproducible
Retrieving registry credentials for gcr.io...
Getting base image gcr.io/distroless/java...
Building dependencies layer...
Building resources layer...
Building classes layer...

Container entrypoint set to [java, -cp, /app/resources:/app/classes:/app/libs/*, <main class>]
Finalizing...

Built and pushed image as <image>
```

Note that the build would pause on `Finalizing...` until all layers are finished pushing.

## Goal

To display fine-grained progress feedback during the build, but not clutter the log messages.

## Design considerations

Since Jib builds/pulls and pushes each layer independently, we should not provide synchronized progress feedback (completion over all layers) as that would limit the concurrency of the build process.

All progress feedback should be local to each layer.

The available information for each layer include:

- type (classes, resources, dependencies, base image layer, etc.)
- digest
- size

The non-available information for each layer include:

- sizes of other layers (eg. other layers may not have finished building)
- number of other layers (eg. base image layers have not been resolved before starting to push an application layer)

Therefore, the actions we can perform for progress feedback include:

- monitor the number of bytes sent (and percentage completion)

And the actions we cannot perform include:

- synchronize progress between layers
- output line replacement (ie. update a progress bar in the same output line) - Maven/Gradle output limited to a single-line logging interface

## Proposal

Display percentage completion of layer pushes at 10s intervals with a 20s cliff.

### Example

```
...
Getting base image gcr.io/distroless/java…
Building dependencies layer…
Building resources layer...
Building classes layer...
Pushing base image layer d4c593cddccd: 30%
Pushing dependencies layer: 30%
Pushing base image layer 8b106a18283f: 10%
Pushing resources layer: 100%
Pushing classes layer: 100%
Pushing base image layer 8b106a18283f: 45%
Pushing base image layer 50501d3b88f7: 60%
Pushing base image layer d4c593cddccd: 100%
Pushing dependencies layer: 65%
Pushing base image layer 50501d3b88f7: 100%
Pushing dependencies layer: 90%
Pushing base image layer 8b106a18283f: 70%
Pushing dependencies layer: 100%
Pushing base image layer 8b106a18283f: 90%
Pushing base image layer 8b106a18283f: 100%

Container entrypoint set to [java, -cp, /app/resources:/app/classes:/app/libs/*, <main class>]
Finalizing...

Built and pushed image as <image>
```

Note that `100%` will always be displayed to indicate completion of each layer.

### Alternative considerations

- display byte-completion (eg. `10MB/100MB`)
- display with time (eg. `25% (10s)`)
- display with progress bar (eg. `[=====                   ] 10MB/100MB (15s)`)
