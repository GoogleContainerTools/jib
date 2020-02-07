# Jib with GraalVM Native Executables (EXPERIMENTAL)

> *Note:* Jib's `native-image` support is experimental and may be
> changed -- or even removed -- without notice.

This example demonstrates using the Jib's experimental `native_image`
containerizing mode.  To build, you will need to do a Maven build
like the following:

```
JAVA_HOME=$GRAALVM_HOME mvn package \
    native-image:native-image \
    jib:dockerBuild -Dimage=native-hello
```

The initial `package` will build a jar of the application.  The
`native-image-maven-plugin` will download the GraalVM `native-image`
component and use it to compile the jar with its dependencies, and
`jib:dockerBuild` will create a docker image.

Note that you must run this build from the same platform as your
container as `native-image` does not support cross-compilation at
the moment.  Further note that the `native-image-maven-plugin`
requires being invoked from JVM that supports the
_JVM Compier Interface_ (JVMCI).  We usually use the Graal JVM
distributions.

