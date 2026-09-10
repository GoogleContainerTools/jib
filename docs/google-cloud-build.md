# Jib on Google Cloud Build

You can use Jib on [Google Cloud Build](https://cloud.google.com/build) in a simple step:

```yaml
steps:
  - name: 'gcr.io/cloud-builders/javac:8'
    entrypoint: './gradlew'
    args: ['--console=plain', '--no-daemon', ':server:jib', '-Djib.to.image=gcr.io/$PROJECT_ID/$REPO_NAME:$COMMIT_SHA']
```

Any Java container can be used for building, not only the `gcr.io/cloud-builders/javac:*` (from [gcr.io/cloud-builders/javac](https://github.com/GoogleCloudPlatform/cloud-builders/tree/master/javac)), for example with [Temurin](https://adoptium.net/en-GB/temurin/)'s:

```yaml
steps:
  - name: 'docker.io/library/eclipse-temurin:25'
    entrypoint: './gradlew'
    args: ['--console=plain', '--no-daemon', ':server:jib', '-Djib.to.image=gcr.io/$PROJECT_ID/$REPO_NAME:$COMMIT_SHA']
```

To use [Google "Distroless" Container Images](https://github.com/GoogleContainerTools/distroless) to build with Jib on Google Cloud Build, and avoid running into `Step #1: standard_init_linux.go:228: exec user process caused: no such file or directory` errors (because Google's _distroless_ containers are based on `busybox`), you have to do something like this:

```yaml
steps:
  - name: 'gcr.io/distroless/java17-debian11:debug'
    entrypoint: '/busybox/sh'
    args:
      - -c
      - |
        ln -s /busybox/sh /bin/sh
        ln -s /busybox/env /usr/bin/env
        /workspace/gradlew --console=plain --no-daemon --gradle-user-home=/home/.gradle :server:jib -Djib.to.image=gcr.io/$PROJECT_ID/$REPO_NAME:$COMMIT_SHA
```
