# Proposal: Make sub project/module jars included in container reproducible

Relevant issue: [#1945](https://github.com/GoogleContainerTools/jib/issues/1945)  
Relevent PR: [#2070](https://github.com/GoogleContainerTools/jib/pull/2070)

| ❌ Aborted ❌ |
| :----- |
| This proposal is archived but unimplemented, in favor of documentation in the multimodule example: https://github.com/GoogleContainerTools/jib/tree/master/examples/multi-module |

## Motivation

Currently in multimodule builds, jib takes the jars produced by submodule (and subproject) directly from
the build system. This means if the build system (gradle, maven) doesn't produce reproducible jars, and
jib includes them in the final container, the reproducibility guarantees of jib are not satisfied.

What we want to do is to rewrite these jars to remove any information that could make builds not reproducible.

## Requirements

In order to achieve reproducibility for jars, we need to:
1. Remove timestamps and preserve file order. This can be achieved by setting the time to a fixed value
   and sorting zip entries alphabetically
2. Remove any changeable values from META-INF/MANIFEST.MF like build time, etc

## Non-goals

Signed archives will/should not be touched by this process. While we expect that submodule dependencies will
not be signed, there's no way for us to know what all users are doing eveywhere. A warning should be presented
to the user that signed archives are processed by jib to be reproducible.

## Current solution

Currently the user must configure their build to be reproducible. (we will borrow from these two solutions in our own solution with
proper attribution)

### Gradle
In gradle that means configuring the jar task explicitly: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html

```
jar {
  preserveFileTimestamps = false
  reproducibleFileOrder = true
}
```
and removing any changeable meta data from the MANIFEST.MF that they may have previously configured.

### Maven

In maven, a user must configure an external plugin to do this, one could use: https://github.com/zlika/reproducible-build-maven-plugin
on all submodules and potentially achieve this on their own.

## Proposed Solution

Jib should just handle this itself on the `PROJECT_DEPENDENCIES` layer. For all jars included in the layer, Jib needs to:

1. Inspect the jar's `MANIFEST.MF` and remove values that could change.
2. Force a single timestamp on all files
3. Order the files in the jar(zip) by name (alphabetical)

Jib *will not* do this for any other layer

### Configuration

Jib can make this configurable, but it should be `true` by default. A system property like

```
-Djib.projectDependencies.reproducible=true/false
```

### Implementation

1. A utility in jib-core to convert jars to *reproducible* jars.
1. This utility only impacts the `PROJECT_DEPENDENCIES` layer
1. Triggering this utility at one of two places
    1. During layer writing by introducing some new configuration into `LayerConfiguration` -- potentially more performant
    1. At the plugin level to rewrite the jar before presenting it to the Jib Containerizer

## Potential Issues

1. Maybe we should just direct users to configure their builds to be reproducible using the gradle/maven mechanism desribed above.
2. Can we *really, truly* know every value thrown into the manifest by the user??
