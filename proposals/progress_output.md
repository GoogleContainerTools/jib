# Proposal: Provide feedback on push progress

Relevant issues: [#806](https://github.com/GoogleContainerTools/jib/issues/806), [#1251](https://github.com/GoogleContainerTools/jib/issues/1251)

## Motivation

Currently, Jib lacks any progress feedback for layer pushes. In cases where layers take a long time to push, this lack of progress indication:

- makes it hard to estimate how long the build will take
- makes it unclear as to whether the build is stuck or will complete in some reasonable amount of time
- may deter first-time users from continuing to use Jib 

This is especially prevalent for first-time users whose first builds would need to push all the layers, including the large base image layers.

## Current output

An example of the current output (as of version `0.10.0`) looks like:

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

Since Jib builds/pulls and pushes each layer independently, synchronized progress feedback (completion over all layers, such as a progress bar) could potentially limit the concurrency of the build process - but this might be negligible.

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

- know ahead of time how many layers will be pulled/pushed

## Proposal

Display an overall progress bar along with the tasks currently being executed.

### Example

```
[=====================            ] 60% complete
> Pushing classes layer
> Pulling base image layer 50501d3b88f7
> Pushing dependencies layer
> Pushing base image layer 8b106a18283f
```

The currently executing tasks are displayed below the overall progress bar.

*Note that this would replace the logs messages that are currently outputted as those may corrupt the progress bar display.*

### Summary

The implementation consists of two parts:

1. Emit progress events from the builder steps.
1. Monitor progress events and display to console.

### Emit progress events

There are a few issues to address for the progress event design:

- the total amount of work is not known beforehand, so there is no static max progress known at any time
- the builder steps are asynchronous and therefore progress events should not share a single progress state
- rather, the progress event receiver should be the only potentially stateful entity

These issues can be resolved with a *decentralized allocation tree*.

#### Decentralized allocation tree (DAT)

Each node in the DAT is immutable and initialized with a count of allocation units. These allocation units are to be claimed by progress made on that node or child nodes. Each child node claims 1 allocation unit of the parent node, meaning that completion of all allocation units on that child node means completion of 1 allocation unit on the parent node.

##### Example

Consider a root node (`Node A`) with 10 allocation units. A child node (`Node B`) is added with 4 allocation units. Each of these allocation units represents `10% x 25% = 2.5%` of the entire DAT progress. Completion of all the 4 child node allocation units means that the root node completed 1 allocation unit, or `10%`.

New allocations can be added on-the-fly in a decentralized manner. Let's say a new download started for `123000` bytes and that download should be represented by one of the allocation units of `Node B`. We would create a new node (`Node C`) with `123000` allocation units and have its parent be `Node B`. This establishes the `123000` bytes that would represent `2.5%` of the overall progress without needing to modify other nodes or synchronize with other tasks that may add their own sub-allocations. This also limits the creation of suballocations to only children tasks of the task that created that allocation, making it fully-compatible with a DAG task pipeline.

#### Benefits of DAT approach

The DAT approach solves all of the issues present:

- The progress % would never decrease even in an unknown total work scenario
- The dynamic suballocation supports the asynchronous builder steps
- The allocations are immutable, and state (total progress amount) can be kept at the receiver (DAT reader) side

The downside is that the overall progress is not linear in scale among the work of each allocation. For example, an allocation representing a download may have each byte represent `1%` of the total progress while another may have each byte represent `2%`.

### Monitor progress events

Progress events will be emitted with
1. an allocation node, and
1. a number of progress units completed on that allocation node

Therefore, the progress monitor receiving the progress events will only need to keep a single total progress amount as its state. Upon receiving a progress event, the progress monitor updates its total progress amount by:

```
totalProgressAmount += progressUnits / progressTotal * allocationFraction;
```

`progressUnits` - the progress units from the progress event \
`progressTotal` - the number of allocation units for the allocation node associated with the progress event \
`allocationFraction` - the fraction of the total progress represented by the allocation node, calculated as the *inverted product of all the parent allocation units*

### Display to console

The progress monitor can display a progress bar to the console based on the `totalProgressAmount` upon each update. Using `\r` could work, but results in the console cursor overlapping with the progress bar line. The proposal is to, for each update, move the cursor up a line (`\033[1A`) and print the progress bar line with a newline at the end.

The progress monitor can also keep track of which allocation nodes have completed to display the currently executing tasks.

For Gradle, in order to keep the log messages in sync with Gradle's own log messages (such as the Gradle progress bar), the progress bar display should effectively become a "footer" to the normal log messages Jib outputs and be displayed via the same Gradle Logger interface as the rest of the log messages.

## Alternative rejected proposal

*This alternative proposal was rejected because it cluttered the log messages too much.*

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
