# Proposal: Improve Jib Log Output

Implemented in: **v0.9.0**

## Motivation

To have a more organized, readable, and aethetically-pleasing log output.

## Goals

- Consistency between Maven and Gradle
- Organize across levels of verbosity

## Proposed Design

### Categories

The log output will be organized into 4 categories:

#### Lifecycle

Messages that are part of the execution and will always show up.

#### Info

Messages that are extra information and do not need to be displayed by default. Note that for Maven, these will be at the same level as Debug.

#### Debug

Messages that are useful for debugging what went wrong, but not as useful (or convoluting) as user-facing information. These would be helpful to include in, for example, a bug report.

#### Warn/Error

These messages will be displayed by default, but as the warning/error format of the specific Maven/Gradle logger implementations. Jib should not explicitly log error messages, but rather throw those messages as part of exceptions.

### Information to include

The information to include, in the order in which they should appear are:

1. (info) Where inputs are inferred from, including
    1. The main class
1. (info) Files that went into each layer
    1. Classes, resources, dependencies
1. (lifecycle/info) Important build steps
    1. Show what’s skipped?
    1. Asynchronous steps (the order may vary between runs)
        - [lifecycle] Retrieving credentials for \<base image>...
            - [info] Credentials for \<target image> found via (docker-credential-*/Docker config/Maven settings) in \<### ms>
        - [lifecycle] Retrieving credentials for \<target image>...
            - [info] Credentials for \<target image> found via (docker-credential-*/Docker config/Maven settings) in \<### ms>
        - [lifecycle] Getting base image \<base image>…
            - [info] Fetching manifest for \<base image>...
            - [info] Fetched manifest for \<base image> in \<### ms>
            - [info] Fetching layer \<digest> of \<base image>...
            - [info] Fetched layer \<digest> of \<base image> in \<### ms>
            - [info] Pulled base image in \<### ms>
        - [lifecycle] Building dependencies layer...
            - [info] Built and cached dependencies layer \<digest> in \<### ms>
            - [info] Pushing dependencies layer \<digest>...
            - [info] Pushed dependencies layer \<digest> in \<### ms>
        - [lifecycle] Building resources layer
            - Same as for dependencies
        - [lifecycle] Building classes layer
            - Same as for dependencies
        - [lifecycle] Finalizing…
            - [info] Pushing container configuration \<digest>...
            - [info] Pushed container configuration \<digest> in \<### ms>
            - [info] Pushing manifest...
            - [info] Pushed manifest in \<### ms>
        - [lifecycle] Build complete in \<### s>
1. Finalized information
    1. Container entrypoint set to \<entrypoint>
    1. Built and pushed image as \<target image>

### Example log

At lifecycle level:

```
[lifecycle] Containerizing application to gcr.io/my-gcp-project/my-app...
[lifecycle]
[lifecycle] Retrieving credentials for gcr.io/distroless/java…
[lifecycle] Retrieving credentials for gcr.io/my-gcp-project/my-app...
[lifecycle] Getting base image gcr.io/distroless/java…
[lifecycle] Building dependencies layer...
[lifecycle] Building resources layer...
[lifecycle] Building classes layer...
[lifecycle] Finalizing…
[lifecycle] Build complete in 2.1354s
[lifecycle]
[lifecycle] Container entrypoint set to [java, -cp, /app/libs/*:/app/resources/:/app/classes/, my.App]
[lifecycle]
[lifecycle] Built and pushed image as gcr.io/my-gcp-project/my-ap
```

At info level:

```
[lifecycle] Containerizing application to gcr.io/my-gcp-project/my-app...
[info] Using main class from maven-jar-plugin/: my.App
[info] Containerizing application with the following files:
[info]     Classes:
[info]         <the files>
[info]     Resources:
[info]         <the files>
[info]     Dependencies:
[info]         <the files>
[lifecycle]
[lifecycle] Retrieving credentials for gcr.io/distroless/java…
[info] Credentials for gcr.io/distroless/java found via docker-credential-gcr in 400ms
[lifecycle] Retrieving credentials for gcr.io/my-gcp-project/my-app...
[info] Credentials for gcr.io/my-gcp-project/my-app found via docker-credential-gcr in 300ms
[lifecycle] Getting base image gcr.io/distroless/java…
[info] Fetching manifest for gcr.io/distroless/java...
[info] Fetched manifest for gcr.io/distroless/java in 300ms
[info] Fetching layer sha256:fafafafa... of gcr.io/distroless/java…
[info] Fetching layer sha256:fafafafa... of gcr.io/distroless/java...
[info] Fetched layer sha256:fafafafa... of gcr.io/distroless/java in 200ms
[info] Fetched layer sha256:fafafafa... of gcr.io/distroless/java in 200ms
[info] Pulled base image in 550ms
[lifecycle] Building dependencies layer...
[info] Built and cached dependencies layer sha256:ababab... in 20ms
[info] Pushing dependencies layer sha256:ababab...
[info] Pushed dependencies layer sha256:ababab... in 200ms
[lifecycle] Building resources layer
[info] Built and cached resources layer sha256:bcbcbc... in 20ms
[info] Pushing resources layer sha256:bcbcbc...
[info] Pushed resources layer sha256:bcbcbc... in 250ms
[lifecycle] Building classes layer
[info] Built and cached classes layer sha256:cdcdcd... in 10ms
[info] Pushing classes layer sha256:cdcdcd...
[info] Pushed classes layer sha256:cdcdcd... in 10ms
[lifecycle] Finalizing…
[info] Pushing container configuration sha256:cdcdcd...
[info] Pushed container configuration sha256:cdcdcd... in 100ms
[info] Pushing manifest...
[info] Pushed manifest in 150ms
[lifecycle] Build complete in 2.1354s
[lifecycle]
[lifecycle] Container entrypoint set to [java, -cp, /app/libs/*:/app/resources/:/app/classes/, my.App]
[lifecycle]
[lifecycle] Built and pushed image as gcr.io/my-gcp-project/my-app
```

## Appendix: Current output

Example of current Maven output (`mvn compile jib:build`):

```
[INFO] --- jib-maven-plugin:0.1.7-SNAPSHOT:build (default-cli) @ hello-world ---
[INFO] Using main class from maven-jar-plugin: com.test.HelloWorld
[INFO]
[INFO] Containerizing application with the following files:
[INFO]
[INFO] 	Dependencies:
[INFO]
[INFO] 		/google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/libs/dependency-1.0.0.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/com/google/guava/guava/23.6-jre/guava-23.6-jre.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/org/checkerframework/checker-compat-qual/2.0.0/checker-compat-qual-2.0.0.jar
[INFO] 		/usr/local/google/home/qingyangc/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar
[INFO] 	Resources:
[INFO]
[INFO] 		/google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/target/classes/20m.2
[INFO] 		/google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/target/classes/aklwejflkjawelkfjlkefjlkawejflaweflkwenfkneawklfewnlknaewlgjnlaewkgjklaewgnjlakewnglaekwnglaewngkwengnglkweangklawnglekangwklgnelkngwlknglekwnglkanglkwgnew
[INFO] 		/google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/target/classes/world2
[INFO] 	Classes:
[INFO]
[INFO] 		/google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/target/classes/com
[INFO]
[INFO]
[INFO] Pushing image as gcr.io/qingyangc-sandbox/jibtestimage:built-with-jib
[INFO]
[INFO] RUNNING	Building and pushing image
[INFO] RUNNING	Retrieving registry credentials for gcr.io
[INFO] RUNNING	Retrieving registry credentials for gcr.io
[INFO] Checking credentials from docker-credential-gcr
[INFO] Checking credentials from docker-credential-gcr
[INFO] RUNNING	Building application layers
[INFO] RUNNING	Building dependencies layer
[INFO] RUNNING	Building resources layer
[INFO] Building application layers : 0.931 ms
[INFO] RUNNING	Building classes layer
[INFO] RUNNING	Setting up to push layers
[INFO] Setting up to push layers : 1.083 ms
[INFO] Building dependencies layer : 2.82 ms
[INFO] Building resources layer : 2.879 ms
[INFO] Building classes layer : 10.285 ms
[INFO] Using docker-credential-gcr for gcr.io
[INFO] Using docker-credential-gcr for gcr.io
[INFO] Retrieving registry credentials for gcr.io : 325.95 ms
[INFO] Retrieving registry credentials for gcr.io : 325.974 ms
[INFO] RUNNING	Authenticating with push to gcr.io
[INFO] RUNNING	Authenticating pull from gcr.io
[INFO] Authenticating pull from gcr.io : 674.983 ms
[INFO] RUNNING	Pulling base image manifest
[INFO] Authenticating with push to gcr.io : 745.519 ms
[INFO] RUNNING	Pushing BLOB sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986
[INFO] RUNNING	Pushing BLOB sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1
[INFO] RUNNING	Pushing BLOB sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18
[INFO] BLOB : sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1 already exists on registry
[INFO] Pushing BLOB sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1 : 176.432 ms
[INFO] BLOB : sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986 already exists on registry
[INFO] Pushing BLOB sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986 : 189.759 ms
[INFO] BLOB : sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18 already exists on registry
[INFO] Pushing BLOB sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18 : 281.987 ms
[INFO] Pulling base image manifest : 439.962 ms
[INFO] RUNNING	Setting up base image caching
[INFO] Setting up base image caching : 1.027 ms
[INFO] RUNNING	Pulling base image layer sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5
[INFO] RUNNING	Setting up to push layers
[INFO] RUNNING	Pulling base image layer sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24
[INFO] Pulling base image layer sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 : 0.078 ms
[INFO] RUNNING	Pulling base image layer sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca
[INFO] RUNNING	Pushing BLOB sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5
[INFO] Pulling base image layer sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca : 0.167 ms
[INFO] Setting up to push layers : 0.1 ms
[INFO] RUNNING	Pushing BLOB sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca
[INFO] Pulling base image layer sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 : 0.281 ms
[INFO] RUNNING	Pushing BLOB sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24
[INFO] RUNNING	Building container configuration
[INFO] Building container configuration : 15.243 ms
[INFO] BLOB : sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca already exists on registry
[INFO] Pushing BLOB sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca : 90.115 ms
[INFO] BLOB : sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 already exists on registry
[INFO] Pushing BLOB sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 : 123.695 ms
[INFO] Pushing container configuration sha256:71529d335fd983477a62eaf9e1cd37e488c48eb1d9477e09fbd63e37857b251c : 152.741 ms
[INFO] BLOB : sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 already exists on registry
[INFO] Pushing BLOB sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 : 304.932 ms
[INFO] RUNNING	Pushing new image
[INFO] Pushing new image : 1191.479 ms
[INFO] Building and pushing image : 3149.412 ms
[INFO]
[INFO] Container entrypoint set to [java, -cp, /app/libs/*:/app/resources/:/app/classes/, com.test.HelloWorld]
[INFO]
[INFO] Built and pushed image as gcr.io/qingyangc-sandbox/jibtestimage:built-with-jib
```

Current Gradle output (`gradle build jib --info`):

```
:jib (Thread[Task worker for ':',5,main]) started.
RUNNING Retrieving registry credentials for gcr.io
Using jib extension for gcr.io
RUNNING Retrieving registry credentials for gcr.io
Retrieving registry credentials for gcr.io : 0.176 ms
RUNNING Building dependencies layer
Using jib extension for gcr.io
RUNNING Building resources layer
RUNNING Building classes layer
RUNNING Authenticating with push to gcr.io
Retrieving registry credentials for gcr.io : 0.342 ms
Building dependencies layer : 0.351 ms
Building resources layer : 0.43 ms
RUNNING Authenticating pull from gcr.io
Building classes layer : 0.958 ms
Authentication error: Unable to respond to any of these challenges: {bearer=WWW-Authenticate: Bearer realm="https://gcr.io/v2/token",service="gcr.io"}
Authentication error: Unable to respond to any of these challenges: {bearer=WWW-Authenticate: Bearer realm="https://gcr.io/v2/token",service="gcr.io"}
Authenticating pull from gcr.io : 196.848 ms
RUNNING Pulling base image manifest
Authenticating with push to gcr.io : 237.681 ms
RUNNING Pushing BLOB sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18
RUNNING Pushing BLOB sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986
RUNNING Pushing BLOB sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1
BLOB : sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18 already exists on registry
Pushing BLOB sha256:3b6efb8e94b25098a394fa4d2e988cc12a6afd7a2b112a2c97cc9899d446ae18 : 101.4 ms
BLOB : sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1 already exists on registry
Pushing BLOB sha256:9a6afec7bf36218264ed61dc525d8fe6b585dd7a84b780a327c3663de4ef51b1 : 120.31 ms
BLOB : sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986 already exists on registry
Pushing BLOB sha256:c63484398b097b7e9693ac373ac95630bb8d8ad8ff90a3277e7105bb77e8e986 : 137.118 ms
Pulling base image manifest : 361.452 ms
RUNNING Setting up base image caching
RUNNING Pulling base image layer sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca
Setting up base image caching : 0.224 ms
Pulling base image layer sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca : 0.086 ms
RUNNING Pulling base image layer sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5
RUNNING Pulling base image layer sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24
Pulling base image layer sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 : 0.224 ms
RUNNING Setting up to push layers
Pulling base image layer sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 : 0.334 ms
RUNNING Pushing BLOB sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca
RUNNING Building container configuration
Setting up to push layers : 0.365 ms
RUNNING Pushing BLOB sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24
RUNNING Pushing BLOB sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5
Building container configuration : 1.208 ms
BLOB : sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca already exists on registry
Pushing BLOB sha256:eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca : 89.462 ms
BLOB : sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 already exists on registry
Pushing BLOB sha256:15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5 : 120.538 ms
Pushing container configuration sha256:71529d335fd983477a62eaf9e1cd37e488c48eb1d9477e09fbd63e37857b251c : 121.327 ms
BLOB : sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 already exists on registry
Pushing BLOB sha256:ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24 : 149.226 ms
RUNNING Pushing new image
Pushing new image : 730.65 ms

> Task :jib
Task ':jib' is not up-to-date because:
  Task has not declared any outputs.

Containerizing application with the following files:

        Dependencies:

                /google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/libs/dependency-1.0.0.jar
                /usr/local/google/home/qingyangc/.m2/repository/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar
                /usr/local/google/home/qingyangc/.m2/repository/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar
                /usr/local/google/home/qingyangc/.m2/repository/com/google/guava/guava/23.6-jre/guava-23.6-jre.jar
                /usr/local/google/home/qingyangc/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar
                /usr/local/google/home/qingyangc/.m2/repository/org/checkerframework/checker-compat-qual/2.0.0/checker-compat-qual-2.0.0.jar
                /usr/local/google/home/qingyangc/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar
        Resources:

                /google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/build/resources/main/20m.2
                /google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/build/resources/main/aklwejflkjawelkfjlkefjlkawejflaweflkwenfkneawklfewnlknaewlgjnlaewkgjklaewgnjlakewnglaekwnglaewngkwengnglkweangklawnglekangwklgnelkngwlknglekwnglkanglkwgnew
                /google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/build/resources/main/world2
        Classes:

                /google/src/cloud/qingyangc/sandbox/google3/experimental/users/qingyangc/sandbox/helloproject/build/classes/java/main/com

Pushing image as gcr.io/qingyangc-sandbox/jibgradleimage:67923843946446


RUNNING Building and pushing image
RUNNING Building application layers
Building application layers : 0.382 ms
RUNNING Setting up to push layers
Setting up to push layers : 0.327 ms
Building and pushing image : 1446.819 ms

Container entrypoint set to [java, -cp, /app/libs/*:/app/resources/:/app/classes/, com.test.HelloWorld]

Built and pushed image as gcr.io/qingyangc-sandbox/jibgradleimage:67923843946446


:jib (Thread[Task worker for ':',5,main]) completed. Took 1.468 secs.
```
