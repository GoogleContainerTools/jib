# Proposal: Address Base Image Reproducibility

Implemented in: **v0.9.0**

## Motivation

One of the main goals of Jib is to be able to build images reproducibly, such that the same contents always creates the same images. It does this by wiping the timestamps and user information from the files in the Java application layers (dependencies, resources, classes). However, it does not do the same for the base image layers, which, by default, are from the latest [`gcr.io/distroless/java`](gcr.io/distroless/java) image. This may be unexpected behaviors since by default, reproducibility is on (the user may switch it off using the `enableReproducibleBuilds` parameter for Maven or the `reproducible` parameter for Gradle).

### Terminology

Image **reference** refers to the full reference for an image. This can be as short as `busybox` (which refers to the `library/busybox` **repository** on the Docker Hub **registry**), or as long as `gcr.io/distroless/java@sha256:0135c8b1adb3ed906f521973f825cea3fcdcb9b0db2f4012cc05480bf4d53fd6` (which refers to the image with **digest** `sha256:0135c8b1adb3...` in the `distroless/java` repository on the `gcr.io` registry). An image reference without a specific digest or tag, like `gcr.io/distroless/java`, defaults to the `latest` **tag**, which always refers to the newest digest in that repository.

## Problem

The main problem is that the reproducibility feature of Jib does not actually guaranteed *for the image*, but rather only guarantees reproducibility *for the application layers*. This is a bug.

The problem arises in a common workflow where the developer expects reproducibility:

1. The developer commits a change as version 123.
1. The developer builds the image for that commit - results in image A.
1. On another machine (possibly in prod), that developer checks out version 123 and builds the image - this should have resulted in image A again.

However, since Jib uses the latest version of the [gcr.io/distroless/java](gcr.io/distroless/java) image (which is updated rather frequently - about every 2 weeks) as the base image to build the application layers on top of, if a newer [gcr.io/distroless/java](gcr.io/distroless/java) is latest, the rebuild would result in a different image than expected.

## Goals

- Maintain ease-of-use (no unnecessary extra configuration, at least for the default case)
- Preferable: Keep reproducibility on by default

## Solution

Jib will still use `gcr.io/distroless/java` by default, since in development, users may wish to keep at the latest base image. An alternative would be to use a specific digest of `gcr.io/distroless/java` but that would involve tying a version of Jib to a version of distroless.

The `reproducible`/`enableReproducibleBuilds` configuration will be removed. Application layers (dependencies, resources, classes) will always be reproducible.

Reproducibility will be guaranteed if the user specifies a specific digest to use for a base image. This can be specified as a fully-qualified custom base image, or as a `tag` configuration (Maven).

The user will be warned if the base image used is tagged with `latest` such that reproducibility is not guaranteed. Note that this warning is given by default.

So, the logic flow would be:

1. Jib uses `gcr.io/distroless/java` as the base image.
1. If the user specifies a different image to use as the base image, use that.
1. The user can configure a specific digest to use - `tag` for Maven, and `from.image` for Gradle.
1. If the final tag/digest is still `latest`, warn the user that reproducibility is not guaranteed due a changeable base image, and suggest the user to specify a specific digest.

## Implementation

- Remove the `reproducible`/`enableReproducibleBuilds` configuration and always build application layers reproducibly.
- When validating the `jib-maven-plugin`/`jib-gradle-plugin` configuration, warn the user if the base image uses a `latest` tag.
