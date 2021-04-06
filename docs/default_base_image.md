# Default Base Images in Jib

## Jib Build Plugins 3.0

Starting from version 3.0, the default base image is the official [`adoptopenjdk`](https://hub.docker.com/_/adoptopenjdk) image on Docker Hub. AdoptOpenJDK (which is [being renamed to Adoptium](https://blog.adoptopenjdk.net/2020/06/adoptopenjdk-to-join-the-eclipse-foundation/)) is a popular OpenJDK build also used by [Google Cloud Buildpacks](https://github.com/GoogleCloudPlatform/buildpacks) (for Java) by default. Prior to 3.0, Jib Maven and Gradle plugins built images based on Distroless Java when the user didn't make an explicit choice. The Jib team carefully assessed all the surrounding factors and ultimately decided to switch from Distroless for the best interest of the Jib users. That is, to meet one of the core missions of Jib to build a secure and optimized image.

Note that this change matters only when you are not specifying any base image via `jib.from.image` (Gradle) or `<from><image>` (Maven), which we have recommended against consistently. For strong reproducibility, you should pin to a specific base image using a digest (or at least a tag). And we want to be clear that Jib's default choice for AdoptOpenJDK does not imply any endorsement to it; you should do your due diligence when making a choice for a base image.

Therefore, for many users already setting a specific base image, there will be absolutely no difference when they upgrade to 3.0. Even for those who are not specifying a base image, most likely upgrading to 3.0 will keep working fine, because it's just about getting a JRE from a different image. But if you are not setting a base image, do it now. And for some reason if you have to keep the exact same behavior when using 3.0, you can always specify the Distroless Java as a base image.

## Jib CLI

For the JAR mode, Jib CLI has always used AdoptOpenJDK.
