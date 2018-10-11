# Proposal: Jib Core as a library for Java

The tracking issue is at [#337](https://github.com/GoogleContainerTools/jib/issues/337).

# Goal

Design for Jib Core as a Java library for building container images.

# Proposed API

`Jib` - the main entrypoint for using Jib Core
- `JibContainerBuilder from(String baseImageReference)`
- `JibContainerBuilder from(ImageReference baseImageReference)`
- `JibContainerBuilder from(RegistryImage baseImage)`

`JibContainerBuilder` - configures the container to build
- `JibContainerBuilder addLayer(List<Path> files, Path pathInContainer)`
- `JibContainerBuilder addLayer(LayerConfiguration)`
- `JibContainerBuilder setLayers(List<LayerConfiguration>/LayerConfiguration...)`

- `JibContainerBuilder setEntrypoint(List<String>/String...)`
- `JibContainerBuilder setProgramArguments(List<String>/String...)`
- `JibContainerBuilder setEnvironment(Map<String, String> environmentMap)`
- `JibContainerBuilder addEnvironmentVariable(String name, String value)`
- `JibContainerBuilder setExposedPorts(List<Port>/Port...)`
- `JibContainerBuilder addExposedPort(Port port)`
- `JibContainerBuilder setLabels(Map<String, String> labelMap)`
- `JibContainerBuilder addLabel(String key, String value)`
- `JibContainerBuilder setFormat(ImageFormat)`
- `JibContainerBuilder setCreationTime(Instant creationTime)`
- `JibContainerBuilder setUser(String user)`
- `JibContainer containerize(Containerizer)`

Three `TargetImage` types (`RegistryImage`, `DockerDaemonImage`, and `TarImage`) define the 3 different targets Jib can build to.

`RegistryImage` - builds to a container registry
- `static RegistryImage named(ImageReference/String)`
- `RegistryImage addCredential(String username, String password)`
- `RegistryImage addCredentialRetriever(CredentialRetriever)`

`DockerDaemonImage` - builds to a Docker daemon
- `static DockerDaemonImage named(ImageReference/String)`
- `DockerDaemonImage setDockerExecutable(Path)`

`TarImage` - builds to a tarball archive
- `Builder`
  - `TarImage saveTo(Path outputFile)`
- `static Builder named(ImageReference/String)`

`Containerizer` - configures how and where to containerize to
- `static Containerizer to(RegistryImage)`
- `static Containerizer to(DockerDaemonImage)`
- `static Containerizer to(TarImage)`
- `Containerizer withAdditionalTag(String tag)`
- `Containerizer setExecutorService(ExecutorService)`
- `Containerizer setCacheConfiguration(CacheConfiguration)`
- `Containerizer setEventHandlers(EventHandlers)`
- `Containerizer setAllowInsecureRegistries(boolean)`
- `Containerizer setToolName(String)`

## For Java containers

`JavaContainerBuilder` - builds a `JibContainerBuilder` for Java apps
- `static JavaContainerBuilder builder()`
- `JavaContainerBuilder addDependencies(List<Path> dependencyFiles)`
- `JavaContainerBuilder addResources(List<Path> resourceFiles)`
- `JavaContainerBuilder addClasses(List<Path> classFiles)`
- `JavaContainerBuilder addToClasspath(List<Path> otherFiles)`
- `JavaContainerBuilder setJvmFlags(List<String>/String... jvmFlags)`
- `JavaContainerBuilder setMainClass(String mainClass)`
- `JavaContainerBuilder toContainerBuilder()`

# Simple example

```java
Jib.from("busybox")
   .addLayer(Arrays.asList(Paths.get("helloworld.sh")), "/helloworld.sh")
   .setEntrypoint("/helloworld.sh")
   .containerize(
       Jib.to(RegistryImage.named("coollog/jibtestimage")
                           .setCredential("coollog", "notmyrealpassword"));
```
