# Contributing to Jib

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution; 
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Building Jib

Jib comes as 3 components:

  - `jib-core`: a library
  - `jib-plugins-common`: a library with helpers for plugin builders
  - `jib-maven-plugin`: a Maven plugin that uses `jib-core` and `jib-plugins-common`
  - `jib-gradle-plugin`: a Gradle plugin that uses `jib-core` and `jib-plugins-common`

To build, use the provided `build.sh` which builds and tests each of the components into your local `~/.m2/repository`.  Note that this script does not run integration tests.

## Code Reviews

1. Set your git user.email property to the address used for step 1. E.g.
   ```
   git config --global user.email "janedoe@google.com"
   ```
   If you're a Googler or other corporate contributor,
   use your corporate email address here, not your personal address.
2. Fork the repository into your own Github account.
3. We follow our own [Java style guide](STYLE_GUIDE.md) that extends the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
3. Please include unit tests (and integration tests if applicable) for all new code.
4. Make sure all existing tests pass (but see the note below about integration tests).
   * In `jib-core`, run `./gradlew clean goJF build integrationTest`
   * In `jib-gradle-plugin`, run `./gradlew clean goJF build integrationTest`
   * In `jib-plugins-common`, run `./gradlew clean goJF build`
   * In `jib-maven-plugin`, run `./mvnw clean fmt:format verify -PintegrationTest`
5. Associate the change with an existing issue or file a [new issue](../../issues).
6. Create a pull request!

**Note** that in order to run integration tests, you will need to set the environment variable `JIB_INTEGRATION_TESTING_PROJECT` to the GCP project you would like to use for testing. You will also need Docker installed with the daemon running. Otherwise, feel free to skip integration tests.

# Development Tips

## Configuring Eclipse

Jib is a mix of Gradle and Maven projects.  But since we do not
publish all artifacts, we instead have the projects reference
source from their dependencies directly using `sourceSets` and
`build-helper-maven-plugin`.  This complicates importing these projects
as the Gradle and Maven tooling for Eclipse faithfully replicate the
direct source references.  This has a side-effect that classes
appear both in the originating project _and_ in the subprojects,
and can interfere with Eclipse's incremental compilation.

The following instructions first import the projects, and then replaces
the source-set-style references to use Eclipse's project references
instead.

  1. Ensure you have installed the Gradle tooling for Eclipse, called
     _Buildship_ (available from [the Eclipse
     Marketplace](https://marketplace.eclipse.org/content/buildship-gradle-integration)).
  2. **Import the Maven project:** Use _File &rarr; Open Projects from File System..._
     to use the Eclipe Smart Import feature and specify the root of the Jib project.
     Import the `jib-maven-plugin` project.
  3. **Import the Gradle projects:** Buildship does [not yet support
     Eclipse Smart Import](https://github.com/eclipse/buildship/issues/356).
     Use _File &rarr; Import &rarr; Gradle &rarr; Existing Gradle Project_
     and import each of `jib-core`, `jib-plugins-common`, and `jib-gradle-plugin`.
  4. **Turn source-set references to project references:** For each of 
     `jib-plugins-common`, `jib-maven-plugin`, and `jib-gradle-plugin`:
     - Right-click on the correponding project and select _Properties &rarr; Java Build Path_
     - Open the _Source_ panel and remove all _linked_ source folders: these are like
       symbolic links to other locations and appear like 
       `jib-plugins-common/main-java - /path/to/jib-core/src/main/java`.
       Only folders within the project should remain when complete.
         - you may wish to remove `jib-gradle-plugin`'s `src/test/resources` and
           `src/integration-test/resources` too as these contain test projects,
           and are not linked in as separate projects and so seem have compilation errors
     - Open the _Projects_ panel and click _Add_ to select the dependencies:
         - `jib-plugins-common` depends on `jib-core`
         - `jib-maven-plugin` depends on `jib-core` and `jib-plugins-common`
         - `jib-gradle-plugin` depends on `jib-core` and `jib-plugins-common`

Note that you will likely need to re-apply these changes whenever
you refresh or update these projects.

## Debugging the Jib Maven Plugin (`jib-maven-plugin`)

### Build and use a local snapshot 

To use a local build of the `jib-maven-plugin`:

  1. Build and install `jib-maven-plugin` into your local `~/.m2/repository`
     with `(cd jib-maven-plugin && ./mvnw install)`; this also builds `jib-core`.
     Alternatively, use the provided `build.sh` which performs an `install`.
  1. Modify your test project's `pom.xml` to reference the `-SNAPSHOT`
     version of the `com.google.cloud.tools.jib` plugin.

If developing from within Eclipse with M2Eclipse (the Maven tooling for Eclipse):

  1. Modify your test project's `pom.xml` to reference the `-SNAPSHOT`
     version of the `com.google.cloud.tools.jib` plugin.
  2. Create and launch a _Maven Build_ launch configuration for the
     test project, and ensure the _Resolve Workspace artifacts_ is checked.

### Attaching a debugger

Run `mvnDebug jib:build` and attach to port 8000.

If developing with Eclipse and M2Eclipse (the Maven tooling for Eclipse), just launch the _Maven Build_ with _Debug_.

## Debugging the Jib Gradle Plugin (`jib-gradle-plugin`)

### Build and use a local snapshot 

To use a local build of the `jib-gradle-plugin`:

  1. Build and install `jib-gradle-plugin` into your local `~/.m2/repository`
     with `(cd jib-gradle-plugin && ./gradlew build install)`; this also builds `jib-core`.
     Alternatively, use the provided `build.sh` which performs an `install`.
  1. Modify your test project's `build.gradle` to look like the following:
        ```groovy
        buildscript {
            repositories {
                mavenLocal() // resolve in ~/.m2/repository
                mavenCentral()
            }
            dependencies {
                classpath 'com.google.cloud.tools:jib-gradle-plugin:1.3.1-SNAPSHOT'
            }
        }

        plugins {
            // id 'com.google.cloud.tools.jib' version '1.3.0'
        }

        // Applies the java plugin after Jib to make sure it works in this order.
        apply plugin: 'com.google.cloud.tools.jib' // must explicitly apply local
        apply plugin: 'java'
        ```

### Attaching a debugger

Attach a debugger to a Gradle instance by running Gradle as follows:

```shell
./gradlew jib \
  --no-daemon \
  -Dorg.gradle.jvmargs='-agentlib:jdwp:transport=dt_socket,server=y,address=5005,suspend=y'
```

