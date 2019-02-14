# Use [Google Cloud Build](https://cloud.google.com/cloud-build) with Jib

This example shows how to use [Google Cloud Build](https://cloud.google.com/cloud-build) and Jib to build your Java project into a container image. 

## Prerequisites

Make sure you enable the Cloud Build API and have the Cloud SDK installed. The `gcloud` binary should be on your `PATH`.

See the [Cloud Build Quickstart](https://cloud.google.com/cloud-build/docs/quickstart-docker#before-you-begin) for instructions.

## Build a Maven project

To build your Maven project into a container image, use [`cloudbuild-jib-maven.yaml`](cloudbuild-jib-maven.yaml) as the Cloud Build build configuration file:

```bash
gcloud builds submit --config cloudbuild-jib-maven.yaml <Maven project directory>
```

Make sure to change the `_IMAGE_NAME` substitution to the name of the image you wish to build.

## Build a Gradle project

To build your Gradle project into a container image, first add Jib to your `build.gradle`:

```groovy
plugins {
  id 'com.google.cloud.tools.jib' version '1.0.0'
}
```

Then, use [`cloudbuild-jib-gradle.yaml`](cloudbuild-jib-gradle.yaml) as the Cloud Build build configuration file:

```bash
gcloud builds submit --config cloudbuild-jib-gradle.yaml <Gradle project directory>
```

Make sure to change the `_IMAGE_NAME` substitution to the name of the image you wish to build.

### Build a Gradle project without applying the Jib plugin

You can also build any Gradle project without having to add Jib to the `build.gradle`. First, copy [`auto-jib.gradle`](auto-jib.gradle) to your Gradle project directory. Then, uncomment the `init-script` arg in [`cloudbuild-jib-gradle.yaml`](cloudbuild-jib-gradle-auto.yaml) and submit the build with:

```bash
gcloud builds submit --config cloudbuild-jib-gradle.yaml <Gradle project directory>
``` 

## HelloWorld example

Try building the provided [helloworld-sample](helloworld-sample) Java project on Google Cloud Build:

### Maven

```bash
gcloud builds submit --config cloudbuild-jib-maven.yaml helloworld-sample
```

### Gradle

Uncomment the `init-script` arg in [`cloudbuild-jib-gradle.yaml`](cloudbuild-jib-gradle.yaml) and submit the build with:

```bash
gcloud builds submit --config cloudbuild-jib-gradle.yaml helloworld-sample
```

## Speeding up your build

By default, Google Cloud Build does not cache anything across builds. However, you can [use Google Cloud Storage to cache files](https://cloud.google.com/cloud-build/docs/speeding-up-builds) across builds to potentially speed up subsequent builds. Note that this may not necessarily speed up your build since it involves sending cache data to and from [Google Cloud Storage](https://cloud.google.com/storage).

First, [create a Google Cloud Storage bucket](https://cloud.google.com/storage/docs/creating-buckets) to use for storing the cache files.

Then, for Maven, use [`cloudbuild-jib-maven+cache.yaml`](cloudbuild-jib-maven+cache.yaml) as the Cloud Build configuration file. Change the `_GCS_CACHE_BUCKET` substitution to the bucket you just created and submit the build with:

```bash
gcloud builds submit --config cloudbuild-jib-maven+cache.yaml <Maven project directory>
```

For Gradle, use [`cloudbuild-jib-gradle+cache.yaml`](cloudbuild-jib-gradle+cache.yaml):

```bash
gcloud builds submit --config cloudbuild-jib-gradle+cache.yaml <Gradle project directory>
```
