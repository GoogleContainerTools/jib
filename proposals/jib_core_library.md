# Proposal: Jib Core as a library for Java

The tracking issue is at [#337](https://github.com/GoogleContainerTools/jib/issues/337).

# Goal

Design for Jib Core as a Java library for building container images.

# Proposed API

## General Containers

`Jib` - the main entrypoint for using Jib Core

- `static JibContainerBuilder from(String baseImageReference)`
- `static JibContainerBuilder from(ImageReference baseImageReference)`
- `static JibContainerBuilder from(RegistryImage baseImage)`

`JibContainerBuilder` - configures the container to build

- `JibContainerBuilder addLayer(List<Path> files, AbsoluteUnixPath directoryInContainer)`

  Adds a new layer that will consists of the files referred to by `files`, each of which is copied into the `directoryInContainer`. Regardless of the directory nesting of files in `files`, they will be copied into the same level inside the container. If a directory is in the list of `files`, the directory and its contents will be recursively copied into the container

  ```java
      container.addLayer(
        Arrays.asList(
          Paths.get("/a/b/c.txt"),
          Paths.get("/a/b.txt"),
          Paths.get("/a/c.txt"),
          Paths.get("/d") // With /d/a.txt, /d/b.txt
          ),
        AbsoluteUnixPath.get("/root"));
      // container will have /root/d/a.txt, /root/d/b.txt, /root/b.txt, /root/c.txt (from /a/c.txt)
  ```

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
- `Containerizer setExecutorService(ExecutorService)`
- `Containerizer setCacheConfiguration(CacheConfiguration)`
- `Containerizer setEventHandlers(EventHandlers)`

## Simple example

```java
Jib.from("busybox")   
   .addLayer(Arrays.asList(Paths.get("helloworld.sh")), "/") 
   .setEntrypoint("/helloworld.sh")
   .containerize(
       Containerizer.to(RegistryImage.named("coollog/jibtestimage")
                           .setCredential("coollog", "notmyrealpassword")));
```

# For Java containers

`JavaContainerBuilder` - builds a `JibContainerBuilder` for Java apps. 

- `static JavaContainerBuilder builder()`
- `JavaContainerBuilder addDependencies(List<Path> dependencyFiles)`
- `JavaContainerBuilder addResources(List<Path> resourceFiles)`
- `JavaContainerBuilder addClasses(List<Path> classFiles)`
- `JavaContainerBuilder addToClasspath(List<Path> otherFiles)`
- `JavaContainerBuilder setJvmFlags(List<String>/String... jvmFlags)`
- `JavaContainerBuilder setMainClass(String mainClass)`
- `JibContainerBuilder toContainerBuilder()`

  Returns a `JibContainerBuilder` that, when built, will produce a minimal Java image that executes the java application specified by the builder methods previously called on this `JavaContainerBuilder`. 

  Example:

  ```java
  	RegistryImage destination = RegistryImage.named("gcr.io/myuser/my-java-container:latest");

    JibContainerBuilder javaImage = JavaContainerBuilder.builder()
      .addDependencies(Arrays.asList(Paths.get("/my/filesystem/lib/my-dependency.jar")))
      .addClasses(Arrays.asList(Paths.get("/my/filesystem/target/com/google/FooMain.class")))
      .setMainClass("com.google.FooMain")
      .toContainerBuilder()
      // Add other customization to the image, maybe some labels
      .addExposedPort(Port.tcp(8080));

    // Throws an exception if the image couldn't be build.
    JibContainer result = javaImage.containerize(Containerizer.to(destination));
  ```