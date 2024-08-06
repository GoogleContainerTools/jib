# Examples using Jib Core in Gradle builds

Jib Core is a containerization library for JVM languages, so it works well in Groovy as well. You can use Jib Core directly within a Gradle `build.gradle` to make tasks that build and manipulate container images.

For example, the following snippet is a simple example that creates a Gradle task that adds an environment variable to an existing image:

`build.gradle`:

```groovy
// Imports Jib Core as a library to use in this build script.
buildscript {
  repositories {
    mavenLocal()
    mavenCentral()
  }
  dependencies {
    classpath 'com.google.cloud.tools:jib-core:0.27.0'
  }
}

import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory

// Creates a task called 'dojib'.
task('dojib') {
  doLast { 
    def targetImage = '<target image reference>'
    Jib
        // Starts with the existing image.
        .from('<existing image reference')
        // Adds an environment variable.
        .addEnvironmentVariable('ENV_NAME', 'ENV_VALUE')
        // Performs the containerization.
        .containerize(
            Containerizer.to(
                // Tells Jib to containerize to targetImage's registry. 
                RegistryImage
                    .named(targetImage)
                    // Tells Jib to get registry credentials from a Docker config.
                    .addCredentialRetriever(
                        CredentialRetrieverFactory
                            .forImage(
                                    ImageReference.parse(targetImage),
                                    logEvent -> logger.log(LogLevel.valueOf(logEvent.getLevel().name()), logEvent.getMessage()))
                            .dockerConfig())))
    println 'done'
  }
}
```
